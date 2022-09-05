package models

import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.model._
import org.mongodb.scala.bson.codecs.Macros._
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Indexes
import org.mongodb.scala.result.InsertOneResult
import play.api.Logging
import play.api.libs.json.Json
import models.ModelHelper._
import java.util.Date
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

case class BookLog(_id: ObjectId, time: Date, userId: String, bookId: ObjectId, log: String, var title:Option[String] = None)

object BookLog {
  implicit val w1 = Json.writes[BookLog]
  def apply(userId: String, bookId: ObjectId, log: String): BookLog =
    BookLog(_id = new ObjectId(), time = new Date(), userId = userId, bookId = bookId, log)
}

@Singleton
class BookLogOp @Inject()(mongoDB: MongoDB) extends Logging {
  val ColName = "bookLogs"
  val codecRegistry = fromRegistries(fromProviders(classOf[BookLog]), DEFAULT_CODEC_REGISTRY)
  val collection: MongoCollection[BookLog] = mongoDB.database.withCodecRegistry(codecRegistry).getCollection(ColName)

  def log(_log: BookLog): Future[InsertOneResult] =
    collection.insertOne(_log).toFuture()

  init()

  collection.createIndex(Indexes.descending("time", "userId", "bookId"))
  collection.createIndex(Indexes.descending("userId", "bookId"))

  private def init(): Unit = {
    for (colNames <- mongoDB.database.listCollectionNames().toFuture()) {
      if (!colNames.contains(ColName)) {
        val f = mongoDB.database.createCollection(ColName).toFuture()
        f onComplete {
          case Success(_) =>

          case Failure(exception) =>
            logger.error("failed", exception)
        }
      }
    }
  }

  def getLogsByUserId(userId:String): Future[Seq[BookLog]] =
    collection.find(equal("userId", userId)).sort(Sorts.descending("time")).limit(100)
      .toFuture()

}
