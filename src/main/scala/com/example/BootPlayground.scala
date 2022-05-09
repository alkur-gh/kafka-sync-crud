package com.example

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}

object BootPlayground extends App {
  implicit val system: ActorSystem = ActorSystem("my-system")
  val config: Config = ConfigFactory.load()



  println("Press `Enter` to shutdown...")
  scala.io.StdIn.readLine()
  system.terminate()
}
