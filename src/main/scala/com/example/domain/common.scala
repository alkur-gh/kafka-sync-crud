package com.example.domain

sealed trait PayloadType
object PayloadType {
  case object CreateRequest extends PayloadType
  case object CreatedResponse extends PayloadType
  case object CreateFailedResponse extends PayloadType

  case object GetAllRequest extends PayloadType
  case object GetAllSuccessResponse extends PayloadType

  case object GetOneRequest extends PayloadType
  case object GetOneSuccessResponse extends PayloadType
  case object GetOneFailedResponse extends PayloadType

  case object DeleteRequest extends PayloadType
  case object DeletedResponse extends PayloadType

  case object UpdateRequest extends PayloadType
  case object UpdatedResponse extends PayloadType
}

case class CreateRequest(user: User)

case class CreatedResponse(user: User)

case class CreateFailedResponse(message: String)

case object GetAllRequest

case class GetAllSuccessResponse(users: List[User])

case class GetOneRequest(userId: String)

case class GetOneSuccessResponse(user: Option[User])

case class GetOneFailedResponse(message: String)

case class DeleteRequest(userId: String)

case object DeletedResponse

case class UpdateRequest(userId: String, user: User)

case object UpdatedResponse

case class KafkaPacket(requestId: String, ptype: String, payload: String)
