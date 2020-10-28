name := "EventHub"
coverageEnabled := true

lazy val akkaHttpVersion = "10.2.1"
lazy val akkaVersion    = "2.6.9"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "com.cyapex",
      scalaVersion    := "2.13.3"
    )),
    name := "akka-http-quickstart-scala",
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

      "com.typesafe.akka" %% "akka-http-testkit"        % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion     % Test,
      "org.scalatest"     %% "scalatest"                % "3.0.8"         % Test
    )
  )

val circeVersion = "0.12.3"

libraryDependencies ++= Seq(
  "io.circe"          %% "circe-core"       % circeVersion,
  "io.circe"          %% "circe-generic"    % circeVersion,
  "io.circe"          %% "circe-parser"     % circeVersion,
  "de.heikoseeberger" %% "akka-http-circe"  % "1.31.0",
)

scalacOptions in Compile ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint")

// show full stack traces and test case durations
testOptions in Test += Tests.Argument("-oDF")
logBuffered in Test := false
