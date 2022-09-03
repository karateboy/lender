package models

import play.api.{Logger, Logging}
import play.api.libs.json.Json

import java.nio.file.Path
import javax.inject.{Inject, Singleton}
import scala.collection.mutable.ListBuffer

case class CityInfo(zoneId: String, name: String, zoneOffset: Int, minuteOffset: Int, lat: Double, lng: Double)

object CityInfo {
  implicit val reads = Json.reads[CityInfo]
  implicit val writes = Json.writes[CityInfo]
}
@Singleton
class CityInfoOp @Inject()(environment: play.api.Environment) extends Logging {

  implicit val writes = Json.writes[CityInfo]

  def map: Map[String, CityInfo] = list.map(ci => ci.name -> ci).toMap

  var list = List.empty[CityInfo]

  def load(path: Path): Unit = {
    //Open Excel
    import org.apache.poi.ss.usermodel.WorkbookFactory

    import java.io.FileInputStream
    val wb = WorkbookFactory.create(new FileInputStream(path.toFile))
    val sheet = wb.getSheetAt(0)
    var rowN = 1
    var finish = false
    val cityInfoList = ListBuffer.empty[CityInfo]
    do {
      val row = sheet.getRow(rowN)
      if (row == null)
        finish = true
      else {
        try {
          val name = row.getCell(0).getStringCellValue
          val zoneId: String = row.getCell(1).getStringCellValue
          if (zoneId.isEmpty) {
            finish = true
            throw new Exception("End of file")
          }
          val zoneOffset = row.getCell(2).getNumericCellValue.toInt
          val mintueOffset = row.getCell(3).getNumericCellValue.toInt
          val lat = row.getCell(4).getNumericCellValue
          val lng = row.getCell(5).getNumericCellValue
          cityInfoList.append(CityInfo(zoneId = zoneId, name = name, zoneOffset = zoneOffset,
            minuteOffset = mintueOffset, lat = lat, lng = lng))
        } catch {
          case ex: Throwable =>
            if (!finish)
              logger.error(s"failed to handle $rowN row", ex)
        }
        rowN += 1
      }
    } while (!finish)
    list = cityInfoList.toList
  }

  load(environment.rootPath.toPath.resolve("conf/cityInfo.xlsx"))
}
