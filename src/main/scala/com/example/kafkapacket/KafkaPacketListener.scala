package com.example.kafkapacket

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}

class KafkaPacketListener extends Actor with ActorLogging {

  import KafkaPacketListener._

  implicit private val system: ActorSystem = context.system

  override def receive: Receive = active(Map.empty)

  def active(subscribers: Map[String, ActorRef]): Receive = {
    case Subscribe(packetId) =>
      log.debug(s"Subscribe($packetId) from ${sender()}")
      context.become(active(subscribers + (packetId -> sender())))
    case Unsubscribe(packetId) =>
      log.debug(s"Unsubscribe($packetId) from ${sender()}")
      context.become(active(subscribers - packetId))
    case packet @ KafkaPacket(id, _, _) =>
      log.debug(s"Listener received packet: $packet")
      if (subscribers.contains(id)) {
        subscribers(id) ! Notification(packet)
      } else {
        log.debug(s"No subscriber for $packet")
      }
  }
}

object KafkaPacketListener {
  def props(): Props = Props[KafkaPacketListener]()
  case class Subscribe(packetId: String)
  case class Unsubscribe(packetId: String)
  case class Notification(packet: KafkaPacket)
}
