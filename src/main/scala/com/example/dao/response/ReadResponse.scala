package com.example.dao.response

import com.example.model.User

trait ReadResponse extends DaoResponse

object ReadResponse {
  case class SingleUser(user: User) extends ReadResponse
  case class MultipleUser(users: Iterable[User]) extends ReadResponse
}
