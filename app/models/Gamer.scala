package models

import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.model.{Filters, Indexes}
import org.mongodb.scala.result.UpdateResult
import play.api.Logging
import play.api.libs.json.Json

import java.util.Date
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import ModelHelper._
case class Gamer(_id: ObjectId, name: String, birthday: Date, cityId: String, lunarDateTime: LunarDateTime){
  import Prediction._
  def getGamerJson(): GamerJson =
    GamerJson(id=_id.toHexString, birthday = s"${lunarDateTime.year}-${lunarDateTime.month}-${lunarDateTime.day}",
      eightWords = lunarDateTime.eightWords)
}

object Gamer {
  implicit val r1 = Json.reads[LunarDateTime]
  implicit val w1 = Json.writes[LunarDateTime]
  implicit val write = Json.writes[Gamer]
  implicit val reads = Json.reads[Gamer]
}
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class GamerOp @Inject()(mongodb: MongoDB) extends Logging {
  import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
  import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
  import org.mongodb.scala.bson.codecs.Macros._


  val collectionName = "gamers"
  val codecRegistry = fromRegistries(fromProviders(classOf[Gamer], classOf[LunarDateTime]), DEFAULT_CODEC_REGISTRY)
  val collection = mongodb.database.getCollection[Gamer](collectionName).withCodecRegistry(codecRegistry)
  collection.createIndex(Indexes.ascending("name"))
  collection.createIndex(Indexes.ascending("birthday"))

  for (colNames <- mongodb.database.listCollectionNames().toFuture()) {
    if (!colNames.contains(collectionName)) {
      val f = mongodb.database.createCollection(collectionName).toFuture()
      f.failed.foreach(logger.error(s"failed to create $collectionName", _))
    }
  }

  def get(_id:ObjectId): Future[Gamer] ={
    val f = collection.find(Filters.equal("_id", _id)).first().toFuture()
    f.failed.foreach(logger.error("failed", _))
    f
  }

  def upsert(gamer:Gamer): Future[UpdateResult] ={
    val f = collection.replaceOne(Filters.equal("_id", gamer._id), gamer).toFuture()
    f.failed.foreach(logger.error("failed", _))
    f
  }
}