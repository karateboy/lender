package models

import com.opencsv.CSVReaderHeaderAware
import play.api.Logger

import java.io.{BufferedReader, FileInputStream, InputStreamReader}
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time._
import java.time.format.{DateTimeFormatter, TextStyle}
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject._
import scala.collection.immutable.TreeSet
import scala.collection.mutable
import scala.jdk.CollectionConverters._

case class SolarTerm(dt: Instant, name: String)

object LunarCalendar {
  val logger: Logger = Logger("LunarCalendar")
  val centralSolarTerms = List("雨水", "春分", "穀雨", "小滿", "夏至", "大暑", "處暑", "秋分", "霜降", "小雪", "冬至", "大寒")
  val centralSolarMonthMap = centralSolarTerms.zipWithIndex.toMap
  var newMoonTreeSet: TreeSet[Instant] = TreeSet.empty[Instant]
  implicit val solarTermOrder: Ordering[SolarTerm] = Ordering.by(st => st.dt)
  var winterSolsticeTreeSet: TreeSet[Instant] = TreeSet.empty[Instant]
  var solarTermTreeSet: TreeSet[SolarTerm] = TreeSet.empty[SolarTerm]

  def load(path: Path): Unit = {
    val DATETIME_KEY = "datetime_str"
    val ASTRO_OBJ_KEY = "astro_obj"
    val ASTRO_OBJ_EVENT_TYPE_KEY = "astro_obj_event_type"
    val MOON = "MOON"
    val SUN = "SUN"

    val tabZoneID = ZoneId.of("GMT+08:00")
    logger.info(s"tab zoneID = ${tabZoneID.getId}")
    val reader = {
      val fis = new FileInputStream(path.toFile);
      val isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
      val br = new BufferedReader(isr)
      new CSVReaderHeaderAware(br)
    }
    var map: mutable.Map[String, String] = null
    do {
      map = reader.readMap().asScala
      if (map != null) {
        val dtStr = map(DATETIME_KEY)
        val dt = LocalDateTime.parse(dtStr, DateTimeFormatter.ofPattern("yyyy-M-d HH:mm:ss"))
          .atZone(tabZoneID).toInstant
        val astroObj = map(ASTRO_OBJ_KEY)
        val astroObjEvent = map(ASTRO_OBJ_EVENT_TYPE_KEY)
        if (astroObj == MOON) {
          if (astroObjEvent == "朔")
            newMoonTreeSet = newMoonTreeSet.+(dt)
        } else if (astroObj == SUN) {
          if (astroObjEvent == "冬至")
            winterSolsticeTreeSet = winterSolsticeTreeSet.+(dt)

          val solarTerm = SolarTerm(dt.atZone(ZoneId.systemDefault()).toInstant, astroObjEvent)
          solarTermTreeSet = solarTermTreeSet.+(solarTerm)
        } else
          logger.error(s"Unexpected astro_obj $astroObj")
      }
    } while (map != null)
    reader.close()
    logger.info(s"newMoonTreeSet first=${newMoonTreeSet.firstKey} last=${newMoonTreeSet.lastKey}")
    logger.info(s"solarTermTreeSet first=${solarTermTreeSet.firstKey} last=${solarTermTreeSet.lastKey}")
    logger.info(s"winterTreeSet first=${winterSolsticeTreeSet.firstKey} last=${winterSolsticeTreeSet.lastKey}")
  }

}

case class LunarDateTime(year: Int, month: Int, day: Int, hour: Int, minute: Int, leapMonth: Boolean,
                         daylightSaving: Boolean, eightWords: Seq[String], dayOfWeek: String)

@Singleton
class LunarCalendar @Inject()(environment: play.api.Environment) {

  import LunarCalendar._

  load(environment.rootPath.toPath.resolve("conf/astro.csv"))

  def getLunarDateTime(zonedDateTime: ZonedDateTime, geoZoneId: ZoneId, zoneMintueOffset:Int): LunarDateTime = {
    val dt = zonedDateTime.toInstant
    logger.debug(s"ldt=${dt.toString}")
    val dayOfWeek = zonedDateTime.getDayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINESE)
    // 1. determine closest 冬至
    val ret =
      for {
        winterSolstice <- winterSolsticeTreeSet.maxBefore(dt)
        nextWinterSolstice <- winterSolsticeTreeSet.minAfter(dt)
        lunarNov <- newMoonTreeSet.maxBefore(winterSolstice)
      } yield {
        logger.debug(s"previous winter solstice = $winterSolstice")
        logger.debug(s"next winter solstice = $nextWinterSolstice")
        logger.debug(s"lunarNov = $lunarNov")

        val yearMonthes = newMoonTreeSet.range(winterSolstice, nextWinterSolstice).toList.+:(lunarNov)
        logger.debug(s"yearMonth #=${yearMonthes.size} $yearMonthes")

        def adjustDayStart(instant: Instant) = {
          val adjustZoneDateTime = instant.atZone(geoZoneId).plusMinutes(zoneMintueOffset)
          if(adjustZoneDateTime.getHour < 23)
            adjustZoneDateTime.minusDays(1)
            .withHour(23).withMinute(0).withSecond(0).withNano(0)
          else
            adjustZoneDateTime
              .withHour(23).withMinute(0).withSecond(0).withNano(0)
        }

        val (month: Int, monthStart: ZonedDateTime, leapMonth: Boolean) =
          if (yearMonthes.length == 13) { // 不須處理閏月
            val yearMonthList = yearMonthes.filter(inst => adjustDayStart(inst).isBefore(zonedDateTime))
              .zip(Seq(11, 12, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11))
            val (firstDayOfMonth, num) = yearMonthList.last
            if (yearMonthList.length >= 3)
              (num, adjustDayStart(firstDayOfMonth), false)
            else
              (num, adjustDayStart(firstDayOfMonth), false)
          } else {
            var hasLeapMonthBefore = false
            val yearMonthList = yearMonthes.filter(inst => adjustDayStart(inst).isBefore(zonedDateTime))
            val extraMonthList: Seq[(ZonedDateTime, Boolean, Int)] = {
              for (newMoon <- yearMonthList) yield {
                if (!hasLeapMonthBefore) {
                  val start = SolarTerm(newMoon, "月初")
                  val end = SolarTerm(newMoonTreeSet.minAfter(newMoon.plus(1, ChronoUnit.DAYS)).get, "下月初")
                  val solarTerms = solarTermTreeSet.range(start, end)
                  val leapMonth = !solarTermTreeSet.range(start, end).exists(p => centralSolarTerms.contains(p.name))
                  hasLeapMonthBefore = leapMonth
                  (adjustDayStart(newMoon), leapMonth, 8)
                } else
                  (adjustDayStart(newMoon), false, 0)
              }
            }
            val lunarMonthNumber = extraMonthList.count(!_._2)
            val (lunarMonth, leapMonth, leapMonthNum) = extraMonthList.last
            if (lunarMonthNumber + 10 > 12)
              ((lunarMonthNumber + 10) % 12, lunarMonth, leapMonth)
            else
              (lunarMonthNumber + 10, lunarMonth, leapMonth)
          }


        val year =
          if (month < 12) {
            monthStart.getYear
          } else if (month == 12 && monthStart.getMonthValue == 12) {
            monthStart.getYear
          } else
            monthStart.getYear - 1

        val day = (ChronoUnit.DAYS.between(monthStart, zonedDateTime) + 1).toInt
        val dayStart = monthStart.plus(day - 1, ChronoUnit.DAYS)
        val hours = ChronoUnit.HOURS.between(dayStart, zonedDateTime)
        val hour = if (hours == 0)
          23
        else
          hours - 1

        val daylightSaving = hour.toInt != zonedDateTime.getHour

        val minute = ChronoUnit.MINUTES.between(dayStart, zonedDateTime) % 60
        LunarDateTime(year, month, day, hour.toInt, minute.toInt, leapMonth = leapMonth, daylightSaving = daylightSaving,
          eightWords = getEightWords(year, month, hour.toInt, zonedDateTime.toLocalDateTime), dayOfWeek = dayOfWeek)
      }
    ret.getOrElse(throw new Exception("Out of support range!"))
  }

  def getEightWords(year: Int, month: Int, hour: Int, dt: LocalDateTime): Seq[String] = {
    val 天干: Array[Char] = "甲乙丙丁戊己庚辛壬癸".toArray
    val 地支: Array[Char] = "子丑寅卯辰巳午未申酉戌亥".toArray
    val 干支組合: Seq[String] =
      for (i <- 0 to 59) yield
        s"${天干(i % 10)}${地支(i % 12)}"

    val 年柱: String = 干支組合(Math.floorMod(year - 1984, 60))
    val 月柱: String = {
      val 年干: Char = 年柱(0)
      val monthPos =
        if (年干 == '甲' || 年干 == '己')
          2
        else if (年干 == '乙' || 年干 == '庚')
          4
        else if (年干 == '丙' || 年干 == '辛')
          6
        else if (年干 == '丁' || 年干 == '壬')
          8
        else
          0

      val 月干: Char = 天干((monthPos + month - 1) % 10);
      val 月支 = 地支((month + 1) % 12)
      s"${月干}${月支}"
    }
    val 日柱: String = {
      logger.debug(s"${dt}")
      val 甲子日 = LocalDate.of(2006, 12, 1)
      val days = if (hour < 23)
        ChronoUnit.DAYS.between(甲子日, dt)
      else
        ChronoUnit.DAYS.between(甲子日, dt) + 1

      干支組合(Math.floorMod(days, 60).toInt)
    }

    val 時柱 = {
      val 日干 = 日柱(0)

      val timePos =
        if (日干 == '甲' || 日干 == '己')
          0
        else if (日干 == '乙' || 日干 == '庚')
          2
        else if (日干 == '丙' || 日干 == '辛')
          4
        else if (日干 == '丁' || 日干 == '壬')
          6
        else
          8
      val 時干: Char = if (hour == 0 || hour == 23)
        天干(timePos)
      else
        天干((timePos + (hour - 1) / 2 + 1) % 10)

      val 時支: Char = if (hour == 0)
        地支(0)
      else
        地支(((hour - 1) / 2 + 1) % 12)

      s"${時干}${時支}"
    }
    Seq(年柱, 月柱, 日柱, 時柱)
  }
}
