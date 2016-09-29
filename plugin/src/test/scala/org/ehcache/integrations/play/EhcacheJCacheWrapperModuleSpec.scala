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

import org.ehcache.config.builders.CacheConfigurationBuilder
import org.ehcache.config.builders.CacheManagerBuilder.newCacheManagerBuilder
import org.ehcache.xml.XmlConfiguration
import play.api.test.PlaySpecification

import scala.util.Try

/**
  * EhcacheJCacheWrapperModuleSpec
  */
class EhcacheJCacheWrapperModuleSpec extends PlaySpecification {

  val xmlConfig = new XmlConfiguration(getClass().getResource("/template-test-config.xml"))

  "EhcacheJCacheWrapper" should {
    "enhance configuration always when no xml config specified" in {
      val wrapper = new EhcacheJCacheWrapper(None)
      val name = "name"
      val baseConfig: MutableConfiguration[String, Any] = new MutableConfiguration[String, Any]()
      wrapper.enhanceConfiguration(name, baseConfig) must not beTheSameAs(baseConfig)
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

  "Ehcache" should {
    "be abe to use WrappedValueWithExpiryExpiration" in {
      val manager = newCacheManagerBuilder().build(true)
      val template: CacheConfigurationBuilder[String, Any] = xmlConfig
              .newCacheConfigurationBuilderFromTemplate("template-wrap-expiry", classOf[String], classOf[Any])
      Try { manager.createCache("test", template)} must beASuccessfulTry
    }
  }
}
