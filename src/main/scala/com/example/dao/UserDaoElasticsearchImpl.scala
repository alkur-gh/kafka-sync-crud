package com.example.dao

import akka.http.scaladsl.model.StatusCodes
import com.example.dao.responses._
import com.example.model.User
import com.sksamuel.elastic4s.{ElasticClient, RequestFailure, RequestSuccess}
import org.json4s._
import org.json4s.jackson.Serialization
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class UserDaoElasticsearchImpl(client: ElasticClient)(implicit ec: ExecutionContext = ExecutionContext.Implicits.global)
  extends UserDao {

  import com.sksamuel.elastic4s.ElasticDsl
  import ElasticDsl._
  import com.sksamuel.elastic4s.json4s.ElasticJson4s.Implicits._

  implicit private val serialization: Serialization = Serialization
  implicit private val formats: DefaultFormats = DefaultFormats

  private val INDEX_NAME = "users"

  private val logger: Logger = LoggerFactory.getLogger(classOf[UserDaoElasticsearchImpl])

  private val defaultRecover: PartialFunction[Throwable, DaoResponse] = {
    case ex: Throwable =>
      val message = ex.toString
      logger.error(message)
      CommonResponses.UnknownError(message)
  }

  def init(): Unit = {
    val future = client.execute {
      createIndex(INDEX_NAME).mapping(
        properties(
          textField("name")
        )
      )
    }
    val response = Await.result(future, 10.seconds)
    response match {
      case _: RequestSuccess[_] => logger.info(response.toString)
      case _: RequestFailure =>
        logger.error(response.toString)
        throw new RuntimeException(s"Failed to initialize user dao: ${response.toString}")
    }
  }

  override def create(user: User): Future[DaoResponse] = {
    client
      .execute {
        indexInto(INDEX_NAME).id(user.name).doc(user).createOnly(true)
      }
      .map {
        case RequestSuccess(StatusCodes.Created.intValue, _, _, result) =>
          CreateResponse.Created(user.copy(id = Some(result.id)))
        case RequestFailure(StatusCodes.Conflict.intValue, _, _, _) =>
          CreateResponse.Conflict
      }
      .recover(defaultRecover)
  }

  override def readById(id: String): Future[DaoResponse] = {
    client
      .execute {
        get(INDEX_NAME, id)
      }
      .map {
        case RequestSuccess(StatusCodes.OK.intValue, _, _, result) =>
          val user = User.from(result.sourceAsMap).get
          ReadResponse.SingleUser(user.copy(id = Some(result.id)))
        case RequestSuccess(StatusCodes.NotFound.intValue, _, _, _) =>
          CommonResponses.UserNotFound
      }
      .recover(defaultRecover)
  }

  override def readByQuery(query: String): Future[DaoResponse] = {
    client
      .execute {
        if (query.isBlank) {
          search(INDEX_NAME).matchAllQuery()
        } else {
          search(INDEX_NAME).query(query)
        }
      }
      .map {
        case RequestSuccess(StatusCodes.OK.intValue, _, _, result) =>
          val users = result.hits.hits
            .map(h => User.from(h.sourceAsMap).get.copy(id = Some(h.id)))
          ReadResponse.MultipleUsers(users)
      }
      .recover(defaultRecover)
  }

  override def update(user: User): Future[DaoResponse] = {
    client
      .execute {
        updateById(INDEX_NAME, user.id.get).doc(user)
      }
      .map {
        case RequestSuccess(StatusCodes.OK.intValue, _, _, _) =>
          UpdateResponse.Updated
        case RequestFailure(StatusCodes.NotFound.intValue, _, _, _) =>
          CommonResponses.UserNotFound
      }
      .recover(defaultRecover)
  }

  override def deleteById(id: String): Future[DaoResponse] = {
    client
      .execute {
        ElasticDsl.deleteById(INDEX_NAME, id)
      }
      .map {
        case RequestSuccess(StatusCodes.OK.intValue, _, _, _) =>
          DeleteResponse.Deleted
        case RequestSuccess(StatusCodes.NotFound.intValue, _, _, _) =>
          CommonResponses.UserNotFound
      }
      .recover(defaultRecover)
  }
}
