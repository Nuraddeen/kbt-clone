package ng.itcglobal.kabuto.core.util

import com.typesafe.config.ConfigFactory

object Config {
	lazy val config = ConfigFactory.load()
	config.checkValid(ConfigFactory.defaultReference)

	val dbUser:         String  = config.getString("kabuto.postgres.user")
	val dbPasswd:       String  = config.getString("kabuto.postgres.password")
	val dbName:         String  = config.getString("kabuto.postgres.database")

	val testDbUser:     String  = config.getString("kabuto.test-postgres.user")
	val testDbPasswd:   String  = config.getString("kabuto.test-postgres.password")
	val testDbName:     String  = config.getString("kabuto.test-postgres.database")
	val applicationIpAddress    = config.getString("kabuto.ip")
  val applicationPortNumber   = config.getInt("kabuto.port")

	val filesDirectory: String  = config.getString("kabuto.base-files-directory")
}
