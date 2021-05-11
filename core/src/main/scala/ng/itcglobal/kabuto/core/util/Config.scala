package ng.itcglobal.kabuto.core.util

import com.typesafe.config.{Config, ConfigFactory, ConfigValue}

object Config {
	lazy val config: Config = ConfigFactory.load()
	config.checkValid(ConfigFactory.defaultReference)

	val dbUser    = config.getString("kabuto.postgres.user")
	val dbPasswd  = config.getString("kabuto.postgres.password")
	val dbName    = config.getString("kabuto.postgres.database")

	val testDbUser    = config.getString("kabuto.test-postgres.user")
	val testDbPasswd  = config.getString("kabuto.test-postgres.password")
	val testDbName    = config.getString("kabuto.test-postgres.database")
}
