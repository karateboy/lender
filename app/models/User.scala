package models

import models.ModelHelper._
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.codecs.Macros._
import org.mongodb.scala.model.{Filters, Updates}
import org.mongodb.scala.result.{DeleteResult, InsertOneResult, UpdateResult}
import play.api._
import play.api.libs.json._

import java.time.{LocalDateTime, ZoneId, ZoneOffset}
import java.util.Date
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.{Failure, Success}

case class User(_id: String, uuid:ObjectId, password: String, name: String,
                birthday:Date, cityId:String, isAdmin: Boolean, items: Seq[ObjectId])

object User{
  implicit val read = Json.reads[User]
  implicit val write = Json.writes[User]
}

import javax.inject._

@Singleton
class UserOp @Inject()(mongodb: MongoDB, cityInfoOp: CityInfoOp, gamerOp: GamerOp, lunarCalendar: LunarCalendar) extends Logging {

  import org.mongodb.scala._
  implicit val log = logger
  val ColName = "users"
  val codecRegistry = fromRegistries(fromProviders(classOf[User]), DEFAULT_CODEC_REGISTRY)
  val collection: MongoCollection[User] = mongodb.database.withCodecRegistry(codecRegistry).getCollection(ColName)

  def init(): Unit = {
    for (colNames <- mongodb.database.listCollectionNames().toFuture()) {
      if (!colNames.contains(ColName)) {
        val f = mongodb.database.createCollection(ColName).toFuture()
        f onComplete completeHandler
      }
    }

    val f = collection.countDocuments().toFuture()
    f onComplete {
      case Success(count)=>
        if (count == 0) {
          val birthday = LocalDateTime.of(1975, 10, 25, 4, 0, 0)
          val uuid = new ObjectId()
          val cityInfo = cityInfoOp.map("台北")
          val defaultUser = User(_id="karateboy@stronghold.tw",
            uuid = uuid,
            password = "45142399bank!",
            name = "Aragorn",
            birthday = Date.from(birthday.atZone(ZoneId.of(cityInfo.zoneId)).toInstant),
            cityId= cityInfo.name,
            isAdmin = true,
            items = Seq.empty[ObjectId])
          logger.info(s"Create default user: $defaultUser")
          newUser(defaultUser)
        }
      case Failure(exception)=>
        logger.error("failed", exception)
    }
  }

  init()

  def newUser(user: User): Future[InsertOneResult] = {
    val f = collection.insertOne(user).toFuture()
    val cityInfo = cityInfoOp.map(user.cityId)
    val zoneDateTime = user.birthday.toInstant.atZone(ZoneId.of(cityInfo.zoneId))
    val lunarDateTime = lunarCalendar.getLunarDateTime(zoneDateTime,
      ZoneId.of(ZoneOffset.ofHours(cityInfo.zoneOffset).getId), cityInfo.minuteOffset)
    val gamer = Gamer(_id=user.uuid, user.name, birthday = user.birthday, cityId = user.cityId,
      lunarDateTime = lunarDateTime)
    gamerOp.upsert(gamer)
    f onComplete completeHandler
    f
  }

  import org.mongodb.scala.model.Filters._

  def deleteUser(email: String): Future[DeleteResult] = {
    val f = collection.deleteOne(equal("_id", email)).toFuture()
    f onComplete(completeHandler)
    f
  }

  def updateUser(user: User): Future[User] = {
      val update1 =
          Updates.combine(
            Updates.set("name", user.name),
            Updates.set("isAdmin", user.isAdmin),
            Updates.set("items", user.items)
          )
      val update = if (user.password.nonEmpty)
        Updates.combine(update1, Updates.set("password", user.password))
      else
        update1

      val f = collection.findOneAndUpdate(equal("_id", user._id), update).toFuture()
      f onComplete completeHandler
      f
  }

  def getUserByEmail(email: String): Future[User] = {
    val f = collection.find(equal("_id", email)).first().toFuture()
    f onComplete completeHandler
    f
  }

  def getUser(email: String): Future[User] = {
    val f = collection.find(equal("_id", email)).first().toFuture()
    f onComplete completeHandler
    f
  }

  def getAllUsers(): Future[Seq[User]] = {
    val f = collection.find().toFuture()
    f onComplete completeHandler
    f
  }

  def getAdminUsers(): Future[Seq[User]] = {
    val f = collection.find(equal("isAdmin", true)).toFuture()
    f onComplete completeHandler
    f
  }

  def setUserItems(_id:String, items:Seq[ObjectId]): Future[UpdateResult] = {
    val filter = Filters.equal("_id", _id)
    val f = collection.updateOne(filter, Updates.set("items", items)).toFuture()
    f onComplete completeHandler
    f
  }
}
