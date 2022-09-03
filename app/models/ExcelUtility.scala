package models

import org.apache.poi.openxml4j.opc._
import org.apache.poi.ss.usermodel._
import org.apache.poi.xssf.usermodel._
import play.api.Logger

import java.io._
import java.nio.file._
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Instant, ZoneId, ZoneOffset, ZonedDateTime}
import javax.inject._
import scala.collection.mutable.ListBuffer
import scala.math.BigDecimal.RoundingMode

@Singleton
class ExcelUtility @Inject()
(environment: play.api.Environment, lunarCalendar: LunarCalendar) {
  val docRoot = s"${environment.rootPath}/report_template/"
  val logger = Logger(this.getClass)

  def createStyle(prec: Int)(implicit wb: XSSFWorkbook): XSSFCellStyle = {
    val format_str = if (prec != 0)
      "0." + "0" * prec
    else
      "0"

    val style = wb.createCellStyle();
    val format = wb.createDataFormat();
    val font = wb.createFont();
    font.setFontHeightInPoints(12);
    font.setFontName("正黑體");

    style.setFont(font)
    style.setDataFormat(format.getFormat(format_str))
    style.setBorderBottom(BorderStyle.THIN);
    style.setBottomBorderColor(IndexedColors.BLACK.getIndex());
    style.setBorderLeft(BorderStyle.THIN);
    style.setLeftBorderColor(IndexedColors.BLACK.getIndex());
    style.setBorderRight(BorderStyle.THIN);
    style.setRightBorderColor(IndexedColors.BLACK.getIndex());
    style.setBorderTop(BorderStyle.THIN);
    style.setTopBorderColor(IndexedColors.BLACK.getIndex());
    style
  }

  def exportFinanceMonthTab(cityInfo: CityInfo, financeData: FinanceData, reportType: FinanceReport.Value): File = {
    val (reportFilePath, pkg, wb) = prepareTemplate("financeMonthTab.xlsx")
    val format = wb.createDataFormat()
    val percentStyle = wb.createCellStyle()
    percentStyle.setDataFormat(format.getFormat("0.00"))

    val geoZoneId = ZoneId.of(ZoneOffset.ofHours(cityInfo.zoneOffset).getId)
    val indexLen = financeData.indexNames.length

    def fillData(sheet: Sheet): Unit = {
      val headerRow = sheet.getRow(0)
      for ((name, idx) <- financeData.indexNames.zipWithIndex) {
        headerRow.createCell(idx + 1).setCellValue(name)
        headerRow.createCell(idx + 1 + indexLen).setCellValue(s"Ret. $name")
      }

      val indexList = financeData.indexTreeSet.toList.sliding(2).filter(_.length == 2)

      for ((indexes, idx) <- indexList.zipWithIndex) {
        val row = sheet.createRow(idx + 1)
        val fmt = DateTimeFormatter.ofPattern("yyyy-M-d")
        row.createCell(0).setCellValue(indexes(1).date.toLocalDate.format(fmt))
        val valueSize = indexes(1).values.size
        for ((v, idx) <- indexes(1).values.zipWithIndex) {
          row.createCell(1 + idx).setCellValue(v)
          val percentage: Double = (v - indexes(0).values(idx)) * 100 / indexes(1).values(idx)
          val decimal = BigDecimal(percentage).setScale(2, RoundingMode.HALF_EVEN)
          row.createCell(1 + valueSize + idx).setCellValue(decimal.doubleValue)
        }

        val lunar = lunarCalendar.getLunarDateTime(indexes(1).date, geoZoneId, cityInfo.minuteOffset)
        val ew = lunar.eightWords
        row.createCell(1 + 2 * valueSize).setCellValue(s"${ew(0)}年${ew(1)}月${ew(2)}日")
      }
    }

    def getLunarMonthList(): Seq[Instant] = {
      val start = financeData.indexTreeSet.firstKey.date
      val end = financeData.indexTreeSet.lastKey.date
      val adjustEnd = LunarCalendar.newMoonTreeSet.minAfter(end.toInstant).getOrElse(end.toInstant)
      LunarCalendar.newMoonTreeSet.range(start.toInstant, adjustEnd.plusSeconds(1)).toList
    }

    def fillMonthReturn(sheet: Sheet, lunarMonthList: Seq[Instant]): Unit = {
      sheet.setAutobreaks(true)

      val leapMonthStyle = sheet.getRow(0).getCell(0).getCellStyle
      val headerStyle = sheet.getRow(1).getCell(0).getCellStyle
      sheet.getRow(0).createCell(2).setCellValue(cityInfo.name)
      // var lastLunarMonthOpt: Option[LunarDateTime] = None
      val headerRow = sheet.getRow(1)
      for ((name, idx) <- financeData.indexNames.zipWithIndex) {
        val endCell = headerRow.createCell(4 + idx)
        endCell.setCellValue(name)
        endCell.setCellStyle(headerStyle)
        val returnCell = headerRow.createCell(4 + financeData.indexNames.size + idx)
        returnCell.setCellValue(s"Ret. $name")
        returnCell.setCellStyle(headerStyle)
      }

      for ((dtRange, idx) <- lunarMonthList.sliding(2).filter(_.length == 2).zipWithIndex) {
        val row = sheet.createRow(idx + 3)
        def getFirstDayOfMonth(dt:Instant): ZonedDateTime =
          if(dt.atZone(geoZoneId).getHour <23)
            dt.atZone(geoZoneId).withHour(0).withMinute(0)
          else
            dt.atZone(geoZoneId).plusDays(1).withHour(0).withMinute(0)

        val monthStart = getFirstDayOfMonth(dtRange(0))
        val nextMonthStart = getFirstDayOfMonth(dtRange(1))

        val lunar = lunarCalendar.getLunarDateTime(monthStart, geoZoneId, cityInfo.minuteOffset)

        row.createCell(0).setCellValue(s"${lunar.year}-${lunar.month}-${lunar.day}")
        row.createCell(1).setCellValue(s"${monthStart.getYear}-${monthStart.getMonth.getValue}-${monthStart.getDayOfMonth}")
        val thisMonthEnd = nextMonthStart.minusDays(1)

        row.createCell(2).setCellValue(s"${thisMonthEnd.getYear}-${thisMonthEnd.getMonth.getValue}-${thisMonthEnd.getDayOfMonth}")
        for {lastMonthEnd <- financeData.indexTreeSet.maxBefore(StockIndex(monthStart, Seq.empty[Double]))
             thisMonthEnd <- financeData.indexTreeSet.maxBefore(StockIndex(nextMonthStart, Seq.empty[Double]))
             } {
          row.createCell(3).setCellValue(thisMonthEnd.date.format(DateTimeFormatter.ofPattern("yyyy/M/d")))
          for ((value, idx) <- thisMonthEnd.values.zipWithIndex) {
            val indexCell = row.createCell(4 + idx)
            val lastMonthIndex = lastMonthEnd.values
            indexCell.setCellValue(value)
            val percentage: Double = (value - lastMonthIndex(idx)) * 100 / lastMonthIndex(idx)
            val decimal = BigDecimal(percentage).setScale(2, RoundingMode.HALF_EVEN)
            val cell = row.createCell(4 + indexLen + idx)
            cell.setCellValue(decimal.doubleValue)
            cell.setCellStyle(percentStyle)
          }

          if (lunar.leapMonth) {
            for (i <- 0 to (3 + 2 * financeData.indexNames.size)) {
              val cell = row.getCell(i)
              if (cell != null)
                cell.setCellStyle(leapMonthStyle)
            }
          }
        }
      }
    }

    def fillTop3RangeDay(sheet: Sheet, lunarMonthList: Seq[Instant], valueIdx: Int): Unit = {
      sheet.setAutobreaks(true)
      val leapMonthStyle = sheet.getRow(0).getCell(0).getCellStyle
      val dayStyle = sheet.getRow(0).getCell(3).getCellStyle
      sheet.getRow(0).createCell(2).setCellValue(cityInfo.name)

      val rangeList = lunarMonthList.sliding(2).filter(x => x.length == 2)
      for ((range, monthIdx) <- rangeList.zipWithIndex) {
        val (monthStartInst, nextMonthStartInst) = (range.head, range.last)
        val monthRowN = 2 + 7 * monthIdx
        val monthRow = sheet.createRow(monthRowN)
        val monthStart = monthStartInst.atZone(geoZoneId).plusSeconds(1)
        val nextMonthStart = nextMonthStartInst.atZone(geoZoneId)
        val monthEnd = nextMonthStart.minusDays(1)
        val lunarMonthStart = lunarCalendar.getLunarDateTime(monthStart, geoZoneId, cityInfo.minuteOffset)
        monthRow.createCell(0).setCellValue(s"${lunarMonthStart.year}-${lunarMonthStart.month}-${lunarMonthStart.day}")
        monthRow.createCell(1).setCellValue(s"${monthStart.getYear}-${monthStart.getMonth.getValue}-${monthStart.getDayOfMonth}")
        monthRow.createCell(2).setCellValue(s"${monthEnd.getYear}-${monthEnd.getMonth.getValue}-${monthEnd.getDayOfMonth}")
        for {lastMonthEnd <- financeData.indexTreeSet.maxBefore(StockIndex(monthStart, Seq.empty[Double]))
             thisMonthEnd <- financeData.indexTreeSet.maxBefore(StockIndex(nextMonthStart, Seq.empty[Double]))
             gain = (thisMonthEnd.values(valueIdx) - lastMonthEnd.values(valueIdx)) / lastMonthEnd.values(valueIdx)
             gainCell = monthRow.createCell(4)
             } {
          gainCell.setCellValue(gain)
          gainCell.setCellStyle(dayStyle)
        }
        for {lastDayOfLastMonth <- financeData.indexTreeSet.maxBefore(StockIndex(monthStart, Seq.empty[Double]))
             rangeIndexes = financeData.indexTreeSet.range(lastDayOfLastMonth,
               StockIndex(nextMonthStart, Seq.empty[Double])).toList
             } {

          val rangeChangeIterator = rangeIndexes.sliding(2).filter(x => x.length == 2).map(range => {
            val current = range(1).values(valueIdx)
            val prev = range(0).values(valueIdx)
            val change = (current - prev) / prev
            (range(1), change)
          })

          val sortedRangeChange = rangeChangeIterator.toList.sortBy(_._2)
          val top3DayGain: List[(StockIndex, Double)] = sortedRangeChange.reverse.take(3)
          val top3DayLoss: List[(StockIndex, Double)] = sortedRangeChange.take(3).reverse
          val dayList = List(top3DayGain, top3DayLoss).flatten
          for (((stockIndex, change), dayIdx) <- dayList.zipWithIndex) {
            val dayRow = sheet.createRow(monthRowN + 1 + dayIdx)
            val dayCell = dayRow.createCell(3)
            dayCell.setCellStyle(dayStyle)
            dayCell.setCellValue(s"${stockIndex.date.getYear}-${stockIndex.date.getMonthValue}-${stockIndex.date.getDayOfMonth}")
            val changeCell = dayRow.createCell(4)
            changeCell.setCellValue(change)
            changeCell.setCellStyle(dayStyle)
            val breakCell = dayRow.createCell(5)
            breakCell.setCellStyle(dayStyle)
            val lunar = lunarCalendar.getLunarDateTime(stockIndex.date, geoZoneId, cityInfo.minuteOffset)
            val ew = lunar.eightWords
            for (i <- 0 to 2) {
              val cell = dayRow.createCell(6 + i)
              cell.setCellValue(s"${ew(i)}")
              cell.setCellStyle(dayStyle)
            }
          }
        }
      }
    }

    fillData(wb.getSheetAt(0))
    wb.cloneSheet(1, "斗數月報酬率(閏月獨立)")
    wb.cloneSheet(1, "斗數月報酬率(閏月併入上月)")
    wb.cloneSheet(1, "斗數月報酬率(閏月分割)")
    val lunarMonthList = getLunarMonthList()
    val skipLeapMonthList =
      for {dt <- lunarMonthList
           zdt = dt.atZone(geoZoneId).plusSeconds(1)
           lunar = lunarCalendar.getLunarDateTime(zdt, geoZoneId, cityInfo.minuteOffset) if !lunar.leapMonth
           } yield
        dt
    val splitLeapMonthList = {
      val lb = ListBuffer.empty[Instant]
      var list = lunarMonthList
      while (list.nonEmpty) {
        val dt = list.head
        list = list.drop(1)
        val zdt = dt.atZone(geoZoneId).plusSeconds(1)
        val lunar = lunarCalendar.getLunarDateTime(zdt, geoZoneId, cityInfo.minuteOffset)
        if (!lunar.leapMonth)
          lb.append(dt)
        else {
          lb.append(dt.plus(15, ChronoUnit.DAYS))
          if (list.nonEmpty)
            list = list.drop(1)
        }
      }
      lb.toList
    }
    val reportMonthList = Seq(lunarMonthList, skipLeapMonthList, splitLeapMonthList)
    for ((monthList, idx) <- reportMonthList.zipWithIndex) {
      reportType match {
        case FinanceReport.MonthlyFullReport =>
          fillMonthReturn(wb.getSheetAt(3 + idx), monthList)
      }
    }
    for ((name, idx) <- financeData.indexNames.zipWithIndex) {
      val sheet = wb.cloneSheet(2, s"月前三大漲跌日 ${name}")
      fillTop3RangeDay(sheet, lunarMonthList, idx)
    }

    wb.removeSheetAt(2)
    wb.removeSheetAt(1)
    finishExcel(reportFilePath, pkg, wb)
  }

  private def prepareTemplate(templateFile: String): (Path, OPCPackage, XSSFWorkbook) = {
    val templatePath = Paths.get(docRoot + templateFile)
    val reportFilePath = Files.createTempFile("temp", ".xlsx");

    Files.copy(templatePath, reportFilePath, StandardCopyOption.REPLACE_EXISTING)

    //Open Excel
    val pkg = OPCPackage.open(new FileInputStream(reportFilePath.toAbsolutePath().toString()))
    val wb = new XSSFWorkbook(pkg)

    (reportFilePath, pkg, wb)
  }

  def finishExcel(reportFilePath: Path, pkg: OPCPackage, wb: XSSFWorkbook): File = {
    val out = new FileOutputStream(reportFilePath.toAbsolutePath().toString());
    wb.write(out);
    out.close();
    pkg.close();

    new File(reportFilePath.toAbsolutePath().toString())
  }
}