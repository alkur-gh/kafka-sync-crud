package com.example.dao.responses

object CommonResponses {
  case object UserNotFound extends DaoResponse

  case class UnknownError(message: String) extends DaoResponse
}
