package com.example.kafkapacket

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.stream.scaladsl.Sink
import com.example.util.Implicits._
import org.json4s.jackson.Serialization.read
import org.slf4j.{Logger, LoggerFactory}

object KafkaPacketConsumer {

  val log: Logger = LoggerFactory.getLogger(classOf[KafkaPacketConsumer.type])

  def consumeToHandler(settings: ConsumerSettings[String, String], topic: String)
                      (handler: KafkaPacket => Unit)
                      (implicit system: ActorSystem): Consumer.DrainingControl[Done] = {
    import system.dispatcher
    val control: Consumer.DrainingControl[Done] = Consumer
      .atMostOnceSource(settings, Subscriptions.topics(topic))
      .map { message =>
        try {
          val packet = read[KafkaPacket](message.value())
          handler(packet)
        } catch {
          case e: Throwable =>
            e.printStackTrace()
            throw e
        }
      }
      .toMat(Sink.ignore)(Consumer.DrainingControl.apply)
      .run()
    control.streamCompletion.onComplete { result =>
      log.info(s"Consumer stream from topic $topic complete: $result")
    }
    control
  }
}
