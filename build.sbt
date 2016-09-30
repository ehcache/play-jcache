/*
 * Copyright Terracotta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

lazy val baseSettings = Seq(
  organization := "org.ehcache.integrations.play",
  version := "0.1.0-SNAPSHOT",
  parallelExecution in Test := false
)

lazy val play_jcache = (project in file("."))
  .settings(baseSettings: _*)
  .settings(
    publishLocal := {},
    publish := {}
  ).aggregate(plugin)

lazy val plugin = (project in file("plugin"))
  .settings(baseSettings: _*)
  .settings(
    name := "play2-jcache-play25",
    scalaVersion := "2.11.7",
    resolvers += "Typesafe Maven Repository" at "https://dl.bintray.com/typesafe/maven-releases/",
    libraryDependencies += "com.typesafe.play" %% "play" % play.core.PlayVersion.current % "provided",
    libraryDependencies += "com.typesafe.play" %% "play-cache" % play.core.PlayVersion.current % "provided",

    libraryDependencies += "com.typesafe.play" %% "play-test" % play.core.PlayVersion.current % "provided,test",
    libraryDependencies += "com.typesafe.play" %% "play-specs2" % play.core.PlayVersion.current % "provided,test",

    libraryDependencies += "javax.cache" % "cache-api" % "1.0.0" % "provided",
    libraryDependencies += "org.ehcache" % "ehcache" % "3.1.3" % "provided",

    coverageEnabled := true,

    publishTo <<= version { v: String =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots")
      else                             Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    pomExtra := (
      <url>https://github.com/ehcache/play-jcache</url>
      <licenses>
        <license>
          <name>The Apache Software License, Version 2.0</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:ehcache/play-jcache.git</url>
        <connection>scm:git:git@github.com:ehcache/play-jcache.git</connection>
      </scm>
      <developers>
        <developer>
          <name>Terracotta Engineers</name>
          <email>tc-oss@softwareag.com</email>
          <organization>Terracotta Inc., a wholly-owned subsidiary of Software AG USA, Inc.</organization>
          <organizationUrl>http://ehcache.org</organizationUrl>
        </developer>
      </developers>
      <issueManagement>
        <system>Github</system>
        <url>https://github.com/ehcache/play-jcache/issues</url>
      </issueManagement>
      )
  )
