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

import org.ehcache.ValueSupplier
import org.ehcache.expiry.{Expirations, Expiry, Duration => EhDuration}

import scala.concurrent.duration.FiniteDuration

case class WrappedValueWithExpiry(value: AnyRef, expiration: FiniteDuration) {}

class WrappedValueWithExpiryExpiration(delegate: Expiry[_ >: String, _ >: AnyRef]) extends Expiry[String, AnyRef] {
  def this() = {
    this(Expirations.noExpiration())
  }

  def getExpiryForAccess(key: String, value: ValueSupplier[_ <: AnyRef]): EhDuration = delegate.getExpiryForAccess(key, value)

  def getExpiryForCreation(key: String, value: AnyRef): EhDuration = {
    getWrappedExpiryOrDelegate(value, delegate.getExpiryForCreation(key, value))
  }

  def getExpiryForUpdate(key: String, oldValue: ValueSupplier[_ <: AnyRef], newValue: AnyRef): EhDuration = {
    getWrappedExpiryOrDelegate(newValue, delegate.getExpiryForUpdate(key, oldValue, newValue))
  }

  private def getWrappedExpiryOrDelegate(value: AnyRef, delegation: => EhDuration): EhDuration = {
    value match {
      case WrappedValueWithExpiry(_, duration) => EhDuration.of(duration.length, duration.unit)
      case _ => delegation
    }
  }
}
