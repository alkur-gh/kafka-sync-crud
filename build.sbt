scalaVersion := "2.13.8"

val AkkaVersion = "2.6.19"
val AkkaHttpVersion = "10.2.9"
val AkkaKafkaVersion = "3.0.0"
val Json4sVersion = "4.0.5"
val AkkaElasticsearchVersion = "3.0.4"
val AkkaHttpJsonVersion = "1.39.2"
val Elastic4sVersion = "7.17.2"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-stream-kafka" % AkkaKafkaVersion,
  "org.json4s" %% "json4s-jackson" % Json4sVersion,
  "org.json4s" %% "json4s-ext" % Json4sVersion,
  "de.heikoseeberger" %% "akka-http-json4s" % AkkaHttpJsonVersion,
  "com.lightbend.akka" %% "akka-stream-alpakka-elasticsearch" % AkkaElasticsearchVersion,
  "com.sksamuel.elastic4s" %% "elastic4s-client-akka" % Elastic4sVersion,
  "com.sksamuel.elastic4s" %% "elastic4s-json-json4s" % Elastic4sVersion,
  "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.11",
  "org.codehaus.janino" % "janino" % "3.1.7"
)