

lazy val akkaHttpVersion = "10.2.2"
lazy val akkaVersion     = "2.6.10"
lazy val circeVersion    = "0.14.0-M2"


lazy val sharedSettings = Seq(
  organization := "ng.itcglobal",
  version      := "0.1.0",
  scalaVersion := "2.13.3",
  trapExit := false,
  scalacOptions ++= Seq(
    "-deprecation", 
    "-feature", 
    "-unchecked"
  ))


lazy val kabuto = (project in file("."))
  .aggregate(core, web, dms).settings()

lazy val web = (project in file("web"))
  .dependsOn(core, dms)
  .settings(
    sharedSettings, 
    mainClass in assembly := Some("ng.itcglobal.kabuto.web.Main")
  )

lazy val dms = (project in file("dms"))
  .dependsOn(core)
  .settings(
    sharedSettings, 
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http-testkit"        % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion     % Test,
      "org.scalatest"     %% "scalatest"                % "3.2.2"         % Test, 
    )
  )

lazy val core = (project in file("core")).
  
  settings(
    sharedSettings, 
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http"                    % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json"         % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-actor-typed"             % akkaVersion,
      "com.typesafe.akka" %% "akka-stream"                  % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-typed"       % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-query"       % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding-typed"  % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-tools"           % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-cassandra"   % "1.0.3",
      "ch.qos.logback"    % "logback-classic"               % "1.2.3",
      "io.spray"          %%  "spray-json"                  % "1.3.6",
      "de.heikoseeberger" %% "akka-http-circe"              % "1.31.0",
      "com.twelvemonkeys.imageio" % "imageio-tiff"          % "3.6.4",
      "com.twelvemonkeys.imageio" % "imageio-core"          % "3.6.4",
      "com.twelvemonkeys.contrib" % "contrib"               % "3.6.4",
      "com.sksamuel.scrimage"     % "scrimage-core"         % "4.0.18",
      "com.github.pathikrit"      %% "better-files"         % "3.9.1"

    )
  )

libraryDependencies ++= Seq(
  "org.postgresql"  %  "postgresql" % "42.2.8",
  "io.getquill"     %% "quill-async-postgres" % "3.7.0"
)

libraryDependencies += "ch.megard" %% "akka-http-cors" % "1.0.0"

resolvers += "Sonatype release repository" at "https://oss.sonatype.org/content/repositories/releases/"



scalacOptions in Compile ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint")

// show full stack traces and test case durations
testOptions in Test += Tests.Argument("-oDF")
logBuffered in Test := false
