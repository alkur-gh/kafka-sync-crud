package com.example.dao.response

trait UpdateResponse extends DaoResponse

object UpdateResponse {
  case object Updated extends UpdateResponse
}
