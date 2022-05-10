package com.example.dao.responses

import com.example.util.Implicits._
import org.json4s.jackson.Serialization

import scala.util.Try

object ResponseSerialization {

//  override def options(contentType: String, content: String): List[Option[DaoResponse]] = List(
//      magic[CreateResponse.Created](contentType, content),
//      magic[CreateResponse.Conflict.type](contentType, content),
//      magic[ReadResponse.SingleUser](contentType, content),
//      magic[ReadResponse.MultipleUsers](contentType, content),
//      magic[UpdateResponse.Updated.type](contentType, content),
//      magic[DeleteResponse.Deleted.type](contentType, content),
//      magic[CommonResponses.UserNotFound.type](contentType, content),
//      magic[CommonResponses.UnknownError](contentType, content),
//    )
//
//  override def readContent[U: Manifest](content: String): U =
//    Serialization.read[U](content)

  case class Wrapper(responseType: String, body: String)

  def write(response: DaoResponse): String = {
    //noinspection DuplicatedCode
    val responseType: String = response.getClass.getSimpleName.stripSuffix("$")
    val body: String = Serialization.write(response)
    val wrapper = Wrapper(responseType, body)
    Serialization.write(wrapper)
  }

  def magic[T](contentType: String, content: String)(implicit m: Manifest[T]): Option[T] = {
    if (m.runtimeClass.getSimpleName.stripSuffix("$").equals(contentType)) {
      Some(Serialization.read[T](content))
    } else {
      None
    }
  }

  def options(contentType: String, content: String): List[Option[DaoResponse]] = {
    List(
      magic[CreateResponse.Created](contentType, content),
      magic[CreateResponse.Conflict.type](contentType, content),
      magic[ReadResponse.SingleUser](contentType, content),
      magic[ReadResponse.MultipleUsers](contentType, content),
      magic[UpdateResponse.Updated.type](contentType, content),
      magic[DeleteResponse.Deleted.type](contentType, content),
      magic[CommonResponses.UserNotFound.type](contentType, content),
      magic[CommonResponses.UnknownError](contentType, content),
    )
  }

  def read(s: String): Try[DaoResponse] = Try {
    val wrapper = Serialization.read[Wrapper](s)
    options(wrapper.responseType, wrapper.body)
      .collectFirst { case Some(v) => v }
      .getOrElse(CommonResponses.UnknownError(s"Unknown content type `${wrapper.responseType}` from ${wrapper}"))
//    responseType match {
//      case t if CreateResponse.Created.toString.equals(t) => Serialization.read[CreateResponse.Created](body)
//      case t if CreateResponse.Conflict.toString.equals(t) => Serialization.read[CreateResponse.Conflict.type](body)
//      case t if ReadResponse.SingleUser.toString.equals(t) => Serialization.read[ReadResponse.SingleUser](body)
//      case t if ReadResponse.MultipleUsers.toString.equals(t) => Serialization.read[ReadResponse.MultipleUsers](body)
//      case t if UpdateResponse.Updated.toString.equals(t) => Serialization.read[UpdateResponse.Updated.type](body)
//      case t if DeleteResponse.Deleted.toString.equals(t) => Serialization.read[DeleteResponse.Deleted.type](body)
//      case t if CommonResponses.UserNotFound.toString.equals(t) => Serialization.read[CommonResponses.UserNotFound.type](body)
//      case t if CommonResponses.UnknownError.toString.equals(t) => Serialization.read[CommonResponses.UnknownError](body)
//    }
  }
}
