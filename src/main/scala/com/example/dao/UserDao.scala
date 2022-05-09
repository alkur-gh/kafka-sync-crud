package com.example.dao

import com.example.dao.responses.DaoResponse
import com.example.model.User

import scala.concurrent.Future

trait UserDao {
  def create(user: User): Future[DaoResponse]
  def readById(id: String): Future[DaoResponse]
  def readByQuery(query: String): Future[DaoResponse]
  def update(user: User): Future[DaoResponse]
  def deleteById(id: String): Future[DaoResponse]
}
