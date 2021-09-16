package ng.itcglobal.kabuto
package core.db.postgres

import core.util.Config
import cats.effect.IO
import doobie.{ExecutionContexts, Meta, Transactor}
import doobie.quill.DoobieContext
import io.getquill.{mirrorContextWithQueryProbing, MappedEncoding, SnakeCase, idiom => _}

import java.time.{LocalDate, LocalDateTime, LocalTime, ZoneId}
import java.util.{Date, UUID}

import  ng.itcglobal.kabuto._
import core.db.postgres.Tables.DocumentMetadata


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
	implicit val uuidMeta: Meta[UUID] = Meta[String].timap(UUID.fromString)(_.toString)

	implicit val jodaLocalDateTimeDecoder = mirrorContextWithQueryProbing.MappedEncoding[Date, LocalDateTime](fromDateToLocalDateTime)
	implicit val jodaLocalDateTimeEncoder = MappedEncoding[LocalDateTime, Date](fromLocalDateTimeToDateTime)

//	implicit val jodaDurationDecoder      = MappedEncoding(Duration.millis)
//	implicit val jodaDurationEncoder      = MappedEncoding[Duration, Long](_.getMillis)

//	implicit val jodaDurationEncoder      = MappedEncoding[Duration, Long](_.getMillis)
//	implicit val jodaDurationDecoder      = MappedEncoding(Duration.millis)

	def fromDateToLocalDateTime(date: Date): LocalDateTime = {
		val local: LocalDate =
			date
				.toInstant
				.atZone(ZoneId.systemDefault())
				.toLocalDate;

		LocalDateTime.of(local, LocalTime.now)
	}

	def fromLocalDateTimeToDateTime(dateTime: LocalDateTime): Date = java.sql.Timestamp.valueOf(dateTime);



	
}