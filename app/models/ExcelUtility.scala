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
(environment: play.api.Environment) {
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