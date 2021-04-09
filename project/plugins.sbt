addSbtPlugin("ch.epfl.scala"          % "sbt-bloop"        % "1.4.5")
addSbtPlugin("org.scalameta"          % "sbt-scalafmt"     % "2.4.2")
addSbtPlugin("com.github.daniel-shuy" % "sbt-release-mdoc" % "1.0.1")
//addSbtPlugin("com.geirsson"              % "sbt-ci-release"   % "1.5.4")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.17")

val mdocVersion = "2.2.13"
addSbtPlugin("org.scalameta"            % "sbt-mdoc" % mdocVersion)
libraryDependencies += "org.scalameta" %% "mdoc"     % mdocVersion
