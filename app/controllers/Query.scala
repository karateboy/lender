package controllers

import models.Highchart._
import models._
import org.mongodb.scala.bson.ObjectId
import play.api._
import play.api.libs.json._
import play.api.mvc._

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, LocalDateTime, ZoneId, ZonedDateTime}
import java.util.Date
import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import ModelHelper._

import scala.util.Random

class Query @Inject()(cityInfoOp: CityInfoOp ,lunarCalendar: LunarCalendar,cc: MessagesControllerComponents, predictionOp: PredictionOp, userOp: UserOp)
  extends MessagesAbstractController(cc) with Logging {

  val authorizedAction: Security.AuthenticatedBuilder[UserInfo] = AuthorizedAction(parse.defaultBodyParser)

  def winRateTrendChart(itemStr: String,
                        startNum: Long, endNum: Long): Action[AnyContent] = authorizedAction.async {
    implicit request =>
      val userInfo = request.user
      val items = itemStr.split(':').toSeq.map(new ObjectId(_))
      val start = LocalDateTime.from(Instant.ofEpochMilli(startNum).atZone(ZoneId.systemDefault()))
      val end = LocalDateTime.from(Instant.ofEpochMilli(endNum).atZone(ZoneId.systemDefault()))

      for(chart <- trendHelper(userInfo.uuid, items, start, end)) yield {
        Results.Ok(Json.toJson(chart))
      }
  }

  def trendHelper(userId:ObjectId, items: Seq[ObjectId], start: LocalDateTime, end: LocalDateTime): Future[HighchartData] = {
    val predictionTimeMapF = predictionOp.getPredictionTimeMap(userId, items,
      Date.from(start.atZone(ZoneId.systemDefault()).toInstant),
      Date.from(end.atZone(ZoneId.systemDefault()).toInstant))

    for(predictionTimeMap <- predictionTimeMapF) yield {
      val series =
        for(item <- items) yield {
          val timeData: Seq[Seq[Option[Double]]] =
            for (time <- predictionTimeMap.keys.toList.sorted) yield {
              val timeMap = predictionTimeMap(time)
              if(timeMap.contains(item)) {
                val prediction = timeMap(item)
                  Seq(Some(time.getTime.toDouble), prediction.winRate1.map(_*100))
              } else {
                Seq(Some(time.getTime.toDouble), None)
              }
            }
          seqData(s"${item}", timeData)
        }

      val dtf = DateTimeFormatter.ofPattern("YYYY年MM月dd日")
      val title =
        s"勝率趨勢圖 (${start.format(dtf)}~${end.format(dtf)})"

      val downloadFileName =
        start.format(DateTimeFormatter.ofPattern("YYMMdd"))

      val getAxisLines =
        Some(Seq(AxisLine("#FF0000", 2, 50, Some(AxisLineLabel("right", "勝率50%")))))

      val xAxis = XAxis(None)
      val chart =
        if (items.length == 1) {
          HighchartData(
            Map("type" -> "line"),
            Map("text" -> title),
            xAxis,
            Seq(YAxis(None, Some(AxisTitle(Some("勝率 (%)"))), getAxisLines)),
            series,
            Some(downloadFileName))
        } else {
          val yAxis =
            Seq(YAxis(None, Some(AxisTitle(Some("勝率 (%)"))), getAxisLines))

          HighchartData(
            Map("type" -> "line"),
            Map("text" -> title),
            xAxis,
            yAxis,
            series,
            Some(downloadFileName))
        }
      chart
    }
  }

  case class InvestTiming(item:ObjectId, bestDate:Date, bestWinRate:Option[Double],
                          worstDate:Date, worstWinRate:Option[Double])
  def getInvestItemTiming(): Action[AnyContent] = authorizedAction.async {
    implicit request =>
      val userInfo = request.user
      val start = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant)
      val end = Date.from(LocalDate.now().plusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant)
      for{
        user <- userOp.getUser(userInfo.id)
        predictionMap <- predictionOp.getPredictionTiming(user.uuid, user.items, start, end)
      } yield {
        val timingList = predictionMap.map(p=>{
          val winRates = p._2.map(_.winRate1)
          val max = winRates.max
          val min = winRates.min
          val best = p._2.find(p=>p.winRate1 == max).get
          val worst = p._2.find(p=>p.winRate1 == min).get
          InvestTiming(p._1, bestDate = best._id.time, bestWinRate = best.winRate1,
            worstDate = worst._id.time, worstWinRate = worst.winRate1)
        })
        implicit val writes = Json.writes[InvestTiming]
        Ok(Json.toJson(timingList.toList.sortBy(_.bestDate)))
      }
  }

  import Prediction._
  def getPendingGame()= Action {
    implicit request=>
      val cityInfo = cityInfoOp.map("台北")
      val m1 = Math.abs(Random.nextInt() % 3650)
      val zdt1 = ZonedDateTime.now().minusDays(m1)
      val ldt1 = lunarCalendar.getLunarDateTime(zdt1, ZoneId.of(cityInfo.zoneId), cityInfo.minuteOffset)
      val gamer1 = Gamer(_id = new ObjectId(), name = "gamer1",
        birthday = Date.from(zdt1.toInstant), lunarDateTime = ldt1, cityId = "台北")

      val m2 = Math.abs(Random.nextInt() % 3650)
      val zdt2 = ZonedDateTime.now().minusDays(m2)
      val ldt2 = lunarCalendar.getLunarDateTime(zdt2, ZoneId.of(cityInfo.zoneId), cityInfo.minuteOffset)
      val gamer2 = Gamer(_id = new ObjectId(), name = "gamer2",
        birthday = Date.from(zdt2.toInstant), lunarDateTime = ldt2, cityId = "台北")
      val gamers = Seq(gamer1.getGamerJson(), gamer2.getGamerJson())
      val m3 = Math.abs(Random.nextInt() % 365)
      val zdt3 = ZonedDateTime.now().plusDays(m3)
      val ldt3 = lunarCalendar.getLunarDateTime(zdt3, ZoneId.of(cityInfo.zoneId), cityInfo.minuteOffset)
      val eventJson = EventJson(new ObjectId().toHexString, lunarDate = s"${ldt3.year}-${ldt3.month}-${ldt3.day}",
        lunarTime = s"${ldt3.hour}:${ldt3.minute}", eightWords = ldt3.eightWords, city = Some("台北"))
      val pi = PredictionInput(gamers = gamers, event = eventJson)
      Ok(Json.toJson(pi))
  }

  def postPrediction(id:String) = Action(parse.json) {
    implicit request =>
    //val _id = new ObjectId(id)
      val param = request.body.validate[PredictionOutput]
      param.fold(
        error =>
          BadRequest(Json.obj("ok" -> false, "msg" -> JsError.toJson(error)))
        ,
        output => {
          logger.info(output.toString)
            Ok("")
        })
  }
}
