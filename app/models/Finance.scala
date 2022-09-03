package models

import org.apache.poi.ss.usermodel.{Cell, CellType, Row}
import play.api.Logger

import java.nio.file.Path
import java.time.format.{DateTimeFormatter, DateTimeParseException}
import java.time.{LocalDate, LocalDateTime, LocalTime, ZoneId, ZonedDateTime}
import scala.collection.mutable.TreeSet

case class StockIndex(date: ZonedDateTime, values: Seq[Double])
case class FinanceData(indexNames: Seq[String], indexTreeSet: TreeSet[StockIndex])
object Finance {
  val logger: Logger = Logger(this.toString)
  implicit val stockIndexOrder: Ordering[StockIndex] = Ordering.by(st => st.date)

  def handleFinanceData(path: Path, geoZoneId: ZoneId): FinanceData ={
    import org.apache.poi.ss.usermodel.WorkbookFactory

    import java.io.FileInputStream
    val wb = WorkbookFactory.create(new FileInputStream(path.toFile))
    val sheet = wb.getSheetAt(0)
    def getFinanceData()={
      var finish = false
      def getIndexNames(row:Row): Seq[String] = {
        var names = Seq.empty[String]
        var col = 1
        try{
          while(row.getCell(col).getStringCellValue.nonEmpty){
            names = names:+(row.getCell(col).getStringCellValue)
            col = col+1
          }
        }catch{
          case _:Throwable=>
        }
        names
      }
      val indexNames = getIndexNames(sheet.getRow(0))
      val dateFormat = DateTimeFormatter.ofPattern("yyyy-M-d")
      val dateFormat1 = DateTimeFormatter.ofPattern("yyyy/M/d")
      val indexTreeSet: TreeSet[StockIndex] = TreeSet.empty[StockIndex]
      val localTime = LocalTime.of(0, 0)
      var rowN = 1
      do {
        def handleDateCell(cell:Cell): ZonedDateTime ={
          if(cell.getCellType == CellType.NUMERIC){
            val date = cell.getDateCellValue()
            date.toInstant.atZone(ZoneId.systemDefault()).toLocalDate.atTime(localTime).atZone(geoZoneId)
          }else if(cell.getCellType == CellType.STRING){
            val dateStr = cell.getStringCellValue
            val localDate =
              try{
                LocalDate.parse(dateStr, dateFormat)
              }catch {
                case _:DateTimeParseException =>
                  LocalDate.parse(dateStr, dateFormat1)
              }
            localDate.atTime(localTime).atZone(geoZoneId)
          }else {
            finish = true
            throw new Exception("End of file")
          }
        }
        val row = sheet.getRow(rowN)
        if (row == null)
          finish = true
        else {
          try {
            val cell = row.getCell(0)
            val date: ZonedDateTime = handleDateCell(cell)
            def getIndexValues(row:Row): Seq[Double] = {
              var values = Seq.empty[Double]
              try{
                for(col<- 1 to indexNames.length)
                  values = values:+ row.getCell(col).getNumericCellValue
              }catch{
                case _:Throwable=>
              }
              values
            }
            val indexValues = getIndexValues(row)
            indexTreeSet.addOne(StockIndex(date, indexValues))
          }catch{
            case ex:Throwable=>
              if(!finish)
                logger.error(s"failed to handle $rowN row", ex)
          }
          rowN += 1
        }
      } while (!finish)
      FinanceData(indexNames, indexTreeSet)
    }

    getFinanceData()
  }
}
