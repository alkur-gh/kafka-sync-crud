package com.example.dao.requests

import com.example.model.User

case class UpdateRequest(user: User) extends DaoRequest
