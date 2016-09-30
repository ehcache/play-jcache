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

import javax.cache.{Cache, CacheManager, Caching}
import javax.cache.configuration.{MutableConfiguration, Configuration => JCacheConfiguration}
import javax.cache.processor.{EntryProcessor, MutableEntry}
import javax.inject.{Inject, Provider, Singleton}

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

  def jCacheWrapper: JCacheWrapper

  lazy val jCacheManager: CacheManager = new CacheManagerProvider(environment, configuration, applicationLifecycle).get

  /**
    * Use this to create with the given name.
    */
  def cacheApi(name: String, create: Boolean = true): CacheApi = {
    new JCacheApi(NamedJCacheProvider.getNamedCache(name, jCacheManager, create, jCacheWrapper).get, jCacheWrapper.valueWrapper(name))
  }

  lazy val defaultCacheApi: CacheApi = cacheApi(configuration.getString("play.cache.defaultCache").getOrElse("play"))
}

/**
  *
  */
trait ValueWrapper {
  def wrapValue(value: Any, expiration: Duration): Any

  def unwrapValue(value: Any): Any
}

/**
  * This trait defines an extension point for JCache integration.
  */
trait JCacheWrapper {

  def valueWrapper(name: String): ValueWrapper

  def enhanceConfiguration(name: String, baseConfig: JCacheConfiguration[String, Any]): JCacheConfiguration[String, Any]
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
    val resourceName = config.getString("play.cache.jcacheConfigResource")
    val configUri = resourceName.map(env.resource(_).get).map(_.toURI).getOrElse(provider.getDefaultURI)
    val manager = provider.getCacheManager(configUri, env.classLoader)
    lifecycle.addStopHook(() => Future.successful(manager.close()))
    manager
  }
}

private[play] class NamedJCacheProvider(name: String, create: Boolean) extends Provider[Cache[String, Any]] {
  @Inject private var manager: CacheManager = _
  @Inject private var jCacheWrapper: JCacheWrapper = _
  lazy val get: Cache[String, Any] = NamedJCacheProvider.getNamedCache(name, manager, create, jCacheWrapper).get
}

private[play] object NamedJCacheProvider {
  def getNamedCache(name: String, manager: CacheManager, create: Boolean, jCacheWrapper: JCacheWrapper): Option[Cache[String, Any]] = {
    Option(manager.getCache(name, classOf[String], classOf[Any]))
      .orElse(if (create) Option {
        val config = jCacheWrapper.enhanceConfiguration(name, new MutableConfiguration[String, Any]().setTypes(classOf[String], classOf[Any]))
        manager.createCache(name, config)
      } else Option.empty)
  }
}

private[play] class NamedCacheApiProvider(key: BindingKey[Cache[String, Any]]) extends Provider[CacheApi] {
  @Inject private var injector: Injector = _
  @Inject private var jCacheWrapper: JCacheWrapper = _
  lazy val get: CacheApi = {
    val cache: Cache[String, Any] = injector.instanceOf(key)
    new JCacheApi(cache, jCacheWrapper.valueWrapper(cache.getName))
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
class JCacheApi @Inject()(cache: Cache[String, Any], valueWrapper: ValueWrapper) extends CacheApi {

  def set(key: String, value: Any, expiration: Duration) = {
    cache.put(key, valueWrapper.wrapValue(value, expiration))
  }

  def get[T: ClassTag](key: String): Option[T] = {
    filter(cache.get(key))
  }

  def getOrElse[A: ClassTag](key: String, expiration: Duration)(orElse: => A): A = {
    cache.invoke(key, new EntryProcessor[String, Any, A] {
      def process(entry: MutableEntry[String, Any], arguments: AnyRef*): A = {
        if (entry.exists()) {
          filter(entry.getValue).getOrElse({
            entry.setValue(valueWrapper.wrapValue(orElse, expiration))
            orElse
          })
        } else {
          entry.setValue(valueWrapper.wrapValue(orElse, expiration))
          orElse
        }
      }
    })
  }

  def remove(key: String) = {
    cache.remove(key)
  }

  private def filter[T](value: Any)(implicit ct: ClassTag[T]): Option[T] = {
    Option(value).map(valueWrapper.unwrapValue).filter { v =>
      Primitives.wrap(ct.runtimeClass).isInstance(v) ||
        ct == ClassTag.Nothing || (ct == ClassTag.Unit && v == ((): Unit))
    }.asInstanceOf[Option[T]]
  }
}
