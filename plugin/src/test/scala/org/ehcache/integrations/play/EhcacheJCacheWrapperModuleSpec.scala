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

import java.util.concurrent.TimeUnit
import javax.cache.configuration.MutableConfiguration

import org.ehcache.config.builders.CacheConfigurationBuilder
import org.ehcache.config.builders.CacheManagerBuilder.newCacheManagerBuilder
import org.ehcache.xml.XmlConfiguration
import play.api.test.PlaySpecification

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Try

/**
  * EhcacheJCacheWrapperModuleSpec
  */
class EhcacheJCacheWrapperModuleSpec extends PlaySpecification {

  val xmlConfig = new XmlConfiguration(getClass.getResource("/template-test-config.xml"))

  "EhcacheJCacheWrapper" should {
    "enhance configuration always when no xml config specified" in {
      val wrapper = new EhcacheJCacheWrapper(None)
      val name = "name"
      val baseConfig: MutableConfiguration[String, Any] = new MutableConfiguration[String, Any]()

      wrapper.enhanceConfiguration(name, baseConfig) must not beTheSameAs baseConfig
      wrapper.valueWrapper(name) must beAnInstanceOf[EhcacheValueWrapper]
    }

    "enhance configuration when matching template has no expiry configured" in {
      val wrapper = new EhcacheJCacheWrapper(Some(xmlConfig))
      val name = "template-no-expiry"
      wrapper.enhanceConfiguration(name, new MutableConfiguration[String, Any]())

      wrapper.valueWrapper(name) must beAnInstanceOf[EhcacheValueWrapper]
    }

    "not enhance configuration when matching template has expiry configured" in {
      val wrapper = new EhcacheJCacheWrapper(Some(xmlConfig))
      val name = "template-expiry"
      wrapper.enhanceConfiguration(name, new MutableConfiguration[String, Any]())

      wrapper.valueWrapper(name) must beAnInstanceOf[NoOpValueWrapper]
    }
  }

  "EhcacheValueWrapper" should {
    val wrapper = new EhcacheValueWrapper
    val testValue = "value"
    val duration: FiniteDuration = Duration.create(100, TimeUnit.SECONDS)
    "wrap value when expiration is not infinite" in {
      wrapper.wrapValue(testValue, duration) must beEqualTo(WrappedValueWithExpiry(testValue, duration))

    }
    "not wrap value when expiration is infinite" in {
      wrapper.wrapValue(testValue, Duration.Inf) must be equalTo testValue
    }
    "unwrap value when it is WrappedValueWithExpiry" in {
      wrapper.unwrapValue(WrappedValueWithExpiry(testValue, duration)) must be equalTo testValue
    }
    "leave value untouched when it is not WrappedValueWithExpiry" in {
      wrapper.unwrapValue(testValue) must be equalTo testValue
    }
  }

  "NoOpValueWrapper" should {
    val wrapper = new NoOpValueWrapper
    val testValue = "value"
    "not wrap value when expiration is infinite" in {
      wrapper.wrapValue(testValue, Duration.Inf) must be equalTo testValue
    }
    "not wrap value when expiration is not infinite" in {
      wrapper.wrapValue(testValue, Duration.create(100, TimeUnit.SECONDS)) must be equalTo testValue
    }
    "not unwrap value when it is WrappedValueWithExpiry" in {
      val value = WrappedValueWithExpiry(testValue, Duration.create(10, TimeUnit.SECONDS))
      wrapper.unwrapValue(value) must be equalTo value
    }
  }

  "Ehcache" should {
    "be abe to use WrappedValueWithExpiryExpiration" in {
      val manager = newCacheManagerBuilder().build(true)
      val template: CacheConfigurationBuilder[String, Any] = xmlConfig
              .newCacheConfigurationBuilderFromTemplate("template-wrap-expiry", classOf[String], classOf[Any])

      Try { manager.createCache("test", template)} must beASuccessfulTry
    }
  }
}
