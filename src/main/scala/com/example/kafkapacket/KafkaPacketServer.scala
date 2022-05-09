package com.example.kafkapacket

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}

class KafkaPacketServer(kafkaPacketProducer: KafkaPacketProducer, handlerActorProps: Props)
  extends Actor with ActorLogging {

  import KafkaPacketServer._

  implicit private val system: ActorSystem = context.system

  override def receive: Receive = {
    case packet @ KafkaPacket(id, _, _) =>
      log.debug(s"Received packet: $packet")
      system.actorOf(handlerActorProps, s"kafka-packet-server-handler-$id") ! packet
    case ResponsePacket(packet) =>
      log.debug(s"Send response packet: $packet")
      kafkaPacketProducer.sendPacket(packet)
  }
}

object KafkaPacketServer {
  def props(kafkaPacketProducer: KafkaPacketProducer, handlerActorProps: Props): Props = {
    Props(classOf[KafkaPacketServer], kafkaPacketProducer, handlerActorProps)
  }
  case class ResponsePacket(packet: KafkaPacket)
}
