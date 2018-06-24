name := """ep-db"""

version := "1.0"

lazy val ep_db = (project in file("."))

//javacOptions ++= Seq("-g")

scalaVersion := "2.12.3"

libraryDependencies ++= Seq(
  "org.postgresql" % "postgresql" % "42.0.0",
  "net.arnx"% "jsonic" % "1.3.5",
  "commons-pool" % "commons-pool" % "1.6",
  "commons-io" % "commons-io" % "2.0.1",
  "commons-logging" % "commons-logging" % "1.1.1",
  "org.apache.commons" % "commons-lang3" % "3.0.1",
  "org.slf4j" % "slf4j-api" % "1.7.7",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
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
  "org.jsoup" % "jsoup" % "1.10.3",
  "me.tongfei" % "progressbar" % "0.5.5",
  "org.jbibtex" % "jbibtex" % "1.0.15",
  "gov.nist.math" % "jama" % "1.0.3"
)

test in assembly := {}
assemblyOutputPath in assembly := file("dist/ep-db.jar")

packageOptions in assembly ~= { pos =>
  pos.filterNot { po =>
    po.isInstanceOf[Package.MainClass]
  }
}

assemblyMergeStrategy in assembly := {
    case x if Assembly.isConfigFile(x) =>
      MergeStrategy.concat
    case PathList(ps @ _*) if Assembly.isReadme(ps.last) || Assembly.isLicenseFile(ps.last) =>
      MergeStrategy.rename
    case PathList("META-INF", xs @ _*) =>
      (xs map {_.toLowerCase}) match {
        case ("manifest.mf" :: Nil) | ("index.list" :: Nil) | ("dependencies" :: Nil) =>
          MergeStrategy.discard
        case ps @ (x :: xs) if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") =>
          MergeStrategy.discard
        case "plexus" :: xs =>
          MergeStrategy.discard
        case "services" :: xs =>
          MergeStrategy.filterDistinctLines
        case ("spring.schemas" :: Nil) | ("spring.handlers" :: Nil) =>
          MergeStrategy.filterDistinctLines
        case _ => MergeStrategy.deduplicate
      }
    case _ => MergeStrategy.first
  }
