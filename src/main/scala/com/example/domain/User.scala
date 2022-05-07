package com.example.domain

import akka.stream.alpakka.elasticsearch.MessageWriter

case class User(id: String, name: String)

object User {
  val elasticsearchWriter: MessageWriter[User] = new MessageWriter[User] {
    import org.json4s._
    import org.json4s.jackson.JsonMethods._

    override def convert(user: User): String = {
      compact(JObject(List("id" -> JString(user.id), "name" -> JString(user.name))))
    }
  }
}
