package controllers

import models._
import org.mongodb.scala.bson.ObjectId
import play.api.Logging
import play.api.libs.json.{JsError, JsValue, Json}
import play.api.mvc.{Action, AnyContent, MessagesAbstractController, MessagesControllerComponents}

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import ModelHelper._

import java.util.Date

case class InvestItem(name:String, birthday:Date, cityId:String, uuid:Option[String])
class UserController @Inject()(lunarCalendar: LunarCalendar, cc: MessagesControllerComponents, userOp: UserOp,
                     predictionOp: PredictionOp)
                    (implicit ec: ExecutionContext) extends MessagesAbstractController(cc) with Logging {

  import User._
  val authorizedAction = AuthorizedAction(parse.defaultBodyParser)

  def newUser: Action[JsValue] = authorizedAction(parse.json).async {
    implicit request =>
      val newUserParam = request.body.validate[User]

      newUserParam.fold(
        error => Future {
          logger.error(JsError.toJson(error).toString())
          BadRequest(Json.obj("ok" -> false, "msg" -> JsError.toJson(error).toString()))
        },
        param => {
          for (_ <- userOp.newUser(param)) yield
            Ok(Json.obj("ok" -> true))
        })
  }

  def deleteUser(email: String): Action[AnyContent] = authorizedAction.async {
    implicit request =>
      for (_ <- userOp.deleteUser(email)) yield
        Ok(Json.obj("ok" -> true))
  }

  def updateUser(id: String): Action[JsValue] = authorizedAction(parse.json) {
    implicit request =>
      val userParam = request.body.validate[User]

      userParam.fold(
        error => {
          logger.error(JsError.toJson(error).toString())
          BadRequest(Json.obj("ok" -> false, "msg" -> JsError.toJson(error).toString()))
        },
        param => {
          userOp.updateUser(param)
          Ok(Json.obj("ok" -> true))
        })
  }

  def getUser(id: String): Action[AnyContent] = authorizedAction.async {
    implicit request =>
      for (user <- userOp.getUserByEmail(id)) yield
        Ok(Json.toJson(user))
  }

  def getAllUsers: Action[AnyContent] = authorizedAction.async {
    for (users <- userOp.getAllUsers()) yield
      Ok(Json.toJson(users))
  }

  def setUserItems(userId: String): Action[JsValue] = authorizedAction.async(parse.json) {
    implicit request =>
      implicit val reads = Json.reads[InvestItem]
      val ret = request.body.validate[Seq[InvestItem]]
      ret.fold(
        error => {
          logger.error(JsError.toJson(error).toString())
          Future {
            BadRequest(Json.obj("ok" -> false, "msg" -> JsError.toJson(error).toString()))
          }
        },
        items => {
          /*
          for (ret <- userOp.setUserItems(userId, items)) yield {
            val start = LocalDate.now()
            val end = start.plusMonths(1)
            //predictionOp.upsertPredictionByUser(userId, items, start, end)
            Ok(Json.obj("ok" -> true))
          }*/
          Future.successful(Ok(""))
        }
      )
  }
}
