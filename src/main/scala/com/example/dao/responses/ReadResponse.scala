package com.example.dao.responses

import com.example.model.User

trait ReadResponse extends DaoResponse

object ReadResponse {
  case class SingleUser(user: User) extends ReadResponse
  case class MultipleUsers(users: Iterable[User]) extends ReadResponse
}
