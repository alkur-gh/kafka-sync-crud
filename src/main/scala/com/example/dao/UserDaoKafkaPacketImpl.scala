package com.example.dao

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import com.example.dao.requests._
import com.example.dao.responses.{CommonResponses, DaoResponse, ResponseSerialization}
import com.example.kafkapacket.{KafkaPacket, KafkaPacketClient, PacketTypes}
import com.example.model.User

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class UserDaoKafkaPacketImpl(clientActor: ActorRef)(implicit val system: ActorSystem) extends UserDao {
  import system.dispatcher

  implicit private val timeout: Timeout = 10.seconds

  private def randomId: String = UUID.randomUUID().toString

  private def sendRequest(request: DaoRequest): Future[DaoResponse] = {
    val packet = KafkaPacket(randomId, PacketTypes.Request, RequestSerialization.write(request))
    (clientActor ? KafkaPacketClient.Send(packet))
      .mapTo[KafkaPacketClient.Response]
      .map { case KafkaPacketClient.Response(packet) =>
        println(packet)
        ResponseSerialization.read(packet.payload) match {
          case Failure(exception) => CommonResponses.UnknownError(s"Response deserialization failed: ${exception.getMessage}")
          case Success(value) => value
        }
      }
  }

  override def create(user: User): Future[DaoResponse] = {
    sendRequest(CreateRequest(user))
  }

  override def readById(id: String): Future[DaoResponse] = {
    sendRequest(ReadByIdRequest(id))
  }

  override def readByQuery(query: String): Future[DaoResponse] = {
    sendRequest(ReadByQueryRequest(query))
  }

  override def update(user: User): Future[DaoResponse] = {
    sendRequest(UpdateRequest(user))
  }

  override def deleteById(id: String): Future[DaoResponse] = {
    sendRequest(DeleteByIdRequest(id))
  }
}
