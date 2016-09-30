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
import org.mockito.Matchers
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.concurrent.duration.Duration
import scala.util.Try

/**
  * WrappedValueWithExpirySpec
  */
class WrappedValueWithExpirySpec extends Specification with Mockito{

  "WrappedValueWithExpiry" should {
    "fail to build if Duration.Inf is used" in {
      Try { WrappedValueWithExpiry("value", Duration.Inf) } must beFailedTry
    }
  }

  "WrappedValueWithExpiryExpiration" should {
    val mockedExpiry = mock[Expiry[String, AnyRef]]
    val expiry = new WrappedValueWithExpiryExpiration(mockedExpiry)
    "delegates in getExpiryForAccess" in {
      expiry.getExpiryForAccess("key", mock[ValueSupplier[AnyRef]])
      there was one(mockedExpiry).getExpiryForAccess(Matchers.eq("key"), any[ValueSupplier[AnyRef]])
    }
    "delegates when value is not wrapped in getExpiryForCreation" in {
      expiry.getExpiryForCreation("key", "value")
      there was one(mockedExpiry).getExpiryForCreation("key", "value")
    }
    "return configured expiration duration when value is wrapped in getExpiryForCreation" in {
      val duration = Duration.create(10, TimeUnit.SECONDS)
      val newValue = WrappedValueWithExpiry("value", duration)
      expiry.getExpiryForCreation("key", newValue) must be equalTo EhDuration.of(10, TimeUnit.SECONDS)
    }
    "delegates when value is not wrapped in getExpiryForUpdate" in {
      expiry.getExpiryForUpdate("key", mock[ValueSupplier[AnyRef]], "value")
      there was one(mockedExpiry).getExpiryForUpdate(Matchers.eq("key"), any[ValueSupplier[AnyRef]], Matchers.eq("value"))
    }
    "return configured expiration duration when value is wrapped in getExpiryForUpdate" in {
      val duration = Duration.create(10, TimeUnit.SECONDS)
      val newValue = WrappedValueWithExpiry("value", duration)
      expiry.getExpiryForUpdate("key", mock[ValueSupplier[AnyRef]], newValue) must be equalTo EhDuration.of(10, TimeUnit.SECONDS)
    }
  }
}
