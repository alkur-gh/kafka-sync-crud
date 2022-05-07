package com.example.api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.pathPrefix
import com.example.dao.{UserDao, UserDaoElasticsearchImpl}
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.akka.{AkkaHttpClient, AkkaHttpClientSettings}
import com.typesafe.config.{Config, ConfigFactory}

object BootApi extends App {
  val config: Config = ConfigFactory.load()
  implicit val system: ActorSystem = ActorSystem("api")
  import system.dispatcher

  val client = ElasticClient(AkkaHttpClient(AkkaHttpClientSettings(config.getConfig("elasticsearch"))))
  val dao: UserDao = new UserDaoElasticsearchImpl(client)

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

  system.terminate()
}


