import akka.pattern.ask
import akka.util.Timeout
import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshal
import org.json4s._
import org.json4s.jackson.JsonMethods._
import akka.stream.scaladsl.{ Source, Keep, Sink }
import akka.stream.{ ActorAttributes, Supervision }
import akka.kafka.{ ProducerSettings, ConsumerSettings, Subscriptions }
import akka.kafka.scaladsl.{ Producer, Consumer }
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ StringSerializer, StringDeserializer }
import org.apache.kafka.clients.consumer.ConsumerConfig

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.util.{ Success, Failure }

import java.util.UUID

import com.example.domain._

object BootApi extends App {

  implicit val system: ActorSystem = ActorSystem("api-system")
  import system.dispatcher

  import de.heikoseeberger.akkahttpjson4s.Json4sSupport._

  val packetMaid = system.actorOf(Props[KafkaPacketMaid](), "kafka-packet-maid")

  val kafkaConsumerSettings = ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
    .withBootstrapServers("localhost:9092")
    .withGroupId("api-packet-maid")
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  val kafkaConsumerTopic = "users.responses"

  val control = Consumer
    .plainSource(kafkaConsumerSettings, Subscriptions.topics(kafkaConsumerTopic))
    .toMat(Sink.foreach({ message => 
      import org.json4s.jackson.Serialization.read
      implicit val formats = org.json4s.DefaultFormats
      val packet = read[KafkaPacket](message.value)
      packetMaid ! KafkaPacketMaid.PacketReceived(packet)
    }))(Consumer.DrainingControl.apply)
    .run()

  implicit val serialization = jackson.Serialization
  implicit val formats = DefaultFormats

  implicit val timeout: Timeout = 10.seconds

  val route: Route = pathPrefix("users") {
    concat(
      (pathEnd & get) {
        import GetAllActor._
        val requestId = UUID.randomUUID
        val actor = system.actorOf(props(requestId, packetMaid), s"api-request-$requestId")
        val future = actor ? Request
        onSuccess(future) {
          case response: GetAllSuccessResponse =>
            complete(StatusCodes.OK, response.users)
          case response: GetAllFailedResponse =>
            complete(StatusCodes.InternalServerError, response.message)
          case response =>
            println(response)
            complete(StatusCodes.InternalServerError)
        }
      },
      (pathEnd & post & entity(as[User])) { user =>
        import CreateActor._
        val requestId = UUID.randomUUID
        val actor = system.actorOf(props(requestId, packetMaid), s"api-request-${requestId}")
        val future = actor ? Request(user)
        onSuccess(future) {
          case response: CreatedResponse =>
            complete(StatusCodes.Created, response.user)
          case response: CreateFailedResponse =>
            complete(StatusCodes.BadRequest, response.message)
          case response =>
            println(response)
            complete(StatusCodes.InternalServerError)
        }
      },
      (path(Remaining) & get) { userId =>
        import GetOneActor._
        val requestId = UUID.randomUUID
        val actor = system.actorOf(props(requestId, packetMaid), s"api-request-${requestId}")
        val future = actor ? Request(userId)
        onSuccess(future) {
          case response: GetOneSuccessResponse =>
            response.user match {
              case Some(user) =>
                complete(StatusCodes.OK, user)
              case None =>
                complete(StatusCodes.NotFound)
            }
          case response =>
            println(response)
            complete(StatusCodes.InternalServerError)
        }
      },
      (path(Remaining) & put & entity(as[User])) { (userId, user) =>
        if (userId.equals(user.id)) {
          import UpdateActor._
          val requestId = UUID.randomUUID
          val actor = system.actorOf(props(requestId, packetMaid), s"api-request-${requestId}")
          val future = actor ? Request(userId, user)
          onSuccess(future) {
            case UpdatedResponse =>
              complete(StatusCodes.OK)
            case response =>
              println(response)
              complete(StatusCodes.InternalServerError)
          }
        } else {
          complete(StatusCodes.BadRequest, "changing `id` is not allowed")
        }
      },
      (path(Remaining) & delete) { userId =>
        import DeleteActor._
        val requestId = UUID.randomUUID
        val actor = system.actorOf(props(requestId, packetMaid), s"api-request-${requestId}")
        val future = actor ? Request(userId)
        onSuccess(future) {
          case DeletedResponse =>
            complete(StatusCodes.OK)
          case response =>
            println(response)
            complete(StatusCodes.InternalServerError)
        }
      },
    )
  }

  Http()
    .newServerAt("0.0.0.0", 8080)
    .bind(route)
    .map(binding => {
      println(s"Listening: ${binding}")
    })

  println("Press 'Enter' to shutdown...")
  scala.io.StdIn.readLine()
  val future = control.drainAndShutdown()
  future.onComplete {
    case Failure(exception) => exception.printStackTrace(); system.terminate()
    case Success(value) => println(value); system.terminate()
  }
}

class KafkaPacketMaid extends Actor {
  import KafkaPacketMaid._

  import scala.collection.mutable
  private val waiting: mutable.Map[String, ActorRef] = mutable.Map.empty

  override def receive = {
    case PacketReceived(packet) =>
      if (waiting.contains(packet.requestId)) {
        waiting(packet.requestId) ! packet
        waiting -= packet.requestId
      } else {
        println(s"no listener for: $packet")
      }
    case Subscribe(requestId) =>
      waiting.addOne(requestId, sender())
  }
}

object KafkaPacketMaid {
  case class PacketReceived(packet: KafkaPacket)
  case class Subscribe(requestId: String)
}

class CreateActor(requestId: UUID, maid: ActorRef) extends Actor {

  import CreateActor._

  import org.json4s.jackson.Serialization.{ write, read }
  implicit val formats = org.json4s.DefaultFormats

  implicit val system = context.system
  import system.dispatcher

  val producerSettings = ProducerSettings(context.system, new StringSerializer, new StringSerializer)
    .withBootstrapServers("localhost:9092")

  val kafkaProducer = producerSettings.createKafkaProducer()

  def receive = {
    case Request(user) =>
      val replyTo = sender()
      val javaFuture = kafkaProducer
        .send(new ProducerRecord("users.requests", requestId.toString,
              write(KafkaPacket(requestId.toString, PayloadType.CreateRequest.toString, write(CreateRequest(user))))))
      Future {
        val result = javaFuture.get()
        implicit val timeout: Timeout = 10.seconds
        val future = maid ? KafkaPacketMaid.Subscribe(requestId.toString)
        future.mapTo[KafkaPacket].map { packet => packet match {
          case KafkaPacket(_, ptype, payload) if ptype.equals(PayloadType.CreatedResponse.toString) =>
            val response = read[CreatedResponse](payload)
            replyTo ! CreatedResponse(response.user)
          case KafkaPacket(_, ptype, payload) if ptype.equals(PayloadType.CreateFailedResponse.toString) =>
            val response = read[CreateFailedResponse](payload)
            replyTo ! CreateFailedResponse(response.message)
          case KafkaPacket(_, ptype, _) =>
            replyTo ! CreateFailedResponse(s"unknown packet type: $ptype")
        }}
      }
  }
}

object CreateActor {

  def props(requestId: UUID, maid: ActorRef): Props =
    Props(classOf[CreateActor], requestId, maid)

  case class Request(user: User)

  case class CreatedResponse(user: User)

  case class CreateFailedResponse(message: String)
}

class GetAllActor(requestId: UUID, maid: ActorRef) extends Actor {

  import GetAllActor._

  import org.json4s.jackson.Serialization.{ write, read }
  implicit val formats = org.json4s.DefaultFormats

  implicit val system = context.system
  import system.dispatcher

  val producerSettings = ProducerSettings(context.system, new StringSerializer, new StringSerializer)
    .withBootstrapServers("localhost:9092")

  val kafkaProducer = producerSettings.createKafkaProducer()

  override def receive = {
    case Request =>
      val replyTo = sender()
      val javaFuture = kafkaProducer
        .send(new ProducerRecord("users.requests", requestId.toString,
              write(KafkaPacket(requestId.toString, PayloadType.GetAllRequest.toString, write(GetAllRequest)))))
      Future {
        val result = javaFuture.get()
        implicit val timeout: Timeout = 10.seconds
        val future = maid ? KafkaPacketMaid.Subscribe(requestId.toString)
        future.mapTo[KafkaPacket].map { packet => packet match {
          case KafkaPacket(_, ptype, payload) if ptype.equals(PayloadType.GetAllSuccessResponse.toString) =>
            val response = read[GetAllSuccessResponse](payload)
            replyTo ! GetAllSuccessResponse(response.users)
          case KafkaPacket(_, ptype, _) =>
            replyTo ! GetAllFailedResponse(s"unknown packet type: $ptype")
        }}
      }
  }
}

object GetAllActor {

  def props(requestId: UUID, maid: ActorRef): Props =
    Props(classOf[GetAllActor], requestId, maid)

  case object Request

  case class GetAllSuccessResponse(users: List[User])
  case class GetAllFailedResponse(message: String)
}

class GetOneActor(requestId: UUID, maid: ActorRef) extends Actor {
  import GetOneActor._

  import org.json4s.jackson.Serialization.{ write, read }
  implicit val formats = org.json4s.DefaultFormats

  implicit val system = context.system
  import system.dispatcher

  val producerSettings = ProducerSettings(context.system, new StringSerializer, new StringSerializer)
    .withBootstrapServers("localhost:9092")

  val kafkaProducer = producerSettings.createKafkaProducer()

  override def receive = {
    case Request(userId) =>
      val replyTo = sender()
      val javaFuture = kafkaProducer
        .send(new ProducerRecord("users.requests", requestId.toString,
              write(KafkaPacket(requestId.toString, PayloadType.GetOneRequest.toString, write(GetOneRequest(userId))))))
      Future {
        val result = javaFuture.get()
        implicit val timeout: Timeout = 10.seconds
        val future = maid ? KafkaPacketMaid.Subscribe(requestId.toString)
        future.mapTo[KafkaPacket].map { packet => packet match {
          case KafkaPacket(_, ptype, payload) if ptype.equals(PayloadType.GetOneSuccessResponse.toString) =>
            val response = read[GetOneSuccessResponse](payload)
            replyTo ! GetOneSuccessResponse(response.user)
          case KafkaPacket(_, ptype, _) =>
            replyTo ! GetOneFailedResponse(s"unknown packet type: $ptype")
        }}
      }
  }

}

object GetOneActor {

  def props(requestId: UUID, maid: ActorRef): Props =
    Props(classOf[GetOneActor], requestId, maid)

  case class Request(userId: String)

  case class GetOneSuccessResponse(user: Option[User])
  case class GetOneFailedResponse(message: String)
}

class DeleteActor(requestId: UUID, maid: ActorRef) extends Actor {

  import DeleteActor._

  import org.json4s.jackson.Serialization.{ write, read }
  implicit val formats = org.json4s.DefaultFormats

  implicit val system = context.system
  import system.dispatcher

  val producerSettings = ProducerSettings(context.system, new StringSerializer, new StringSerializer)
    .withBootstrapServers("localhost:9092")

  val kafkaProducer = producerSettings.createKafkaProducer()

  override def receive = {
    case Request(userId) =>
      val replyTo = sender()
      val javaFuture = kafkaProducer
        .send(new ProducerRecord("users.requests", requestId.toString,
              write(KafkaPacket(requestId.toString, PayloadType.DeleteRequest.toString, write(DeleteRequest(userId))))))
      Future {
        val result = javaFuture.get()
        implicit val timeout: Timeout = 10.seconds
        val future = maid ? KafkaPacketMaid.Subscribe(requestId.toString)
        future.mapTo[KafkaPacket].map { packet => packet match {
          case KafkaPacket(_, ptype, payload) if ptype.equals(PayloadType.DeletedResponse.toString) =>
            replyTo ! DeletedResponse
          case KafkaPacket(_, ptype, _) =>
            replyTo ! DeleteFailedResponse(s"unknown packet type: $ptype")
        }}
      }
  }
}

object DeleteActor {

  def props(requestId: UUID, maid: ActorRef): Props =
    Props(classOf[DeleteActor], requestId, maid)

  case class Request(userId: String)

  case object DeletedResponse

  case class DeleteFailedResponse(message: String)
}

class UpdateActor(requestId: UUID, maid: ActorRef) extends Actor {

  import UpdateActor._

  import org.json4s.jackson.Serialization.{ write, read }
  implicit val formats = org.json4s.DefaultFormats

  implicit val system = context.system
  import system.dispatcher

  val producerSettings = ProducerSettings(context.system, new StringSerializer, new StringSerializer)
    .withBootstrapServers("localhost:9092")

  val kafkaProducer = producerSettings.createKafkaProducer()

  override def receive = {
    case Request(userId, user) =>
      val replyTo = sender()
      val javaFuture = kafkaProducer
        .send(new ProducerRecord("users.requests", requestId.toString,
              write(KafkaPacket(requestId.toString, PayloadType.UpdateRequest.toString, write(UpdateRequest(userId, user))))))
      Future {
        val result = javaFuture.get()
        implicit val timeout: Timeout = 10.seconds
        val future = maid ? KafkaPacketMaid.Subscribe(requestId.toString)
        future.mapTo[KafkaPacket].map { packet => packet match {
          case KafkaPacket(_, ptype, payload) if ptype.equals(PayloadType.UpdatedResponse.toString) =>
            replyTo ! UpdatedResponse 
          case KafkaPacket(_, ptype, _) =>
            replyTo ! UpdateFailedResponse(s"unknown packet type: $ptype")
        }}
      }
  }
}

object UpdateActor {

  def props(requestId: UUID, maid: ActorRef): Props =
    Props(classOf[UpdateActor], requestId, maid)

  case class Request(userId: String, user: User)

  case object UpdatedResponse

  case class UpdateFailedResponse(message: String)
}
