package com.example.dao.requests

import com.example.model.User

case class CreateRequest(user: User) extends DaoRequest
