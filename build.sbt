name := "docker-core"

version := "0.0.003"

scalaVersion := "2.13.7"

crossScalaVersions := Seq("2.11.12", "2.12.15", "2.13.7")

libraryDependencies ++= Seq(
  "com.github.docker-java" % "docker-java" % "3.2.12",
  "com.github.docker-java" % "docker-java-transport-httpclient5" % "3.2.12",
  "org.scalatest" %% "scalatest" % "3.2.10" % Test
)

val projectSourceDirs = List("core", "impl-docker-java", "default-api")
Compile / unmanagedSourceDirectories ++= projectSourceDirs.map(dir => (Compile / baseDirectory).value / dir)

organization := "com.logicovercode"

val techLead = Developer(
  "techLead",
  "techLead",
  "techlead@logicovercode.com",
  url("https://github.com/logicovercode")
)
developers := List(techLead)

homepage := Some(
  url("https://github.com/logicovercode/DockerCore")
)
scmInfo := Some(
  ScmInfo(
    url("https://github.com/logicovercode/DockerCore"),
    "git@github.com:logicovercode/DockerCore.git"
  )
)

licenses += ("MIT", url("https://opensource.org/licenses/MIT"))

//publishing related settings

crossPaths := false
publishMavenStyle := true
publishTo := Some(Opts.resolver.sonatypeStaging)

//below is not yet working as expected (exploring ...)
publishConfiguration := publishConfiguration.value.withOverwrite(true)
publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true)

val dockerCoreProject = project in file(".")
