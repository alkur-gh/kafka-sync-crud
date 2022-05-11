package com.example.api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.kafka.{ConsumerSettings, ProducerSettings}
import com.example.dao.UserDaoKafkaPacketImpl
import com.example.kafkapacket.{KafkaPacketClient, KafkaPacketConsumer, KafkaPacketListener, KafkaPacketProducer}
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}

object BootApiServer extends App {
  implicit val system: ActorSystem = ActorSystem("my-system")

  import system.dispatcher

  val config: Config = ConfigFactory.load()

  val kafkaProducerSettings = ProducerSettings(system, new StringSerializer, new StringSerializer)
    .withBootstrapServers(config.getString("kafka.bootstrap.servers"))
  val kafkaProducer = kafkaProducerSettings.createKafkaProducer()
  val kafkaProducerTopic = config.getString("kafka.api.produce.topic")

  val kafkaConsumerSettings = ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
    .withBootstrapServers(config.getString("kafka.bootstrap.servers"))
    .withGroupId(config.getString("kafka.api.consume.group.id"))
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
  val kafkaConsumerTopic = config.getString("kafka.api.consume.topic")

  val kafkaPacketProducer = new KafkaPacketProducer(kafkaProducer, kafkaProducerTopic)
  val listenerActor = system.actorOf(KafkaPacketListener.props(), "api-packet-listener")
  val clientActor = system.actorOf(KafkaPacketClient.props(kafkaPacketProducer, listenerActor), "api-packet-client")

  val control = KafkaPacketConsumer.consumeToHandler(kafkaConsumerSettings, kafkaConsumerTopic) { packet =>
    listenerActor ! packet
  }

  val dao = new UserDaoKafkaPacketImpl(clientActor)

  val route = pathPrefix("users")(UserDaoHttpApi.route(dao))
  val `interface` = config.getString("com.example.api.http.interface")
  val port = config.getInt("com.example.api.http.port")

  Http()
    .newServerAt(`interface`, port)
    .bind(route)
    .map(binding => {
      val address = binding.localAddress.getAddress.getHostAddress
      val port = binding.localAddress.getPort
      println(s"Listening on $address:$port...")
    })

  println("Press `Enter` to shutdown...")
  scala.io.StdIn.readLine()
  println("Shutting down...")
  kafkaProducer.close()
  control.drainAndShutdown()
    .map { _ =>
      system.terminate()
    }
}
