package com.example.model

import org.json4s.jackson.Serialization
import org.json4s.{DefaultFormats, Extraction, JObject, Serialization}

import scala.util.Try

case class User(id: Option[String], name: String)

object User {
  implicit val serialization: Serialization = Serialization
  implicit val formats: DefaultFormats = DefaultFormats

  def from(m: Map[String, AnyRef]): Try[User] = Try {
    JObject(m.view.mapValues(Extraction.decompose(_)).toList).extract[User]
  }
}