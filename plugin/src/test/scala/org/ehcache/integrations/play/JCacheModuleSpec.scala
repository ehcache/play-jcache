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

package org.ehcache.integrations.play

import javax.cache.CacheManager

import org.specs2.mutable.After
import org.specs2.specification.Scope

import play.api.{Configuration, Environment}
import play.api.cache.{CacheApi, Cached}
import play.api.inject.DefaultApplicationLifecycle
import play.api.test._
import play.cache.NamedCacheImpl
import play.inject.Bindings.bind

import scala.collection.JavaConversions._
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try

class JCacheModuleSpec extends PlaySpecification {

  sequential

  val configuration = play.api.Configuration.from(Map(
    "play.modules.disabled" -> Seq("play.api.cache.EhCacheModule"),
    "play.modules.enabled" -> Seq("play.api.inject.BuiltinModule",
                                  "org.ehcache.integrations.play.JCacheModule",
                                  "org.ehcache.integrations.play.EhcacheJCacheWrapperModule")
  ))

  "play-jcache" should {
    "provide JCacheModule" in {
      val modules = play.api.inject.Modules.locate(play.api.Environment.simple(), configuration)
      (modules.find { module => module.isInstanceOf[JCacheModule] }.isDefined)
    }
  }

  "JCacheComponents" should {
    class CompileTimeDiApplication(config : Configuration = Configuration.empty) extends Scope with After with JCacheComponents {
      override def applicationLifecycle: DefaultApplicationLifecycle = new DefaultApplicationLifecycle

      override def environment: Environment = play.api.Environment.simple()

      override def configuration: Configuration = config

      override def after: Any = Await.result(applicationLifecycle.stop, Duration.Inf)
    }

    "inject a javax.cache.CacheManager" in new CompileTimeDiApplication() {
      jCacheManager must not beNull
    }

    "inject the default cache" in new CompileTimeDiApplication() {
      defaultCacheApi must not beNull;
      jCacheManager.getCacheNames.toSeq must contain("play")
    }

    "inject the default cache with a non-standard name" in new CompileTimeDiApplication(
      Configuration.from(Map("play.cache.defaultCache" -> "foobar"))
    ) {
      defaultCacheApi must not beNull;
      jCacheManager.getCacheNames.toSeq must contain("foobar")
    }

    "retrieve a pre-existing cache" in new CompileTimeDiApplication(
      Configuration.from(Map("play.cache.jcacheConfigResource" -> "custom-config.xml"))
    ) {
      cacheApi("a", false) must not beNull
    }

    "retrieve a on-demand created cache" in new CompileTimeDiApplication() {
      cacheApi("foobar", true) must not beNull
    }
  }

  "JCacheModule" should {

    "provide a binding for javax.cache.CacheManager" in new WithApplication(
      _.configure(configuration)
    ) {
      app.injector.instanceOf[CacheManager] must not beNull
    }

    "provide a default binding for play.api.cache.CacheApi" in new WithApplication(
      _.configure(configuration)
    ) {
      app.injector.instanceOf[CacheApi] must not beNull
    }

    "provide a default binding for play.cache.CacheApi" in new WithApplication(
      _.configure(configuration)
    ) {
      app.injector.instanceOf[play.cache.CacheApi] must not beNull
    }

    "provide bindings for the default cache" in new WithApplication(
        _.configure(configuration).configure("play.cache.defaultCache" -> "foobar")
    ) {
      val injector = app.injector
      val name = new NamedCacheImpl("foobar")
      injector.instanceOf(bind(classOf[javax.cache.Cache[String, Any]]).qualifiedWith(name)) must not beNull;
      injector.instanceOf(bind(classOf[CacheApi]).qualifiedWith(name)) must not beNull;
      injector.instanceOf(bind(classOf[play.cache.CacheApi]).qualifiedWith(name)) must not beNull;
      injector.instanceOf(bind(classOf[Cached]).qualifiedWith(name)) must not beNull;
    }

    "provide bindings for the explicitly bound caches" in new WithApplication(
      _.configure(configuration).configure("play.cache.bindCaches" -> Seq("foobar"))
    ) {
      val injector = app.injector
      val name = new NamedCacheImpl("foobar")
      injector.instanceOf(bind(classOf[javax.cache.Cache[String, Any]]).qualifiedWith(name)) must not beNull;
      injector.instanceOf(bind(classOf[CacheApi]).qualifiedWith(name)) must not beNull;
      injector.instanceOf(bind(classOf[play.cache.CacheApi]).qualifiedWith(name)) must not beNull;
      injector.instanceOf(bind(classOf[Cached]).qualifiedWith(name)) must not beNull;
    }

    "not create bound caches when not asked to" in {
      Try {
        new WithApplication(_.configure(configuration)
          .configure("play.cache.bindCaches" -> Seq("foobar"))
          .configure("play.cache.createBoundCaches" -> "false")) {
          val injector = app.injector
          val name = new NamedCacheImpl("foobar")
          injector.instanceOf(bind(classOf[CacheApi]).qualifiedWith(name)) must not beNull
        }
      } must beAFailedTry
    }
  }

  "CacheManagerProvider" should {

    "use the implementations default config in the absence of any definition" in new WithApplication(
      _.configure(configuration)
    ) {
      app.injector.instanceOf[CacheManager].getCacheNames.toSeq must beEmpty
    }

    "use the user requested configuration" in new WithApplication(
      _.configure(configuration).configure("play.cache.jcacheConfigResource" -> "custom-config.xml")
    ) {
      app.injector.instanceOf[CacheManager].getCacheNames.toSeq must contain("a", "b", "c")
    }
  }

  "JCacheApi.get" should {
    "get items from cache" in new WithApplication(
      _.configure(configuration)
    ) {
      val defaultCache = app.injector.instanceOf[CacheApi]

      defaultCache.set("foo", "bar")
      defaultCache.get[String]("foo") must beSome("bar")

      defaultCache.set("int", 31)
      defaultCache.get[Int]("int") must beSome(31)

      defaultCache.set("long", 31l)
      defaultCache.get[Long]("long") must beSome(31l)

      defaultCache.set("double", 3.14)
      defaultCache.get[Double]("double") must beSome(3.14)

      defaultCache.set("boolean", true)
      defaultCache.get[Boolean]("boolean") must beSome(true)

      defaultCache.set("unit", ())
      defaultCache.get[Unit]("unit") must beSome(())
    }

    "not get items from cache with the wrong type" in new WithApplication(
      _.configure(configuration)
    ) {
      val defaultCache = app.injector.instanceOf[CacheApi]
      defaultCache.set("foo", "bar")
      defaultCache.set("int", 31)
      defaultCache.set("long", 31l)
      defaultCache.set("double", 3.14)
      defaultCache.set("boolean", true)
      defaultCache.set("unit", ())

      defaultCache.get[Int]("foo") must beNone
      defaultCache.get[Long]("foo") must beNone
      defaultCache.get[Double]("foo") must beNone
      defaultCache.get[Boolean]("foo") must beNone
      defaultCache.get[String]("int") must beNone
      defaultCache.get[Long]("int") must beNone
      defaultCache.get[Double]("int") must beNone
      defaultCache.get[Boolean]("int") must beNone
      defaultCache.get[Unit]("foo") must beNone
      defaultCache.get[Int]("unit") must beNone
    }

    "get items from the cache without giving the type" in new WithApplication(
      _.configure(configuration)
    ) {
      val defaultCache = app.injector.instanceOf[CacheApi]
      defaultCache.set("foo", "bar")
      defaultCache.get("foo") must beSome("bar")
      defaultCache.get[Any]("foo") must beSome("bar")

      defaultCache.set("baz", false)
      defaultCache.get("baz") must beSome(false)
      defaultCache.get[Any]("baz") must beSome(false)

      defaultCache.set("int", 31)
      defaultCache.get("int") must beSome(31)
      defaultCache.get[Any]("int") must beSome(31)

      defaultCache.set("unit", ())
      defaultCache.get("unit") must beSome(())
      defaultCache.get[Any]("unit") must beSome(())
    }
  }

  "JCacheApi.getOrElse" should {
    "get items from cache" in new WithApplication(
      _.configure(configuration)
    ) {
      val defaultCache = app.injector.instanceOf[CacheApi]

      defaultCache.set("foo", "bar")
      defaultCache.getOrElse("foo")("baz") must beEqualTo("bar")

      defaultCache.set("int", 31)
      defaultCache.getOrElse("int")(32) must beEqualTo(31)

      defaultCache.set("long", 31l)
      defaultCache.getOrElse("long")(32L) must beEqualTo(31L)

      defaultCache.set("double", 3.14)
      defaultCache.getOrElse("double")(2.71) must beEqualTo(3.14)

      defaultCache.set("boolean", true)
      defaultCache.getOrElse("boolean")(false) must beEqualTo(true)
    }

    "get items from the cache without giving the type" in new WithApplication(
      _.configure(configuration)
    ) {
      val defaultCache = app.injector.instanceOf[CacheApi]
      defaultCache.set("foo", "bar")
      defaultCache.getOrElse[Any]("foo")("baz") must beEqualTo("bar")

      defaultCache.set("baz", false)
      defaultCache.getOrElse[Any]("baz")(true) must beEqualTo(false)

      defaultCache.set("int", 31)
      defaultCache.getOrElse[Any]("int")(32) must beEqualTo(31)

      defaultCache.set("unit", ())
      defaultCache.getOrElse[Any]("unit")("blah") must beEqualTo(())
    }

    "replace items of the wrong type" in new WithApplication(
      _.configure(configuration)
    ) {
      val defaultCache = app.injector.instanceOf[CacheApi]
      defaultCache.set("foo", "bar")

      defaultCache.getOrElse("foo")(32) must beEqualTo(32)
      defaultCache.get("foo") must beSome(32)

      defaultCache.getOrElse("foo")(32L) must beEqualTo(32L)
      defaultCache.get("foo") must beSome(32L)

      defaultCache.getOrElse("foo")(2.71) must beEqualTo(2.71)
      defaultCache.get("foo") must beSome(2.71)

      defaultCache.getOrElse("foo")(true) must beEqualTo(true)
      defaultCache.get("foo") must beSome(true)

      defaultCache.getOrElse("foo")(()) must beEqualTo(())
      defaultCache.get("foo") must beSome(())
    }

    "install items when nothing is present" in new WithApplication(
      _.configure(configuration)
    ) {
      val defaultCache = app.injector.instanceOf[CacheApi]

      defaultCache.getOrElse("int")(32) must beEqualTo(32)
      defaultCache.get("int") must beSome(32)

      defaultCache.getOrElse("long")(32L) must beEqualTo(32L)
      defaultCache.get("long") must beSome(32L)

      defaultCache.getOrElse("double")(2.71) must beEqualTo(2.71)
      defaultCache.get("double") must beSome(2.71)

      defaultCache.getOrElse("boolean")(true) must beEqualTo(true)
      defaultCache.get("boolean") must beSome(true)

      defaultCache.getOrElse("string")("foo") must beEqualTo("foo")
      defaultCache.get("string") must beSome("foo")

      defaultCache.getOrElse("unit")(()) must beEqualTo(())
      defaultCache.get("unit") must beSome(())
    }
  }

  "JCacheApi.set" should {
    "install a cache mapping" in new WithApplication(
      _.configure(configuration)
    ) {
      val defaultCache = app.injector.instanceOf[CacheApi]

      defaultCache.set("foo", "bar")
      defaultCache.get("foo") must beSome("bar")
    }
  }

  "JCacheApi.remove" should {
    "succeed silently on an absent cache mapping" in new WithApplication(
      _.configure(configuration)
    ) {
      val defaultCache = app.injector.instanceOf[CacheApi]

      Try { defaultCache.remove("foo") } must beASuccessfulTry
    }

    "succeed in removing a present cache mapping" in new WithApplication(
      _.configure(configuration)
    ) {
      val defaultCache = app.injector.instanceOf[CacheApi]
      defaultCache.set("foo", "bar")

      defaultCache.remove("foo")
      defaultCache.get("foo") must beNone
    }
  }
}
