package com.example.dao.responses

trait DeleteResponse extends DaoResponse

object DeleteResponse {
  case object Deleted extends DeleteResponse
}
