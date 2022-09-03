package controllers

import org.bson.types.ObjectId
import play.api.mvc
import play.api.mvc.Security._
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global

class AuthenticatedRequest[A](val userinfo: String, request: Request[A]) extends WrappedRequest[A](request)

case class UserInfo(id: String, uuid: ObjectId, name: String, group: String, isAdmin: Boolean)

object AuthorizedAction {
  val idKey = "id"
  val uuidKey = "uuid"
  val nameKey = "name"
  val adminKey = "admin"
  val groupKey = "group"

  def onUnauthorized(request: RequestHeader): mvc.Results.Status =
    Results.Unauthorized

  def setUserinfo[A](request: Request[A], userInfo: UserInfo): Session = {
    request.session +
      (idKey -> userInfo.id) +
      (uuidKey -> userInfo.uuid.toHexString) + (adminKey -> userInfo.isAdmin.toString()) +
      (nameKey -> userInfo.name) + (groupKey -> userInfo.group)
  }

  def getUserInfo[A]()(implicit request: Request[A]): Option[UserInfo] =
    getUserinfo(request)

  def apply(parser: BodyParser[AnyContent]): AuthenticatedBuilder[UserInfo] = AuthenticatedBuilder(getUserinfo, parser)

  def getUserinfo(request: RequestHeader): Option[UserInfo] = {
    for {
      id <- request.session.get(idKey)
      uuid <- request.session.get(uuidKey)
      admin <- request.session.get(adminKey)
      name <- request.session.get(nameKey)
      group <- request.session.get(groupKey)
    } yield
      UserInfo(id = id, uuid = new ObjectId(uuid),
        name = name, group = group, isAdmin = admin.toBoolean)
  }
}
