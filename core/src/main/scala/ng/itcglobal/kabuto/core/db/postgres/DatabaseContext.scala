package ng.itcglobal.kabuto
package core.db.postgres

import ng.itcglobal.kabuto._
import core.util.Config

import cats.effect.IO
import doobie.{ExecutionContexts, Transactor}
import doobie.quill.DoobieContext
import io.getquill.{MappedEncoding, SnakeCase}

import java.util.UUID
//import org.joda.time.{Duration, LocalDateTime}
import io.getquill.{idiom => _}

trait DatabaseContext {

	val user = Config.dbUser
	val pass = Config.dbPasswd
	val db   = Config.dbName

	implicit val cs = IO.contextShift(ExecutionContexts.synchronous)

	val xa = Transactor.fromDriverManager[IO](
		driver = "org.postgresql.Driver",
		url    = s"jdbc:postgresql:$db",
		user   = user,
		pass   = pass
	)

	val quillContext = new DoobieContext.Postgres(SnakeCase) with EncoderDecoder

}

trait EncoderDecoder{
	implicit val encodeUUID: MappedEncoding[UUID, String] = MappedEncoding[UUID, String](_.toString)
	implicit val decodeUUID: MappedEncoding[String, UUID] = MappedEncoding[String, UUID](UUID.fromString)
//	implicit val jodaLocalDateTimeDecoder = MappedEncoding[Date, LocalDateTime](LocalDateTime.fromDateFields)
//	implicit val jodaDurationDecoder      = MappedEncoding(Duration.millis)
//	implicit val jodaLocalDateTimeEncoder = MappedEncoding[LocalDateTime, Date](_.toDate)
//	implicit val jodaDurationEncoder      = MappedEncoding[Duration, Long](_.getMillis)
}