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

import javax.inject.{Provider, Singleton}

import play.api.inject.Module
import play.api.{Configuration, Environment}

import scala.concurrent.duration.Duration


trait EhcacheJCacheWrapperComponents {
  lazy val ehcacheJCacheWrapper: JCacheWrapper = new EhcacheJCacheWrapperProvider().get
}

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
class EhcacheJCacheWrapperProvider extends Provider[JCacheWrapper] {
  lazy val get: JCacheWrapper = {
    new EhcacheJCacheWrapper()
  }
}

class EhcacheJCacheWrapper extends JCacheWrapper {
  override def wrapValue(value: Any, expiration: Duration): Any = ???
}
