name := "job-orchestrator"
version := "1.0.1"
scalaVersion := "2.12.8"
organization := "io.medgelabs"

assemblyJarName in assembly := "app.jar"
mainClass in assembly := Some("edge.labs.orchestrator.Main")
assemblyMergeStrategy in assembly := {
  case manifest if manifest.contains("MANIFEST.MF") => MergeStrategy.discard
  case referenceOverrides if referenceOverrides.contains("reference-overrides.conf") => MergeStrategy.concat
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.+",
  "com.typesafe.akka" %% "akka-slf4j" % "2.5.+",
  "com.typesafe.akka" %% "akka-stream" % "2.5.+",
  "com.typesafe.akka" %% "akka-http" % "10.1.+",
  "com.typesafe.akka" %% "akka-testkit" % "2.5.+" % Test,
  "org.json4s" %% "json4s-native" % "3.5.+",
  "org.scalaj" %% "scalaj-http" % "2.3.+",
  "com.typesafe" % "config" % "1.3.+",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.scalatest" %% "scalatest" % "3.0.+" % Test,
  "com.h2database" % "h2" % "1.4.+" % Test
)

// Test setup
javaOptions in Test += "-Dconfig.resource=test.conf"

// Gatling Load Tests
libraryDependencies += "io.gatling.highcharts" % "gatling-charts-highcharts" % "2.3.0" % Test
libraryDependencies += "io.gatling" % "gatling-test-framework" % "2.3.0" % Test

lazy val GatlingTest = config("gatling") extend Test
lazy val root = (project in file("."))
  .enablePlugins(GatlingPlugin)
  .configs(GatlingTest)
  .settings(inConfig(GatlingTest)(Defaults.testSettings): _*)
  .settings(
    scalaSource in GatlingTest := baseDirectory.value / "perf" / "scenarios",
    resourceDirectory in GatlingTest := baseDirectory.value / "perf" / "input"
  )
