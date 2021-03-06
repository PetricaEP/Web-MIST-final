name := """ep-project"""

version := "1.0"

EclipseKeys.skipParents in ThisBuild := false

lazy val root = (project in file("."))
	.enablePlugins(PlayJava)
	.enablePlugins(SbtWeb)
	.aggregate(ep_db)
	.dependsOn(ep_db)


lazy val ep_db = (project in file("ep-db"))

//javacOptions ++= Seq("-g")

scalaVersion := "2.12.3"

libraryDependencies ++= Seq(
  guice,
  javaJdbc,
  cache,
  javaWs,
  "org.postgresql" % "postgresql" % "42.0.0",
  "org.apache.commons" % "commons-math3" % "3.6.1",
  "org.webjars" % "jquery" % "3.2.1",
  "org.webjars" % "bootstrap" % "3.3.7",
  "org.webjars" % "bootstrap-slider" % "5.3.1",
  "org.webjars" % "d3js" % "4.2.1",
  "org.webjars.npm" % "d3-cloud" % "1.2.4",
  "org.webjars.bower" % "canvg" % "1.3.0",
  "ca.umontreal.iro.simul" % "ssj" % "3.2.1",
  "org.julienrf" %% "play-jsmessages" % "3.0.0"
)
