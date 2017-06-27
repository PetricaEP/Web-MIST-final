name := """ep-db"""

version := "1.0"

lazy val ep_db = (project in file("."))

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
//  "colt" % "colt" % "1.2.0",
  "org.postgresql" % "postgresql" % "42.0.0",
  "net.arnx"% "jsonic" % "1.3.5",
  "commons-pool" % "commons-pool" % "1.6",
  "commons-io" % "commons-io" % "2.0.1",
  "commons-logging" % "commons-logging" % "1.1.1",
  "org.apache.commons" % "commons-lang3" % "3.0.1",
  "org.slf4j" % "slf4j-api" % "1.7.7",
  "org.slf4j" % "slf4j-log4j12" % "1.7.7",
  "log4j" % "log4j" % "1.2.17",
  "xerces" % "xercesImpl" % "2.11.0",
  "directory-naming" % "naming-java" % "0.8",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.8.7",
  "com.fasterxml.jackson.core" % "jackson-annotations" % "2.8.7",
  "org.apache.commons" % "commons-math3" % "3.6.1",
  "org.jblas" % "jblas" % "1.2.4",
  "xom" % "xom" % "1.2.5",
  "net.sf.jung" % "jung-api" % "2.1.1",
  "net.sf.jung" % "jung-algorithms" % "2.1.1",
  "net.sf.jung" % "jung-graph-impl" % "2.1.1",
  "org.jfree" % "jfreechart" % "1.0.19",
  "net.sourceforge.parallelcolt" % "parallelcolt" % "0.10.1",
  "org.jsoup" % "jsoup" % "1.10.3"
)
