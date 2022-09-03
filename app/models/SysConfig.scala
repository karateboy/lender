package models

import models.ModelHelper._
import org.mongodb.scala.bson._
import org.mongodb.scala.model._
import org.mongodb.scala.result.UpdateResult
import play.api.Logging
import play.api.libs.json._

import javax.inject._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions

@Singleton
class SysConfig @Inject()(mongodb: MongoDB) extends Logging {
  val ColName = "sysConfig"
  val collection = mongodb.database.getCollection(ColName)

  val valueKey = "value"
  val GameIdSeed = "GameIdSeed"

  implicit val log = logger
  val defaultConfig = Map(
    GameIdSeed -> Document(valueKey -> 1L))

  def init(): Unit = {
    for (colNames <- mongodb.database.listCollectionNames().toFuture()) {
      if (!colNames.contains(ColName)) {
        val f = mongodb.database.createCollection(ColName).toFuture()
        f onComplete completeHandler
      }
    }
    val values = Seq.empty[String]
    val idSet = values

    //Clean up unused
    val f1 = collection.deleteMany(Filters.not(Filters.in("_id", idSet.toList: _*))).toFuture()
    f1 onComplete completeHandler
    val updateModels =
      for ((k, defaultDoc) <- defaultConfig) yield {
        UpdateOneModel(
          Filters.eq("_id", k),
          Updates.setOnInsert(valueKey, defaultDoc(valueKey)), UpdateOptions().upsert(true))
      }

    val f2 = collection.bulkWrite(updateModels.toList, BulkWriteOptions().ordered(false)).toFuture()

    import scala.concurrent._
    val f = Future.sequence(List(f1, f2))
    waitReadyResult(f)
  }

  init()

  def upsert(_id: String, doc: Document): Future[UpdateResult] = {
    val uo = new ReplaceOptions().upsert(true)
    val f = collection.replaceOne(Filters.equal("_id", _id), doc, uo).toFuture()
    f onComplete completeHandler
    f
  }

  def get(_id: String): Future[BsonValue] = {
    val f = collection.find(Filters.eq("_id", _id.toString())).headOption()
    f onComplete completeHandler
    for (ret <- f) yield {
      val doc = ret.getOrElse(defaultConfig(_id))
      doc("value")
    }
  }

  def set(_id: String, v: BsonValue): Future[UpdateResult] = upsert(_id, Document(valueKey -> v))

  def getNextGameID: Future[Long] = {
    val f = collection.findOneAndUpdate(Filters.equal("_id", GameIdSeed),
      Updates.inc(valueKey, 1)).toFuture()
    f.failed.foreach(ex=>logger.error("failed to getNextGameID", ex))
    f.map(doc=>doc(valueKey).asInt64().longValue())
  }
}