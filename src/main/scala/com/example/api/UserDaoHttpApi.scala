package com.example.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.example.dao.UserDao
import com.example.dao.responses._
import com.example.model.User
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
import org.json4s._

object UserDaoHttpApi {
  implicit private val serialization: Serialization = jackson.Serialization
  implicit private val formats: DefaultFormats = DefaultFormats

  def route(dao: UserDao): Route = {
    concat(
      pathEnd {
        concat(
          (get & parameter("q".withDefault(""))) { q =>
            onSuccess(dao.readByQuery(q)) {
              case ReadResponse.MultipleUsers(users) =>
                complete(StatusCodes.OK, users)
            }
          },
          (post & entity(as[User])) { user =>
            onSuccess(dao.create(user)) {
              case CreateResponse.Created(user) =>
                complete(StatusCodes.Created, user)
              case CreateResponse.Conflict =>
                complete(StatusCodes.Conflict)
            }
          }
        )
      },
      path(Segment) { userId =>
        concat(
          get {
            onSuccess(dao.readById(userId)) {
              case ReadResponse.SingleUser(user) =>
                complete(StatusCodes.OK, user)
              case CommonResponses.UserNotFound =>
                complete(StatusCodes.NotFound)
            }
          },
          (put & entity(as[User])) { user =>
            onSuccess(dao.update(user.copy(id = Some(userId)))) {
              case UpdateResponse.Updated =>
                complete(StatusCodes.OK)
              case CommonResponses.UserNotFound =>
                complete(StatusCodes.NotFound)
            }
          },
          delete {
            onSuccess(dao.deleteById(userId)) {
              case DeleteResponse.Deleted =>
                complete(StatusCodes.OK)
              case CommonResponses.UserNotFound =>
                complete(StatusCodes.NotFound)
            }
          }
        )
      }
    )
  }
}
