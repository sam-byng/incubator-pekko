/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor

import java.util.concurrent.atomic.AtomicInteger

import scala.util.control.NoStackTrace

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.apache.pekko
import pekko.testkit.EventFilter
import pekko.testkit.TestKit._

object TestExtension extends ExtensionId[TestExtension] with ExtensionIdProvider {
  def lookup = this
  def createExtension(s: ExtendedActorSystem) = new TestExtension(s)
}

// Dont't place inside ActorSystemSpec object, since it will not be garbage collected and reference to system remains
class TestExtension(val system: ExtendedActorSystem) extends Extension

object FailingTestExtension extends ExtensionId[FailingTestExtension] with ExtensionIdProvider {
  def lookup = this
  def createExtension(s: ExtendedActorSystem) = new FailingTestExtension(s)

  class TestException extends IllegalArgumentException("ERR") with NoStackTrace
}

object InstanceCountingExtension extends ExtensionId[InstanceCountingExtension] with ExtensionIdProvider {
  val createCount = new AtomicInteger(0)
  override def createExtension(system: ExtendedActorSystem): InstanceCountingExtension = {
    new InstanceCountingExtension
  }
  override def lookup: ExtensionId[_ <: Extension] = this
}

class InstanceCountingExtension extends Extension {
  InstanceCountingExtension.createCount.incrementAndGet()
}

// Dont't place inside ActorSystemSpec object, since it will not be garbage collected and reference to system remains
class FailingTestExtension(val system: ExtendedActorSystem) extends Extension {
  // create a named actor as a side effect of initializing this extension, relevant
  // for the 'handle extensions that fail to initialize' test.
  system.actorOf(Props.empty, "uniqueName")

  // Always fail, but 'hide' this from IntelliJ to avoid compilation issues:
  if (42.toString == "42")
    throw new FailingTestExtension.TestException
}

class ExtensionSpec extends AnyWordSpec with Matchers {

  "The ActorSystem extensions support" should {

    "support extensions" in {
      val config = ConfigFactory.parseString("""pekko.extensions = ["org.apache.pekko.actor.TestExtension"]""")
      val system = ActorSystem("extensions", config)

      // TestExtension is configured and should be loaded at startup
      system.hasExtension(TestExtension) should ===(true)
      TestExtension(system).system should ===(system)
      system.extension(TestExtension).system should ===(system)

      shutdownActorSystem(system)
    }

    "handle extensions that fail to initialize" in {
      val system = ActorSystem("extensions")

      // First attempt, an actor is created and after that
      // an exception is thrown:
      intercept[FailingTestExtension.TestException] {
        FailingTestExtension(system)
      }

      // Second attempt, we expect to see the same (cached) exception:
      intercept[FailingTestExtension.TestException] {
        FailingTestExtension(system)
      }

      shutdownActorSystem(system)
    }

    "fail the actor system if an extension listed in pekko.extensions fails to start" in {
      intercept[RuntimeException] {
        val system = ActorSystem(
          "failing",
          ConfigFactory.parseString("""
            pekko.extensions = ["org.apache.pekko.actor.FailingTestExtension"]
          """))

        shutdownActorSystem(system)
      }
    }

    "log an error if an extension listed in pekko.extensions cannot be loaded" in {
      val system = ActorSystem(
        "failing",
        ConfigFactory.parseString("""
          pekko.extensions = ["org.apache.pekko.actor.MissingExtension"]
        """))
      EventFilter.error(
        "While trying to load extension [org.apache.pekko.actor.MissingExtension], skipping.").intercept(())(system)
      shutdownActorSystem(system)
    }

    "allow for auto-loading of library-extensions from reference.conf" in {
      import pekko.util.ccompat.JavaConverters._
      // could be initialized by other tests, but assuming tests are not running in parallel
      val countBefore = InstanceCountingExtension.createCount.get()
      val system = ActorSystem("extensions")
      val listedExtensions = system.settings.config.getStringList("pekko.library-extensions").asScala
      listedExtensions.count(_.contains("InstanceCountingExtension")) should ===(1)

      InstanceCountingExtension.createCount.get() - countBefore should ===(1)

      shutdownActorSystem(system)
    }

    "not create duplicate instances when auto-loading of library-extensions" in {
      import pekko.util.ccompat.JavaConverters._
      // could be initialized by other tests, but assuming tests are not running in parallel
      val countBefore = InstanceCountingExtension.createCount.get()
      val system = ActorSystem(
        "extensions",
        ConfigFactory.parseString(
          """
      pekko.library-extensions = ["org.apache.pekko.actor.InstanceCountingExtension", "org.apache.pekko.actor.InstanceCountingExtension", "org.apache.pekko.actor.InstanceCountingExtension$"]
      """))
      val listedExtensions = system.settings.config.getStringList("pekko.library-extensions").asScala
      listedExtensions.count(_.contains("InstanceCountingExtension")) should ===(3) // testing duplicate names

      InstanceCountingExtension.createCount.get() - countBefore should ===(1)

      shutdownActorSystem(system)
    }

    "fail the actor system if a library-extension fails to start" in {
      intercept[FailingTestExtension.TestException] {
        ActorSystem(
          "failing",
          ConfigFactory.parseString("""
            pekko.library-extensions += "org.apache.pekko.actor.FailingTestExtension"
          """).withFallback(ConfigFactory.load()).resolve())
      }

    }

    "fail the actor system if a library-extension cannot be loaded" in {
      intercept[RuntimeException] {
        ActorSystem(
          "failing",
          ConfigFactory.parseString("""
            pekko.library-extensions += "org.apache.pekko.actor.MissingExtension"
          """).withFallback(ConfigFactory.load()))
      }
    }

  }

}
