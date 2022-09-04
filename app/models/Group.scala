package models

import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.codecs.Macros._
import org.mongodb.scala.result.{DeleteResult, InsertOneResult, UpdateResult}
import play.api.Logging
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.{Failure, Success}


case class Ability(action:String, subject:String)
case class Group(_id: String, name: String, admin:Boolean, abilities: Seq[Ability])

import javax.inject._
object Group {
  val PLATFORM_ADMIN = "platformAdmin"
  val GROUP1 = "group1"

  val ACTION_READ = "read"
  val ACTION_MANAGE = "manage"
  val ACTION_SET = "set"

  val SUBJECT_ALL = "all"
  val SUBJECT_DASHBOARD = "Dashboard"
  val SUBJECT_DATA = "Data"
  val SUBJECT_ALARM = "Alarm"

  val defaultGroup : Seq[Group] =
    Seq(
      Group(_id = PLATFORM_ADMIN, "平台管理團隊",
        true, Seq(Ability(ACTION_MANAGE, SUBJECT_ALL))),
      Group(_id = GROUP1, "法人1", false, Seq(Ability(ACTION_READ, SUBJECT_DASHBOARD),
        Ability(ACTION_READ, SUBJECT_DATA),
        Ability(ACTION_SET, SUBJECT_ALARM))))

  implicit val r1 = Json.reads[Ability]
  implicit val reads = Json.reads[Group]
  implicit val w1 = Json.writes[Ability]
  implicit val write = Json.writes[Group]
}

@Singleton
class GroupOp @Inject()(mongoDB: MongoDB) extends Logging {
  import Group._
  import org.mongodb.scala._

  val ColName = "groups"
  val codecRegistry = fromRegistries(fromProviders(classOf[Group], classOf[Ability]), DEFAULT_CODEC_REGISTRY)
  val collection: MongoCollection[Group] = mongoDB.database.withCodecRegistry(codecRegistry).getCollection(ColName)

  private def init(): Unit = {
    for(colNames <- mongoDB.database.listCollectionNames().toFuture()){
      if (!colNames.contains(ColName)) {
        val f = mongoDB.database.createCollection(ColName).toFuture()
        f onComplete {
          case Success(_)=>
            createDefaultGroup
          case Failure(exception)=>
            logger.error("failed", exception)
        }
      }
    }
  }

  init()

  def createDefaultGroup = {
    for(group <- defaultGroup) yield {
      val f = collection.insertOne(group).toFuture()
      f
    }
  }

  def newGroup(group: Group): Future[InsertOneResult] = {
    val f = collection.insertOne(group).toFuture()
    f.failed.foreach(ex=>logger.error("failed", ex))
    f
  }

  import org.mongodb.scala.model.Filters._

  def deleteGroupAsync(_id: String): Future[DeleteResult] = {
    val f = collection.deleteOne(equal("_id", _id)).toFuture()
    f.failed.foreach(ex=>logger.error("failed", ex))
    f
  }

  def updateGroupAsync(group: Group): Future[UpdateResult] = {
    val f = collection.replaceOne(equal("_id", group._id), group).toFuture()
    f.failed.foreach(ex=>logger.error("failed", ex))
    f
  }

  def getGroupByID(_id: String): Future[Group] = {
    val f = collection.find(equal("_id", _id)).first().toFuture()
    f.failed.foreach(ex=>logger.error("failed", ex))
    for(group<-f) yield
      group
  }

  def getAllGroupsAsync(): Future[Seq[Group]] = {
    val f = collection.find().toFuture()
    f.failed.foreach(ex=>logger.error("failed", ex))
    f
  }
}
