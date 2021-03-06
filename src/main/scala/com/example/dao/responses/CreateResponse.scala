package com.example.dao.responses

import com.example.model.User

trait CreateResponse extends DaoResponse

object CreateResponse {
  case class Created(user: User) extends CreateResponse
  case object Conflict extends CreateResponse
}
