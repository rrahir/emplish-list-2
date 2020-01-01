import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt.Def.settings
import sbt._
import sbt.Keys.libraryDependencies

object SharedSettings {

  def apply(): Seq[Def.Setting[_]] = settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % "2.0.0",
      "io.monix" %%% "monix" % "3.1.0",
      "com.softwaremill.sttp.client" %%% "core" % "2.0.0-RC1",
      "org.typelevel" %%% "cats-core" % "2.0.0"
    ) ++ Seq(
      "io.circe" %%% "circe-core",
      "io.circe" %%% "circe-generic",
      "io.circe" %%% "circe-parser"
    ).map(_ % "0.11.1")
  )

  def jvmSettings: Seq[Def.Setting[_]] = settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-json" % "2.7.3"
    )
  )

}
