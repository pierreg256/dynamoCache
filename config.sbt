name := "dynCache"

version := "1.0"

scalaVersion := "2.10.2"

resolvers += "spray repo" at "http://repo.spray.io"

libraryDependencies += "com.typesafe.akka" %% "akka-cluster" % "2.2.1"

libraryDependencies += "io.spray" % "spray-can" % "1.2-20130710"

libraryDependencies += "net.databinder" %% "unfiltered-filter" % "0.7.0"

