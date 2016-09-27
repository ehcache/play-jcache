/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package org.ehcache.integrations.play

import javax.cache.CacheManager

import play.api.cache.{CacheApi, Cached}
import play.api.test._
import play.cache.NamedCacheImpl
import play.inject.Bindings.bind

import scala.util.Try

class JCacheModuleSpec extends PlaySpecification {

  sequential

  val configuration = play.api.Configuration.from(Map(
    "play.modules.disabled" -> Seq("play.api.cache.EhCacheModule"),
    "play.modules.enabled" -> Seq("org.ehcache.integrations.play.JCacheModule", "play.api.inject.BuiltinModule")
  ))

  "play-jcache" should {
    "provide JCacheModule" in {
      val modules = play.api.inject.Modules.locate(play.api.Environment.simple(), configuration)
      (modules.find { module => module.isInstanceOf[JCacheModule] }.isDefined)
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

  "JCacheApi" should {
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
}
