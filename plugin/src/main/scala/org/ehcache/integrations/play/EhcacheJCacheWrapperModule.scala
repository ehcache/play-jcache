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

import javax.cache.configuration.{Configuration => JCacheConfiguration}
import javax.inject.{Inject, Provider, Singleton}

import org.ehcache.config.builders.CacheConfigurationBuilder
import org.ehcache.config.builders.CacheConfigurationBuilder.newCacheConfigurationBuilder
import org.ehcache.config.builders.ResourcePoolsBuilder.heap
import org.ehcache.jsr107.Eh107Configuration.fromEhcacheCacheConfiguration
import org.ehcache.xml.XmlConfiguration
import play.api.inject.Module
import play.api.{Configuration, Environment}

import scala.concurrent.duration.Duration

/**
  * EhcacheWrapperModule
  */
@Singleton
class EhcacheJCacheWrapperModule extends Module {
  def bindings(environment: Environment, configuration: Configuration) = {
    Seq(
      bind[JCacheWrapper].toProvider[EhcacheJCacheWrapperProvider]
    )
  }
}

@Singleton
class EhcacheJCacheWrapperProvider @Inject()(env: Environment, config: Configuration) extends Provider[JCacheWrapper] {
  lazy val get: JCacheWrapper = {
    val resourceName = config.getString("play.cache.jcacheConfigResource")
    val xmlConfig = resourceName.map(r => new XmlConfiguration(env.resource(r).get, env.classLoader))
    new EhcacheJCacheWrapper(xmlConfig)
  }
}

/**
  *
  */
class EhcacheValueWrapper extends ValueWrapper {
  def wrapValue(value: Any, expiration: Duration): Any = {
    WrappedValueWithExpiry(value, expiration)
  }

  def unwrapValue(value: Any): Any = {
    value match {
      case v: WrappedValueWithExpiry => v.value
      case _ => value
    }
  }
}

class NoOpValueWrapper extends ValueWrapper {
  def wrapValue(value: Any, expiration: Duration): Any = {
    value
  }

  def unwrapValue(value: Any): Any = {
    value
  }
}

/**
  * This wrapper implementation allows to leverage Ehcache 3 features to enable back the per mapping TTL.
  *
  * @param xmlConfig the source XML configuration used by the JCache module.
  */
class EhcacheJCacheWrapper(xmlConfig: Option[XmlConfiguration]) extends JCacheWrapper {

  var enhanced = Set.empty[String]

  def valueWrapper(name: String) = {
    enhanced contains name match {
      case true => new EhcacheValueWrapper
      case false => new NoOpValueWrapper
    }
  }

  def enhanceConfiguration(name: String, baseConfig: JCacheConfiguration[String, Any]): JCacheConfiguration[String, Any] = {
    xmlConfig match {
      case Some(config) =>
        val template: CacheConfigurationBuilder[String, Any] = config
                .newCacheConfigurationBuilderFromTemplate(name, classOf[String], classOf[Any])
        template match {
          case null => generateMinimalConfiguration(name)
          case t if t.hasConfiguredExpiry => fromEhcacheCacheConfiguration(t)
          case t =>
            enhanced = enhanced + name
            fromEhcacheCacheConfiguration(t.withExpiry(new WrappedValueWithExpiryExpiration))
        }
      case None => generateMinimalConfiguration(name)
    }
  }

  def generateMinimalConfiguration(name: String): JCacheConfiguration[String, Any] = {
    enhanced = enhanced + name
    fromEhcacheCacheConfiguration(newCacheConfigurationBuilder(classOf[String], classOf[Any], heap(Int.MaxValue))
            .withExpiry(new WrappedValueWithExpiryExpiration))
  }
}
