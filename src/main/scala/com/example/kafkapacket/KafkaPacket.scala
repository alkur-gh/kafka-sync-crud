package com.example.kafkapacket

case class KafkaPacket(id: String, packetType: PacketTypes.PacketType, payload: String)

object PacketTypes extends Enumeration {
  type PacketType = Value
  val Request, Response = Value
}
