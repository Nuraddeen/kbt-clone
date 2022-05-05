import mill._, scalalib._
import coursier.maven._


trait SharedModule extends  SbtModule {
  def scalaVersion = "2.13.3"

  def scalacOptions = Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Xlog-reflective-calls", 
    "-Xlint"
  )

  def repositories = super.repositories ++ Seq(
    MavenRepository("http://dl.bintray.com/africastalking/java/"),
    MavenRepository("https://oss.sonatype.org/content/repositories/releases")
  )
}

object core extends SharedModule  {

lazy val akkaHttpVersion = "10.2.2"
lazy val akkaVersion     = "2.6.10"
lazy val doobieVersion   = "0.12.1"
  
  def ivyDeps = Agg(
      ivy"com.typesafe.akka::akka-actor:$akkaVersion",
      ivy"com.typesafe.akka::akka-http-testkit:$akkaHttpVersion",
    
      ivy"com.typesafe.akka::akka-http:$akkaHttpVersion",
      ivy"com.typesafe.akka::akka-http-spray-json:$akkaHttpVersion",
      ivy"com.typesafe.akka::akka-actor-typed:$akkaVersion",
      ivy"com.typesafe.akka::akka-actor-testkit-typed:$akkaVersion",
      ivy"com.typesafe.akka::akka-stream:$akkaVersion",
      ivy"com.typesafe.akka::akka-persistence-typed:$akkaVersion",
      ivy"com.typesafe.akka::akka-persistence-query:$akkaVersion",
      ivy"com.typesafe.akka::akka-cluster-sharding-typed:$akkaVersion",
      ivy"com.typesafe.akka::akka-cluster-tools:$akkaVersion",
      ivy"com.typesafe.akka::akka-persistence-cassandra:1.0.3",
      ivy"ch.qos.logback:logback-classic:1.2.3",
      ivy"io.spray::spray-json:1.3.6",
      ivy"com.twelvemonkeys.imageio:imageio-tiff:3.6.4",
      ivy"com.twelvemonkeys.imageio:imageio-core:3.6.4",
      ivy"com.twelvemonkeys.contrib:contrib:3.6.4",
      ivy"com.sksamuel.scrimage:scrimage-core:4.0.18",
      ivy"com.github.pathikrit::better-files:3.9.1",
      ivy"org.tpolecat::doobie-core:0.12.1",
      ivy"org.tpolecat::doobie-hikari:0.12.1",          // HikariCP transactor.
      ivy"org.tpolecat::doobie-postgres:0.12.1",          // Postgres driver 42.2.19 + type mappings.
      ivy"org.tpolecat::doobie-quill:0.12.1",          // Support for Quill 3.6.1
      ivy"org.tpolecat::doobie-specs2:0.12.1", // Specs2 support for typechecking statements.
      ivy"org.tpolecat::doobie-scalatest:0.12.1", // ScalaTest support for typechecking statements.
      ivy"ch.megard::akka-http-cors:1.0.0"
  
      )

  object test extends Tests{
    def ivyDeps = Agg(
      ivy"org.scalactic::scalactic:3.1.1",
      ivy"org.scalatest::scalatest:3.2.2"
    )
    def testFrameworks = Seq("org.scalatest.tools.Framework")
  }

}

object web extends SharedModule{
  def mainClass    = Some("ng.itcglobal.kabuto.web.Server")
  def moduleDeps   = Seq(core,dms)

  object test extends Tests{
    def ivyDeps = Agg(
      ivy"org.scalactic::scalactic:3.1.1",
      ivy"org.scalatest::scalatest:3.2.2"
    )
    def testFrameworks = Seq("org.scalatest.tools.Framework")
  }
}

object dms extends SharedModule{
  def moduleDeps = Seq(core)

  object test extends Tests{
    def ivyDeps = Agg(
      ivy"org.scalactic::scalactic:3.1.1",
      ivy"org.scalatest::scalatest:3.2.2"
    )
    def testFrameworks = Seq("org.scalatest.tools.Framework")

   def testOnly(args: List[String]) = T.command {
      super.runMain("org.scalatest.run", args: _*)
    }
  }
}
