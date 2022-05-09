package com.example.kafkapacket

import com.example.util.Implicits._
import org.apache.kafka.clients.producer.{Producer, ProducerRecord}
import org.json4s.jackson.Serialization.write

class KafkaPacketProducer(kafkaProducer: Producer[String, String], kafkaProducerTopic: String) {

  def sendPacket(packet: KafkaPacket): Unit = {
    kafkaProducer.send(new ProducerRecord[String, String](kafkaProducerTopic, packet.id, write(packet)))
    kafkaProducer.flush()
  }
}
