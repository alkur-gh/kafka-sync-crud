package com.example.core

import akka.actor.ActorSystem
import akka.kafka.{ConsumerSettings, ProducerSettings}
import com.example.dao.UserDaoElasticsearchImpl
import com.example.kafkapacket.{KafkaPacketConsumer, KafkaPacketProducer, KafkaPacketServer}
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.akka.{AkkaHttpClient, AkkaHttpClientSettings}
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}

object BootCoreServer extends App {
  implicit val system: ActorSystem = ActorSystem("my-system")

  import system.dispatcher

  val config: Config = ConfigFactory.load()

  private val kafkaProducerSettings = ProducerSettings(system, new StringSerializer, new StringSerializer)
    .withBootstrapServers("localhost:9092")
  private val kafkaProducer = kafkaProducerSettings.createKafkaProducer()
  private val kafkaProducerTopic = "users.responses"

  private val kafkaConsumerSettings: ConsumerSettings[String, String] = ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
    .withBootstrapServers("localhost:9092")
    .withGroupId("kafka-server-consumer")
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")

  private val kafkaConsumerTopic = "users.requests"

  val client = ElasticClient(AkkaHttpClient(AkkaHttpClientSettings(config.getConfig("elasticsearch"))))
  val userDao = new UserDaoElasticsearchImpl(client)
  val handlerActorProps = UserDaoPacketHandlerActor.props(userDao)
  val kafkaPacketProducer = new KafkaPacketProducer(kafkaProducer, kafkaProducerTopic)
  val serverActor = system.actorOf(KafkaPacketServer.props(kafkaPacketProducer, handlerActorProps))

  val control = KafkaPacketConsumer.consumeToHandler(kafkaConsumerSettings, kafkaConsumerTopic) { packet =>
    serverActor ! packet
  }

  println("Press `Enter` to shutdown...")
  scala.io.StdIn.readLine()
  println("Shutting down...")
  kafkaProducer.close()
  control.drainAndShutdown()
  system.terminate()
}
