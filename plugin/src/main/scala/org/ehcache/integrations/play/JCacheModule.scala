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

import javax.cache.configuration.MutableConfiguration
import javax.inject.{Inject, Provider, Singleton}
import javax.cache.{Cache, CacheException, CacheManager, Caching}

import com.google.common.primitives.Primitives
import play.api.cache.{CacheApi, Cached, NamedCache}
import play.api.{Configuration, Environment}
import play.api.inject._
import play.cache.{NamedCacheImpl, CacheApi => JavaCacheApi, DefaultCacheApi => DefaultJavaCacheApi}

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

/**
  * JCache components for compile time injection
  */
trait JCacheComponents {
  def environment: Environment

  def configuration: Configuration

  def applicationLifecycle: ApplicationLifecycle

  lazy val jCacheManager: CacheManager = new CacheManagerProvider(environment, configuration, applicationLifecycle).get

  /**
    * Use this to create with the given name.
    */
  def cacheApi(name: String, create: Boolean = true): CacheApi = {
    new JCacheApi(NamedJCacheProvider.getNamedCache(name, jCacheManager, create).get)
  }

  lazy val defaultCacheApi: CacheApi = cacheApi("play")
}

/**
  * JCacheModule
  */
@Singleton
class JCacheModule extends Module {

  import scala.collection.JavaConversions._

  def bindings(environment: Environment, configuration: Configuration) = {
    val defaultCacheName = configuration.getString("play.cache.defaultCache").get
    val bindCaches = configuration.getStringList("play.cache.bindCaches").get
    val createBoundCaches = configuration.getBoolean("play.cache.createBoundCaches").get

    // Creates a named cache qualifier
    def named(name: String): NamedCache = {
      new NamedCacheImpl(name)
    }

    // bind a cache with the given name
    def bindCache(name: String) = {
      val namedCache = named(name)
      val jcacheKey = bind[Cache[String, Any]].qualifiedWith(namedCache)
      val cacheApiKey = bind[CacheApi].qualifiedWith(namedCache)
      Seq(
        jcacheKey.to(new NamedJCacheProvider(name, createBoundCaches)),
        cacheApiKey.to(new NamedCacheApiProvider(jcacheKey)),
        bind[JavaCacheApi].qualifiedWith(namedCache).to(new NamedJavaCacheApiProvider(cacheApiKey)),
        bind[Cached].qualifiedWith(namedCache).to(new NamedCachedProvider(cacheApiKey))
      )
    }

    Seq(
      bind[CacheManager].toProvider[CacheManagerProvider],
      // alias the default cache to the unqualified implementation
      bind[CacheApi].to(bind[CacheApi].qualifiedWith(named(defaultCacheName))),
      bind[JavaCacheApi].to[DefaultJavaCacheApi]
    ) ++ bindCache(defaultCacheName) ++ bindCaches.flatMap(bindCache)
  }
}

@Singleton
class CacheManagerProvider @Inject()(env: Environment, config: Configuration, lifecycle: ApplicationLifecycle) extends Provider[CacheManager] {
  lazy val get: CacheManager = {
    val provider = Caching.getCachingProvider
    val resourceName = config.getString("play.cache.jcacheConfig")
    val configUri = resourceName.map(env.resource(_).get).map(_.toURI).getOrElse(provider.getDefaultURI)
    val manager = provider.getCacheManager(configUri, env.classLoader)
    lifecycle.addStopHook(() => Future.successful(manager.close()))
    manager
  }
}

private[play] class NamedJCacheProvider(name: String, create: Boolean) extends Provider[Cache[String, Any]] {
  @Inject private var manager: CacheManager = _
  lazy val get: Cache[String, Any] = NamedJCacheProvider.getNamedCache(name, manager, create).get
}

private[play] object NamedJCacheProvider {
  def getNamedCache(name: String, manager: CacheManager, create: Boolean): Option[Cache[String, Any]] = {
    Option(manager.getCache(name, classOf[String], classOf[Any]))
      .orElse(if (create) Option(manager.createCache(name, new MutableConfiguration[String, Any]().setTypes(classOf[String], classOf[Any]))) else Option.empty)
  }
}

private[play] class NamedCacheApiProvider(key: BindingKey[Cache[String, Any]]) extends Provider[CacheApi] {
  @Inject private var injector: Injector = _
  lazy val get: CacheApi = {
    new JCacheApi(injector.instanceOf(key))
  }
}

private[play] class NamedJavaCacheApiProvider(key: BindingKey[CacheApi]) extends Provider[JavaCacheApi] {
  @Inject private var injector: Injector = _
  lazy val get: JavaCacheApi = {
    new DefaultJavaCacheApi(injector.instanceOf(key))
  }
}

private[play] class NamedCachedProvider(key: BindingKey[CacheApi]) extends Provider[Cached] {
  @Inject private var injector: Injector = _
  lazy val get: Cached = {
    new Cached(injector.instanceOf(key))
  }
}

@Singleton
class JCacheApi @Inject()(cache: Cache[String, Any]) extends CacheApi {

  def set(key: String, value: Any, expiration: Duration) = {
    cache.put(key, value)
  }

  def get[T: ClassTag](key: String): Option[T] = {
    return filter(cache.get(key))
  }

  def getOrElse[A: ClassTag](key: String, expiration: Duration)(orElse: => A): A = {
    val current = Option(cache.get(key))
    if (current.isEmpty) {
      if (cache.putIfAbsent(key, orElse)) {
        orElse
      }
    } else {
      val filtered = filter(current.get)
      if (filtered.isEmpty) {
        //this isn't right!
        if (cache.replace(key, current.get, orElse)) {
          orElse
        }
      } else {
        filtered.get
      }
    }
    getOrElse(key, expiration)(orElse)
  }

  def remove(key: String) = {
    cache.remove(key)
  }

  private def filter[T](value: Any)(implicit ct: ClassTag[T]): Option[T] = {
    Option(value).filter { v =>
      Primitives.wrap(ct.runtimeClass).isInstance(v) ||
        ct == ClassTag.Nothing || (ct == ClassTag.Unit && v == ((): Unit))
    }.asInstanceOf[Option[T]]
  }
}
