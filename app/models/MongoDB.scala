package models
import play.api._
import javax.inject._
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class MongoDB @Inject() (config: Configuration){
  import org.mongodb.scala._

  val url = config.get[String]("my.mongodb.url")
  val dbName = config.get[String]("my.mongodb.db")
  
  val mongodbClient: MongoClient = MongoClient(url)
  val database: MongoDatabase = mongodbClient.getDatabase(dbName);

  def getAuditDb(dbName:String) = mongodbClient.getDatabase(dbName)

  def cleanup={
    mongodbClient.close()
  }
}