/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.discovery.config

import scala.collection.immutable
import scala.concurrent.duration._

import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.discovery.Discovery
import org.apache.pekko.discovery.ServiceDiscovery.ResolvedTarget
import org.apache.pekko.testkit.TestKit

object ConfigServiceDiscoverySpec {

  val config: Config = ConfigFactory.parseString("""
pekko {
  loglevel = DEBUG
  discovery {
    method = config
    config {
      services = {
        service1 = {
          endpoints = [
            {
              host = "cat"
              port = 1233
            },
            {
              host = "dog"
            }
          ]
        },
        "service2.domain.com" = {
          endpoints = []
        }
      }
    }
  }
}
    """)

}

class ConfigServiceDiscoverySpec
    extends TestKit(ActorSystem("ConfigDiscoverySpec", ConfigServiceDiscoverySpec.config))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures {

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val discovery = Discovery(system).discovery

  "Config discovery" must {
    "load from config" in {
      val result = discovery.lookup("service1", 100.millis).futureValue
      result.serviceName shouldEqual "service1"
      result.addresses shouldEqual immutable.Seq(
        ResolvedTarget(host = "cat", port = Some(1233), address = None),
        ResolvedTarget(host = "dog", port = None, address = None))
    }
    "return no resolved targets if no endpoints" in {
      val result = discovery.lookup("service2.domain.com", 100.millis).futureValue
      result.serviceName shouldEqual "service2.domain.com"
      result.addresses shouldEqual immutable.Seq.empty
    }
    "return no resolved targets if not in config" in {
      val result = discovery.lookup("dontexist", 100.millis).futureValue
      result.serviceName shouldEqual "dontexist"
      result.addresses shouldEqual immutable.Seq.empty
    }
  }
}
