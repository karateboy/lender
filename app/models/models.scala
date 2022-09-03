package models

import org.mongodb.scala.bson.ObjectId
import play.api._
import play.api.libs.json._

import java.time.{LocalDate, LocalDateTime, Period}
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

/**
 * @author user
 */

object ModelHelper {
  implicit val readObjectId = new Reads[ObjectId]{
    override def reads(json: JsValue): JsResult[ObjectId] = json match {
      case JsString(s) => {
        try {
          JsSuccess(new ObjectId(s))
        } catch {
          case _: NoSuchElementException => JsError(s"Invalid ObjectId '$s'")
        }
      }
      case _ => JsError("String value expected")
    }
  }
  implicit val writeObjectId = new Writes[ObjectId]{
    override def writes(o: ObjectId): JsValue = JsString(o.toHexString)
  }

  def completeHandler[T](implicit logger: Logger): PartialFunction[Try[T], Any] = {
    case Failure(exception) =>
      logger.error("Error=>", exception)
      throw exception
    case Success(_)=>
  }

  def errorHandler(implicit logger: Logger): PartialFunction[Throwable, Any] = {
    case ex: Throwable =>
      logger.error("Error=>", ex)
      throw ex
  }

  def errorHandler(prompt: String = "Error=>")(implicit logger: Logger): PartialFunction[Throwable, Any] = {
    case ex: Throwable =>
      logger.error(prompt, ex)
      throw ex
  }

  import scala.concurrent._

  def waitReadyResult[T](f: Future[T])(implicit logger: Logger) = {
    import scala.concurrent.duration._
    import scala.util._

    val ret = Await.ready(f, Duration.Inf).value.get

    ret match {
      case Success(t) =>
        t
      case Failure(ex) =>
        logger.error(ex.getMessage, ex)
        throw ex
    }
  }

  def getLocalDateIterator(start: LocalDate, end: LocalDate, step: Period): Iterator[LocalDate] = {
    new Iterator[LocalDate] {
      var current = start

      override def hasNext: Boolean = current.plus(step).isBefore(end)

      override def next(): LocalDate = {
        if (hasNext) {
          current = current.plus(step)
          current
        } else
          throw new Exception("past end")
      }
    }
  }
  def getLocalDateTimeIterator(start: LocalDateTime, end: LocalDateTime, step: Period): Iterator[LocalDateTime] = {
    new Iterator[LocalDateTime] {
      var current = start

      override def hasNext: Boolean = current.plus(step).isBefore(end)

      override def next(): LocalDateTime = {
        if (hasNext) {
          current = current.plus(step)
          current
        } else
          throw new Exception("past end")
      }
    }
  }
}
