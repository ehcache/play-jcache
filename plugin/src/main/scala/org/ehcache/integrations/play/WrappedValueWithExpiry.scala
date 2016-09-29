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

import org.ehcache.ValueSupplier
import org.ehcache.expiry.{Expiry, Duration => EhDuration}

import scala.concurrent.duration.Duration

/**
  * WrappedValueWithExpiry
  */
case class WrappedValueWithExpiry(value: Any, expiration: Duration)

/**
  * WrappedValueWithExpiryExpiration
  */
class WrappedValueWithExpiryExpiration extends Expiry[String, Any] {
  def getExpiryForAccess(key: String, value: ValueSupplier[_]): EhDuration = null

  def getExpiryForCreation(key: String, value: Any): EhDuration = {
   value match {
     case (wrapped: WrappedValueWithExpiry) =>
       wrapped.expiration match {
         case Duration.Inf => EhDuration.INFINITE
         case _ => EhDuration.of(wrapped.expiration.toMillis, TimeUnit.MILLISECONDS)
       }
     case _ => EhDuration.INFINITE
   }
  }

  def getExpiryForUpdate(key: String, oldValue: ValueSupplier[_], newValue: Any): EhDuration = {
    getExpiryForCreation(key, newValue)
  }
}
