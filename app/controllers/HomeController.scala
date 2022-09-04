package controllers

import models._
import play.api.Logging
import play.api.libs.json.{JsError, Json}
import play.api.mvc._

import java.nio.file.Files
import java.time.format.TextStyle
import java.time.{LocalDateTime, ZoneId, ZoneOffset, ZonedDateTime}
import java.util.Locale
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

case class ZoneInfoEntry(id: String, text: String)

class HomeController @Inject()(cc: MessagesControllerComponents, groupOp: GroupOp,
                               excelUtility: ExcelUtility)
                              (implicit ec: ExecutionContext) extends MessagesAbstractController(cc) with Logging {

  val authorizedAction = AuthorizedAction(parse.defaultBodyParser)

  def index: Action[AnyContent] = Action {
    implicit request =>
      Redirect("/dist/")
  }

  import Group._
  def newGroup = authorizedAction(parse.json) {
    implicit request =>
      val ret = request.body.validate[Group]
      ret.fold(
        error => {
          logger.error(JsError.toJson(error).toString())
          BadRequest(Json.obj("ok" -> false, "msg" -> JsError.toJson(error).toString()))
        },
        param => {
          groupOp.newGroup(param)
          Ok(Json.obj("ok" -> true))
        })
  }

  def deleteGroup(id: String) = authorizedAction.async {
    implicit request =>
      for(ret <- groupOp.deleteGroupAsync(id)) yield
        Ok(Json.obj("ok" -> (ret.getDeletedCount != 0)))
  }

  def updateGroup(id: String) = authorizedAction(parse.json).async {
    implicit request =>
      val userParam = request.body.validate[Group]

      userParam.fold(
        error => {
          logger.error(JsError.toJson(error).toString())
          Future.successful(BadRequest(
            Json.obj("ok" -> false, "msg" -> JsError.toJson(error).toString())))
        },
        param => {
          for(ret <- groupOp.updateGroupAsync(param)) yield
            Ok(Json.obj("ok" -> (ret.getMatchedCount != 0)))
        })
  }

  def getAllGroups = authorizedAction.async {
    for(groups <-groupOp.getAllGroupsAsync()) yield
      Ok(Json.toJson(groups))
  }


}
