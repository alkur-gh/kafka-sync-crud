package com.example.dao.response

trait DeleteResponse extends DaoResponse

object DeleteResponse {
  case object Deleted extends DeleteResponse
}
