package com.example.core

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.example.dao.UserDao
import com.example.dao.requests._
import com.example.dao.responses.{CommonResponses, ResponseSerialization}
import com.example.kafkapacket.{KafkaPacket, KafkaPacketServer, PacketTypes}

import scala.concurrent.Future
import scala.util.{Failure, Success}

class UserDaoPacketHandlerActor(userDao: UserDao) extends Actor with ActorLogging {

  private val system: ActorSystem = context.system
  import system.dispatcher

  override def receive: Receive = {
    case packet @ KafkaPacket(_, _, _) =>
      val replyTo = sender()
      val requestTry = RequestSerialization.read(packet.payload)
      val responseFuture = requestTry match {
        case Failure(exception) =>
          exception.printStackTrace()
          Future.failed(new IllegalArgumentException(s"Failed to parse request: ${exception.getMessage}"))
        case Success(request) => request match {
          case CreateRequest(user) => userDao.create(user)
          case ReadByIdRequest(id) => userDao.readById(id)
          case ReadByQueryRequest(query) => userDao.readByQuery(query)
          case UpdateRequest(user) => userDao.update(user)
          case DeleteByIdRequest(id) => userDao.deleteById(id)
        }
      }
      responseFuture.onComplete { result =>
        val response = result match {
          case Failure(exception) => CommonResponses.UnknownError(exception.getMessage)
          case Success(value) => value
        }
        val payload = ResponseSerialization.write(response)
        val responsePacket = packet.copy(packetType = PacketTypes.Response, payload = payload)
        replyTo ! KafkaPacketServer.ResponsePacket(responsePacket)
      }
  }
}

object UserDaoPacketHandlerActor {
  def props(userDao: UserDao): Props = Props(classOf[UserDaoPacketHandlerActor], userDao)
}
