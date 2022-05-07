import com.typesafe.config.{ Config, ConfigFactory }
import akka.Done
import akka.actor.ActorSystem
import akka.stream.alpakka.elasticsearch.{
  ElasticsearchConnectionSettings, ApiVersion, ElasticsearchParams,
  ElasticsearchWriteSettings, ElasticsearchSourceSettings, WriteMessage }
import akka.stream.alpakka.elasticsearch.scaladsl.{ ElasticsearchFlow, ElasticsearchSink, ElasticsearchSource }
import akka.stream.scaladsl.{ Source, Keep, Sink }
import akka.stream.{ ActorAttributes, Supervision }
import akka.kafka.{ ProducerSettings, ConsumerSettings, Subscriptions }
import akka.kafka.scaladsl.{ Producer, Consumer }
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerPartitionAssignor.Subscription
import org.apache.kafka.common.serialization.{ StringSerializer, StringDeserializer }

import scala.util.{ Try, Success, Failure }
import scala.concurrent.{ Promise, Future, Await }
import scala.concurrent.duration._

import com.example.domain._

object Boot extends App {
  val config: Config = ConfigFactory.load()
  implicit val system: ActorSystem = ActorSystem(config.getString("app.core.akka.actor.system.name"))
  import system.dispatcher
  val esConnectionSettings = ElasticsearchConnectionSettings(config.getString("app.core.elasticsearch.baseUrl"))
    // TODO: ask about safe way to access passwords
    .withCredentials(config.getString("app.core.elasticsearch.user"), config.getString("app.core.elasticsearch.password"))

  val kafkaConsumerSettings = ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
    .withBootstrapServers(config.getString("app.core.kafka.bootstrap.servers"))
    .withGroupId(config.getString("app.core.kafka.consumer.group.id"))
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  //val kafkaConsumerTopic = config.getString("app.core.kafka.topics.users.requests")
  val kafkaConsumerTopic = "users.requests"

  val kafkaProducerSettings = ProducerSettings(system, new StringSerializer, new StringSerializer)
    .withBootstrapServers(config.getString("app.core.kafka.bootstrap.servers"))
  val kafkaProducerTopic = config.getString("app.core.kafka.topics.users.responses")

  case class ParseException(requestId: String, errorMessage: String) extends Throwable {
    override def toString = s"Request ${requestId}: ${errorMessage}"
  }

  val control = Consumer
    .plainSource(kafkaConsumerSettings, Subscriptions.topics(kafkaConsumerTopic))
    .mapAsync(1)(message => Future {
      import org.json4s.jackson.Serialization.{ write, read }
      implicit val formats = org.json4s.DefaultFormats
      val producerSettings = ProducerSettings(system, new StringSerializer, new StringSerializer)
        .withBootstrapServers("localhost:9092")
      val kafkaProducer = producerSettings.createKafkaProducer()
      val kafkaTopic = "users.responses"
      println(s"${message.key}/${message.value}")

      PacketManager(
        consumerSettings,
        producerSettings,
        consumeTopic,
        produceTopic,
        (PayloadType -> ((RequestId, RequestPayload) => Future[(PayloadType, ResponsePayload)]))*
      )

      onPacket { packet =>
        Map(packet.ptype)(packet.requestId, packet.payload)
          .map((ptype, payload) => {
            produce(
          })
      }
    
      try {
        val packet = read[KafkaPacket](message.value)
        packet match {
          case KafkaPacket(requestId, ptype, payload) if ptype.equals(PayloadType.CreateRequest.toString) =>
            val request = read[CreateRequest](payload)
            val future[(PayloadType, Payload)] = ???

            Source.single(request.user)
              .map(user => WriteMessage.createCreateMessage(user.id, user))
              .via(ElasticsearchFlow.create[User](
                ElasticsearchParams.V7("users"),
                ElasticsearchWriteSettings(esConnectionSettings).withApiVersion(ApiVersion.V7),
                User.elasticsearchWriter))
              .map(response => {
                val (ptype, payload) = if (response.success) {
                  (PayloadType.CreatedResponse.toString, write(CreatedResponse(request.user)))
                } else {
                  (PayloadType.CreateFailedResponse.toString, write(CreateFailedResponse(response.errorReason.get)))
                }
                val javaFuture = kafkaProducer
                  .send(new ProducerRecord("users.responses", requestId,
                    write(KafkaPacket(requestId, ptype, payload))))
                javaFuture.get()
              })
              .run()
          case KafkaPacket(requestId, ptype, payload) if ptype.equals(PayloadType.GetOneRequest.toString) =>
            val request = read[GetOneRequest](payload)
            val future = ElasticsearchSource
              .create(
                ElasticsearchParams.V7("users"),
                query = s"""{"match": {"id": "${request.userId}"}}""",
                ElasticsearchSourceSettings(esConnectionSettings).withApiVersion(ApiVersion.V7))
              .map(result => read[User](result.source.toString))
              .fold(List[User]())((acc, user) => user :: acc)
              .map(users => {
                val javaFuture = kafkaProducer
                  .send(new ProducerRecord("users.responses", requestId,
                    write(KafkaPacket(requestId, PayloadType.GetOneSuccessResponse.toString,
                      write(GetOneSuccessResponse(if (users.isEmpty) None else Some(users.head)))))))
                javaFuture.get()
              })
              .run()
            //throw new RuntimeException("not implemented yet")
          case KafkaPacket(requestId, ptype, payload) if ptype.equals(PayloadType.DeleteRequest.toString) =>
            import akka.stream.alpakka.elasticsearch.MessageWriter
            val request = read[DeleteRequest](payload)
            Source.single(request.userId)
              .map(userId => WriteMessage.createDeleteMessage[User](userId))
              .via(ElasticsearchFlow.create(
                ElasticsearchParams.V7("users"),
                ElasticsearchWriteSettings(esConnectionSettings).withApiVersion(ApiVersion.V7),
                User.elasticsearchWriter))
              .map(_ => {
                val javaFuture = kafkaProducer
                  .send(new ProducerRecord("users.responses", requestId,
                    write(KafkaPacket(requestId, PayloadType.DeletedResponse.toString,
                      write(DeletedResponse)))))
                javaFuture.get()
              })
              .run()
          case KafkaPacket(requestId, ptype, payload) if ptype.equals(PayloadType.UpdateRequest.toString) =>
            val request = read[UpdateRequest](payload)
            Source.single((request.userId, request.user))
              .map { case (userId, user) => WriteMessage.createUpdateMessage[User](userId, user) }
              .via(ElasticsearchFlow.create(
                ElasticsearchParams.V7("users"),
                ElasticsearchWriteSettings(esConnectionSettings).withApiVersion(ApiVersion.V7),
                User.elasticsearchWriter))
              .map(_ => {
                val javaFuture = kafkaProducer
                  .send(new ProducerRecord("users.responses", requestId,
                    write(KafkaPacket(requestId, PayloadType.UpdatedResponse.toString,
                      write(UpdatedResponse)))))
                javaFuture.get()
              })
              .run()
          case KafkaPacket(requestId, ptype, _) if ptype.equals(PayloadType.GetAllRequest.toString) =>
            ElasticsearchSource
              .create(
                ElasticsearchParams.V7("users"),
                query = """{"match_all": {}}""",
                ElasticsearchSourceSettings(esConnectionSettings).withApiVersion(ApiVersion.V7),
              )
              .map(_.source.toString)
              .map(text => {
                // TODO: what to do if there's an error in parsing
                read[User](text)
              })
              .fold(List[User]())((acc, user) => user :: acc)
              .map(_.reverse)
              .map(users => {
                val javaFuture = kafkaProducer
                  .send(new ProducerRecord("users.responses", requestId,
                    write(KafkaPacket(requestId, PayloadType.GetAllSuccessResponse.toString, write(GetAllSuccessResponse(users))))))
                javaFuture.get()
              })
              .run()
            //throw new RuntimeException(s"PayloadType.GetAllRequest not implemented yet")
          case KafkaPacket(_, ptype, _) =>
            throw new RuntimeException(s"unknown payload type: $ptype")
        }
      } catch {
        case ex: Throwable =>
          ex.printStackTrace()
          val javaFuture = kafkaProducer
            .send(new ProducerRecord("users.responses", message.key,
              write(KafkaPacket(message.key, PayloadType.CreateFailedResponse.toString, write(CreateFailedResponse(ex.getMessage()))))))
          javaFuture.get()
      }
    })
    .toMat(Sink.ignore)(Consumer.DrainingControl.apply)
    .run()

  //val control = Consumer
  //  .plainSource(kafkaConsumerSettings, Subscriptions.topics(kafkaConsumerTopic))
  //  .map(message => {
  //    import org.json4s.jackson.JsonMethods._
  //    import org.json4s.DefaultFormats

  //    implicit val formats = DefaultFormats

  //    try {
  //      parse(message.value).extract[UserRequest]
  //    } catch {
  //      case ex: com.fasterxml.jackson.core.JsonParseException => {
  //        throw ParseException(s"key-${message.key}", ex.getMessage())
  //      }
  //    }
  //  })
  //  .withAttributes(ActorAttributes.supervisionStrategy {
  //    case ex: ParseException =>
  //      // TODO: handle the exception, probably send to the API
  //      println(ex.getMessage())
  //      Supervision.Resume
  //    case ex: Throwable =>
  //      println(ex.getMessage())
  //      Supervision.Stop
  //  })
  //  //.map(user => WriteMessage.createCreateMessage(user.id, user))
  //  //.via(ElasticsearchFlow.create[User](
  //  //  ElasticsearchParams.V7("users"),
  //  //  ElasticsearchWriteSettings(esConnectionSettings).withApiVersion(ApiVersion.V7),
  //  //  User.elasticsearchWriter))
  //  //.map(_.toString)
  //  //.map(new ProducerRecord[String, String](kafkaProducerTopic, _))
  //  //.toMat(Producer.plainSink(kafkaProducerSettings))(Consumer.DrainingControl.apply)
  //  .toMat(Sink.foreach(println))(Consumer.DrainingControl.apply)
  //  .run()

  println("Press 'Enter' to shutdown...")
  scala.io.StdIn.readLine()
  println("Shutting down")

  val future = control.drainAndShutdown()
  future.onComplete {
    case Failure(exception) => exception.printStackTrace(); system.terminate()
    case Success(value) => println(value); system.terminate()
  }

  //val future = Source.single(User("0", "username"))
  //  .map(user => WriteMessage.createCreateMessage(user.id, user))
  //  .via(ElasticsearchFlow.create[User](
  //    ElasticsearchParams.V7("users"),
  //    ElasticsearchWriteSettings(esConnectionSettings).withApiVersion(ApiVersion.V7),
  //    User.elasticsearchWriter))
  //  .map(_.toString)
  //  .map(new ProducerRecord[String, String](kafkaProducerTopic, _))
  //  .runWith(Producer.plainSink(kafkaProducerSettings))

  //Await.result(future, 10.seconds)
}
