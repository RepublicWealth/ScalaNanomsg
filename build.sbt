import sbt.Keys._

organization := "org.searchx"

version := "2.0.0"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  "com.typesafe.akka" % "akka-actor_2.11" % "2.3.9"
)