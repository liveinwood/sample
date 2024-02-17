val scala3Version = "3.3.1"

// libraryDependencies += "dev.zio" %% "zio" % "2.1-RC1"
// libraryDependencies += "dev.zio" %% "zio-prelude" % "1.0.0-RC1"

lazy val root = project
  .in(file("."))
  .settings(
    name := "Sample",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "0.7.29" % Test,
      "dev.zio" %% "zio" % "2.0.6",
      "dev.zio" %% "zio-prelude" % "1.0.0-RC23",
      "dev.zio" %% "zio-streams" % "2.1-RC1"
    )
  )
