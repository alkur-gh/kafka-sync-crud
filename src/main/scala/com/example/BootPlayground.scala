package com.example

import akka.actor.ActorSystem
import com.example.dao.{UserDao, UserDaoElasticsearchImpl}
import com.example.dao.response.{CommonResponses, CreateResponse, DeleteResponse, ReadResponse, UpdateResponse}
import com.example.model.User
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.akka.{AkkaHttpClient, AkkaHttpClientSettings}
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.Future

object BootPlayground extends App {
  implicit val system: ActorSystem = ActorSystem("my-system")
  import system.dispatcher
  val config: Config = ConfigFactory.load()
  val client = ElasticClient(AkkaHttpClient(AkkaHttpClientSettings(config.getConfig("elasticsearch"))))

  val dao: UserDao = new UserDaoElasticsearchImpl(client)
//  dao.init()

  dao.update(User(Some("sdhsdhsgsg"), "boringname")).map(println)
}
