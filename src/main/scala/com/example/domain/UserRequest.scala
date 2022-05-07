package com.example.domain

case class UserRequest(requestId: String, requestType: String, user: Option[User])
