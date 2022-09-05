package models

import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.MongoCollection
import play.api.Logging
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.codecs.Macros._
import org.mongodb.scala.result.{DeleteResult, InsertOneResult, UpdateResult}
import play.api.libs.json.Json

import java.util.Date
import javax.inject.{Inject, Singleton}
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import models.ModelHelper._
import org.mongodb.scala.model.Updates

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.collection.mutable
case class Book(_id:ObjectId, title:String, groupId:String, lender:Option[String] = None,
                lentDate:Option[Date] = None, dueDate:Option[Date] = None)
object Book {
  implicit val r1 = Json.reads[Book]
  implicit val w1 = Json.writes[Book]

  val defaultBooks = Seq(
    Book(_id = new ObjectId(), "原子習慣", Group.GROUP1),
    Book(_id = new ObjectId(), "底層邏輯：看清這個世界的底牌", Group.GROUP1),
    Book(_id = new ObjectId(), "任何人都可以寫出神級提案‧好企劃：照著寫！ 10種固定格式、15個教戰範例， 掌握７大訣竅，一次就過關！", Group.GROUP1),
    Book(_id = new ObjectId(), "蛤蟆先生去看心理師", Group.GROUP1),
    Book(_id = new ObjectId(), "暗示自我的力量：輕鬆駕馭你不受控的小心思，斷絕惡習、清理疾病、完成所有你在乎的事！", Group.GROUP1),
  )
}

@Singleton
class BookOp @Inject()(mongoDB: MongoDB, bookLogOp: BookLogOp) extends Logging {
  val ColName = "books"
  val codecRegistry = fromRegistries(fromProviders(classOf[Book]), DEFAULT_CODEC_REGISTRY)
  val collection: MongoCollection[Book] = mongoDB.database.withCodecRegistry(codecRegistry).getCollection(ColName)

  import Book._
  private def init(): Unit = {
    for(colNames <- mongoDB.database.listCollectionNames().toFuture()){
      if (!colNames.contains(ColName)) {
        val f = mongoDB.database.createCollection(ColName).toFuture()
        f onComplete {
          case Success(_)=>
            collection.insertMany(defaultBooks).toFuture()
          case Failure(exception)=>
            logger.error("failed", exception)
        }
      }
    }
  }

  init()

  def newBook(book: Book): Future[InsertOneResult] = {
    val f = collection.insertOne(book).toFuture()
    f.failed.foreach(ex=>logger.error("failed", ex))
    f
  }

  import org.mongodb.scala.model.Filters._

  def deleteBookAsync(_id: ObjectId): Future[DeleteResult] = {
    val f = collection.deleteOne(equal("_id", _id)).toFuture()
    f.failed.foreach(ex=>logger.error("failed", ex))
    f
  }

  def updateBookAsync(book:Book): Future[UpdateResult] = {
    val f = collection.replaceOne(equal("_id", book._id), book).toFuture()
    f.failed.foreach(ex=>logger.error("failed", ex))
    f
  }

  def borrowBookAsync(bookID:ObjectId, user:String): Future[Book] ={
    val now = Instant.now()
    val dueDate = now.plus(14, ChronoUnit.DAYS)
    val updates = Updates.combine(
      Updates.set("lender", user),
      Updates.set("lentDate",  Date.from(now)),
      Updates.set("dueDate", Date.from(dueDate))
    )
    val f = collection.findOneAndUpdate(equal("_id", bookID), updates).toFuture()
    bookLogOp.log(BookLog(user, bookID, "借出"))
    f
  }

  def returnBookAsync(bookID:ObjectId): Future[Book] ={
    val updates = Updates.combine(
      Updates.set("lender", null),
      Updates.set("lentDate",  null),
      Updates.set("dueDate", null)
    )
    val f = collection.findOneAndUpdate(equal("_id", bookID), updates).toFuture()
    f.foreach(book=>bookLogOp.log(BookLog(book.lender.getOrElse(""), bookID, "歸還")))
    f
  }

  def getBookByID(_id: ObjectId): Future[Seq[Book]] = {
    val f = collection.find(equal("_id", _id)).toFuture()
    f.failed.foreach(ex=>logger.error("failed", ex))
    for(books<-f) yield
      books
  }

  def getAllGroupBooksAsync(groupId:String): Future[Seq[Book]] = {
    val f = collection.find(equal("groupId", groupId)).toFuture()
    f.failed.foreach(ex=>logger.error("failed", ex))
    f
  }

  def getBookLentByUser(userID:String): Future[Seq[Book]] = {
    val f = collection.find(equal("lender", userID)).toFuture()
    f.failed.foreach(ex=>logger.error("failed", ex))
    f
  }

  def getBookMapFromIds(bookIds:Seq[ObjectId]): Future[mutable.Map[ObjectId, Book]] = {
    val f = collection.find(in("_id", bookIds:_*)).toFuture()
    f.failed.foreach(ex=>logger.error("failed", ex))
    for(ret<-f) yield {
      import scala.collection.mutable.Map
      val map = Map.empty[ObjectId, Book]
      ret.foreach(book=>map.update(book._id, book))
      map
    }
  }
}
