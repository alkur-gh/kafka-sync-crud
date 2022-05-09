package com.example.dao.requests

import com.example.util.Implicits._
import org.json4s.jackson.Serialization

import scala.util.Try

object RequestSerialization {

  case class Wrapper(requestType: String, body: String)

  def write(request: DaoRequest): String = {
    //noinspection DuplicatedCode
    val requestType: String = request.getClass.getSimpleName
    val body: String = Serialization.write(request)
    val wrapper = Wrapper(requestType, body)
    Serialization.write(wrapper)
  }

  def read(s: String): Try[DaoRequest] = Try {
    val wrapper = Serialization.read[Wrapper](s)
    val requestType = wrapper.requestType
    val body = wrapper.body
    requestType match {
      case t if CreateRequest.toString.equals(t) => Serialization.read[CreateRequest](body)
      case t if ReadByIdRequest.toString.equals(t) => Serialization.read[ReadByIdRequest](body)
      case t if ReadByQueryRequest.toString.equals(t) => Serialization.read[ReadByQueryRequest](body)
      case t if UpdateRequest.toString.equals(t) => Serialization.read[UpdateRequest](body)
      case t if DeleteByIdRequest.toString.equals(t) => Serialization.read[DeleteByIdRequest](body)
    }
  }
}
