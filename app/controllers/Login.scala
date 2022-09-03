package controllers

import models.{GroupOp, User, UserOp}
import play.api.Logging
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

case class Credential(user: String, password: String)
import javax.inject._
import models.Group
case class UserData(user:User, group:Group)

/**
 * @author user
 */
class Login @Inject()
(userOp: UserOp, groupOp:GroupOp, cc: MessagesControllerComponents)
(implicit ec: ExecutionContext) extends MessagesAbstractController(cc) with Logging {
  implicit val credentialReads = Json.reads[Credential]
  import User._
  val authorizedAction = AuthorizedAction(parse.defaultBodyParser)

  def authenticate: Action[JsValue] = Action(parse.json).async {
    implicit request =>
      val credentail = request.body.validate[Credential]
      credentail.fold(
        error =>
          Future.successful(BadRequest(Json.obj("ok" -> false, "msg" -> JsError.toJson(error))))
        ,
        crd => {
          val f = userOp.getUserByEmail(crd.user)
          for (user <- f) yield {
            if (user.password != crd.password) {
              Results.Unauthorized(Json.obj("ok" -> false, "msg" -> "密碼或帳戶錯誤"))
            } else {
              implicit val w2 = Json.writes[UserData]
              val group = Group.defaultGroup(0)
              val userInfo = UserInfo(user._id, user.uuid, user.name, "default", user.isAdmin)
              Ok(Json.obj("ok" -> true, "userData" -> UserData(user, group))).
                withSession(AuthorizedAction.setUserinfo(request, userInfo))
            }
          }
        })
  }

  def isLogin: Action[AnyContent] = authorizedAction(Ok(Json.obj("ok" -> true)))

  def logout: Action[AnyContent] = Action(Ok("logout").withNewSession)
}