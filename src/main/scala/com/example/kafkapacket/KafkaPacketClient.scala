package com.example.kafkapacket

import akka.actor.{Actor, ActorLogging, ActorRef, Props}


class KafkaPacketClient(val kafkaPacketProducer: KafkaPacketProducer, packetListener: ActorRef)
  extends Actor with ActorLogging {

  import KafkaPacketClient._

  override def receive: Receive = active(Map.empty)

  def active(waitingForResponse: Map[String, ActorRef]): Receive = {
    case Send(packet) =>
      packetListener ! KafkaPacketListener.Subscribe(packet.id)
      kafkaPacketProducer.sendPacket(packet)
      log.debug(s"Send packet: $packet")
      context.become(active(waitingForResponse + (packet.id -> sender())))
    case KafkaPacketListener.Notification(packet) =>
      log.debug(s"Received packet: $packet")
      if (waitingForResponse.contains(packet.id)) {
        waitingForResponse(packet.id) ! Response(packet)
        context.become(active(waitingForResponse - packet.id))
      } else {
        log.warning(s"Got foster packet from listener: $packet")
      }
      packetListener ! KafkaPacketListener.Unsubscribe(packet.id)
  }
}

object KafkaPacketClient {

  def props(kafkaPacketProducer: KafkaPacketProducer, listenerActor: ActorRef): Props = {
    Props(classOf[KafkaPacketClient], kafkaPacketProducer, listenerActor)
  }

  case class Send(packet: KafkaPacket)

  case class Response(packet: KafkaPacket)
}
