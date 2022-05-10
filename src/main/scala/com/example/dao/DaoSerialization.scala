package com.example.dao

import com.example.util.Implicits._
import org.json4s.jackson.Serialization

import scala.util.Try

/**
 * Not used, tried some magic but failed.
 */
abstract class DaoSerialization[T <: AnyRef] {
  case class Wrapper(contentType: String, content: String)

  def options(contentType: String, content: String): List[Option[T]]
  def readContent[U: Manifest](content: String): U
//  def optionBackup(contentType: String, content: String): T

  protected def magic[U <: T](contentType: String, content: String)(implicit m: Manifest[U]): Option[T] = {
    if (m.runtimeClass.getSimpleName.stripSuffix("$").equals(contentType)) {
      Some(readContent[U](content))
//      Some(Serialization.read[U](content))
    } else {
      None
    }
  }

  def write(response: T): String = {
    //noinspection DuplicatedCode
    val contentType: String = response.getClass.getSimpleName.stripSuffix("$")
    val content: String = Serialization.write[T](response)
    val wrapper = Wrapper(contentType, content)
    Serialization.write(wrapper)
  }

  def read(s: String): Try[T] = Try {
    val wrapper = Serialization.read[Wrapper](s)
    options(wrapper.contentType, wrapper.content)
      .collectFirst { case Some(v) => v }
      .head
//      .getOrElse(optionBackup(wrapper.contentType, wrapper.content))
  }
}
