package controllers

import models._
import org.mongodb.scala.bson.ObjectId
import play.api._
import play.api.libs.json.JsError.toJson
import play.api.libs.json._
import play.api.mvc._

import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import models.ModelHelper._

class Query @Inject()(cc: MessagesControllerComponents, bookOp: BookOp)
  extends MessagesAbstractController(cc) with Logging {

  val authorizedAction: Security.AuthenticatedBuilder[UserInfo] = AuthorizedAction(parse.defaultBodyParser)

  def getGroupBooks(groupID: String) = authorizedAction.async {
    val f = bookOp.getAllGroupBooksAsync(groupID)
    f.failed.foreach(ex => logger.error("getGroupBooks failed", ex))
    for (ret <- f) yield
      Ok(Json.toJson(ret))
  }

  def getBorrowBooks(userID: String) = authorizedAction.async {
    val f = bookOp.getBookLentByUser(userID)
    f.failed.foreach(ex => logger.error("getBorrowBooks failed", ex))
    for (ret <- f) yield
      Ok(Json.toJson(ret))
  }

  def borrowBook() = authorizedAction(parse.json).async {
    implicit request =>
      implicit val read = Json.reads[BorrowBookParam]
      val ret = request.body.validate[BorrowBookParam]
      ret.fold(err => {
        logger.error(Json.toJson(toJson(JsError(err))).toString())
        Future.successful(BadRequest)
      },
        param => {
          val f = bookOp.borrowBookAsync(param.bookId, param.userId)
          for (ret <- f) yield
            Ok(Json.toJson(ret))
        }
      )
  }


  def returnBook() = authorizedAction(parse.json).async {
    implicit request =>
      implicit val read = Json.reads[ReturnBookParam]
      val ret = request.body.validate[ReturnBookParam]
      ret.fold(err => {
        logger.error(Json.toJson(toJson(JsError(err))).toString())
        Future.successful(BadRequest)
      },
        param => {
          val f = bookOp.returnBookAsync(param.bookId)
          for (ret <- f) yield
            Ok(Json.toJson(ret))
        }
      )
  }

  case class BorrowBookParam(bookId: ObjectId, userId: String)

  case class ReturnBookParam(bookId: ObjectId)
}
