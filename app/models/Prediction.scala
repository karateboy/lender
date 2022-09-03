package models

import models.ModelHelper._
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.model.Indexes
import org.mongodb.scala.model.Sorts.ascending
import org.mongodb.scala.result.UpdateResult
import play.api.Logging
import play.api.libs.json.Json

import java.time.{LocalDate, LocalTime, Period, ZoneId}
import java.util.Date
import scala.collection.mutable
import scala.concurrent.Future
import scala.util.{Failure, Success}

case class PredictionID(id1: ObjectId, id2: ObjectId, time: Date, cityId:String)
case class Prediction(_id: PredictionID, uuid:ObjectId, var winRate1: Option[Double] = None, var winRate2: Option[Double] = None,
                      var status: String = Prediction.StatusPending, createDate:Date, updateDate:Option[Date]=None)

object Prediction {
  val StatusPending = "pending"
  val StatusDone = "done"
  case class GamerJson(id:String, birthday:String, eightWords:Seq[String])
  case class EventJson(id:String, lunarDate:String, lunarTime:String, eightWords:Seq[String], city:Option[String] = None)
  case class PredictionInput(gamers:Seq[GamerJson], event:EventJson)
  case class WinRate(id:String, winRate:Double)
  case class PredictionOutput(success:Boolean, message:Option[String], eventId:String, result:Seq[WinRate])

  implicit val w1 = Json.writes[GamerJson]
  implicit val w2 = Json.writes[EventJson]
  implicit val w3 = Json.writes[WinRate]
  implicit val w4 = Json.writes[PredictionInput]
  implicit val w5 = Json.writes[PredictionOutput]

  implicit val r1 = Json.reads[GamerJson]
  implicit val r2 = Json.reads[EventJson]
  implicit val r3 = Json.reads[WinRate]
  implicit val r4 = Json.reads[PredictionInput]
  implicit val r5 = Json.reads[PredictionOutput]
}

import org.mongodb.scala.model._

import javax.inject._
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class PredictionOp @Inject()(mongoDB: MongoDB) extends Logging {

  import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
  import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
  import org.mongodb.scala.bson.codecs.Macros._

  val collectionName = "predictions"
  val codecRegistry = fromRegistries(fromProviders(classOf[Prediction], classOf[PredictionID]), DEFAULT_CODEC_REGISTRY)
  val collection = mongoDB.database.getCollection[Prediction](collectionName).withCodecRegistry(codecRegistry)
  collection.createIndex(Indexes.ascending("status"))
  collection.createIndex(Indexes.ascending("uuid"), IndexOptions().unique(true))

  implicit val log = logger
  def init() =
    for (colNames <- mongoDB.database.listCollectionNames().toFuture()) {
      if (!colNames.contains(collectionName)) {
        val f = mongoDB.database.createCollection(collectionName).toFuture()
        f onComplete{
          case Success(_) =>
            collection.createIndex(Indexes.descending("_id.id1", "_id.id2", "time"))
          case Failure(ex) =>
            logger.error("Error=>", ex)
            throw ex
        }
      }
    }

  init()

  def getPendingPrediction(): Future[Seq[Prediction]] = getPredictionStatusList(Prediction.StatusPending)

  def getPredictionStatusList(status: String): Future[Seq[Prediction]] = {
    val filter = Filters.equal("status", status)
    val f = collection.find(filter).sort(ascending("time")).toFuture()
    f onComplete completeHandler
    f
  }

  def upsertPredictionByUser(userId: ObjectId, items: Seq[ObjectId], start: LocalDate, end: LocalDate, cityId:String): Future[Seq[UpdateResult]] = {
    val predictionList: Seq[Prediction] =
      for {item <- items
           current <- getLocalDateIterator(start, end, Period.ofDays(1))
           } yield {
        val date = Date.from(current.atTime(LocalTime.of(9, 0)).atZone(ZoneId.systemDefault()).toInstant)
        Prediction(_id = PredictionID(userId, item, date, cityId), uuid = new ObjectId(), createDate = new Date())
      }

    val f =
      for (prediction <- predictionList) yield
        upsert(prediction)

    Future.sequence(f)
  }

  def upsert(prediction: Prediction): Future[UpdateResult] = {
    import org.mongodb.scala.model.ReplaceOptions
    val f = collection.replaceOne(Filters.equal("_id", prediction._id), prediction, ReplaceOptions().upsert(true)).toFuture()
    f onComplete completeHandler
    f
  }

  def getPredictionTimeMap(userId: ObjectId, names: Seq[ObjectId], start: Date, end: Date): Future[mutable.Map[Date, mutable.Map[ObjectId, Prediction]]] = {
    import scala.collection.mutable._
    val timeMap = Map.empty[Date, Map[ObjectId, Prediction]]
    for (predictionList <- query(userId, names, start, end)) yield {
      for (prediction <- predictionList) {
        val itemMap = timeMap.getOrElseUpdate(prediction._id.time, Map.empty[ObjectId, Prediction])
        itemMap.update(prediction._id.id2, prediction)
      }
      timeMap
    }
  }

  def getPredictionTiming(userId: ObjectId, names: Seq[ObjectId], start: Date, end: Date): Future[Map[ObjectId, Seq[Prediction]]] =
    for (predictionList <- query(userId, names, start, end)) yield
      predictionList.groupBy(p => p._id.id2)

  def query(userId: ObjectId, items: Seq[ObjectId], start: Date, end: Date): Future[Seq[Prediction]] = {
    val filter = Filters.and(Filters.equal("_id.id1", userId),
      Filters.in("_id.id2", items: _*),
      Filters.gte("_id.time", start), Filters.lt("_id.time", end)
    )
    val f = collection.find(filter).sort(ascending("time")).toFuture()
    f onComplete completeHandler
    f
  }
}

