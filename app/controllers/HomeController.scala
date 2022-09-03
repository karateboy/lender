package controllers

import models._
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc._

import java.nio.file.Files
import java.time.format.TextStyle
import java.time.{LocalDateTime, ZoneId, ZoneOffset, ZonedDateTime}
import java.util.Locale
import javax.inject._
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

case class ZoneInfoEntry(id: String, text: String)

class HomeController @Inject()(lunarCalendar: LunarCalendar, cc: MessagesControllerComponents,
                               excelUtility: ExcelUtility, cityInfoOp: CityInfoOp)
                              (implicit ec: ExecutionContext) extends MessagesAbstractController(cc) with Logging {
  implicit val write = Json.writes[LunarDateTime]
  implicit val w2 = Json.writes[ZoneInfoEntry]



  val authorizedAction = AuthorizedAction(parse.defaultBodyParser)
  def index: Action[AnyContent] = Action {
    implicit request =>
      Redirect("/dist/")
  }

  def zoneIDList: Action[AnyContent] = authorizedAction {
    val list =
      for (id <- ZoneId.getAvailableZoneIds.asScala.toList.sorted) yield
        ZoneInfoEntry(id, s"${id}, ${ZoneId.of(id).getDisplayName(TextStyle.FULL, Locale.getDefault)}")

    Ok(Json.toJson(list))
  }

  def getLunarDateTime(cityName: String, year: Int, month: Int, day: Int, hour: Int, minute: Int): Action[AnyContent] =
    authorizedAction {
    val cityInfo = cityInfoOp.map(cityName)
    val localDateTime = LocalDateTime.of(year, month, day, hour, minute).plusMinutes(cityInfo.minuteOffset)
    val cityZoneId = ZoneId.of(cityInfo.zoneId)
    val zdt = if(year > 1900)
      localDateTime.atZone(cityZoneId)
    else
      localDateTime.atZone(ZoneId.of(ZoneOffset.ofHours(cityInfo.zoneOffset).getId))

    try{
      val lunar = lunarCalendar.getLunarDateTime(zdt,
        ZoneId.of(ZoneOffset.ofHours(cityInfo.zoneOffset).getId), cityInfo.minuteOffset)
      Ok(Json.toJson(lunar))
    }catch{
      case ex:Throwable=>
        BadRequest(ex.getMessage)
    }
  }

  def getLunarDateTimeNow(cityName: String)= authorizedAction {
    val cityInfo = cityInfoOp.map(cityName)
    val cityZoneId = ZoneId.of(cityInfo.zoneId)
    val zonedDateTime = ZonedDateTime.now(cityZoneId ).plusMinutes(cityInfo.minuteOffset)
    val lunar = lunarCalendar.getLunarDateTime(zonedDateTime,
      ZoneId.of(ZoneOffset.ofHours(cityInfo.zoneOffset).getId), cityInfo.minuteOffset)
    Ok(Json.toJson(lunar))
  }

  def getUserRaceLunarDateTime(userCityName:String, year: Int, month: Int,
                               day: Int, hour: Int, minute: Int, raceCityName:String): Action[AnyContent] =
    authorizedAction {
    val userCityInfo = cityInfoOp.map(userCityName)
    val raceCityInfo = cityInfoOp.map(raceCityName)
    val localDateTime = LocalDateTime.of(year, month, day, hour, minute)
    val userZoneDateTime = localDateTime.atZone(ZoneId.of(userCityInfo.zoneId))
    try{
      val lunar = lunarCalendar.getLunarDateTime(userZoneDateTime.plusMinutes(raceCityInfo.minuteOffset),
        ZoneId.of(ZoneOffset.ofHours(raceCityInfo.zoneOffset).getId), raceCityInfo.minuteOffset)
      Ok(Json.toJson(lunar))
    }catch {
      case ex:Throwable=>
        BadRequest(ex.getMessage)
    }

  }

  def getCityInfos(): Action[AnyContent] = authorizedAction {
    Ok(Json.toJson(cityInfoOp.list))
  }

  def postFinanceData(cityName:String): Action[MultipartFormData[play.api.libs.Files.TemporaryFile]] =
    authorizedAction(parse.multipartFormData) {
    implicit request =>
      val dataFileOpt = request.body.file("data")
      if (dataFileOpt.isEmpty) {
        logger.info("data is empty..")
        Ok(Json.obj("ok" -> true))
      } else {
        val dataFile = dataFileOpt.get
        val cityInfo = cityInfoOp.map(cityName)
        val geoZoneId = ZoneId.of(ZoneOffset.ofHours(cityInfo.zoneOffset).getId)
        val financeData = Finance.handleFinanceData(dataFile.ref.path, geoZoneId)
        val reportType = FinanceReport.apply(FinanceReport.MonthlyFullReport.id)
        val excelFile = excelUtility.exportFinanceMonthTab(cityInfo, financeData, reportType)
        try{
          Ok.sendFile(excelFile, fileName = _ => Some(s"${financeData.indexNames}斗曆月報酬率.xlsx"),
            onClose = () => {
              Files.deleteIfExists(excelFile.toPath())
            })
        }catch {
          case ex:Throwable=>
            BadRequest(ex.getMessage)
        }
      }
  }
}
