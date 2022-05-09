package com.example.dao.responses

trait UpdateResponse extends DaoResponse

object UpdateResponse {
  case object Updated extends UpdateResponse
}
