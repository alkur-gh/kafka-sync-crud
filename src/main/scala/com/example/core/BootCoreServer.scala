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

  val kafkaProducerSettings = ProducerSettings(system, new StringSerializer, new StringSerializer)
    .withBootstrapServers(config.getString("kafka.bootstrap.servers"))
  val kafkaProducer = kafkaProducerSettings.createKafkaProducer()
  val kafkaProducerTopic = config.getString("kafka.core.produce.topic")

  val kafkaConsumerSettings: ConsumerSettings[String, String] = ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
    .withBootstrapServers(config.getString("kafka.bootstrap.servers"))
    .withGroupId("kafka.core.consume.group.id")
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
  val kafkaConsumerTopic = config.getString("kafka.core.consume.topic")

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
