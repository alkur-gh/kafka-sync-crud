package com.example.dao.response

object CommonResponses {
  case object UserNotFound extends DaoResponse

  case class UnknownError(message: String) extends DaoResponse
}
