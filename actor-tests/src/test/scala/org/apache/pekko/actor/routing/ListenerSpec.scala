/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.routing

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Await

import org.apache.pekko
import pekko.actor._
import pekko.routing._
import pekko.testkit._

class ListenerSpec extends PekkoSpec {

  "Listener" must {

    "listen" in {
      val fooLatch = TestLatch(2)
      val barLatch = TestLatch(2)
      val barCount = new AtomicInteger(0)

      val broadcast = system.actorOf(Props(new Actor with Listeners {
        def receive = listenerManagement.orElse {
          case "foo" => gossip("bar")
        }
      }))

      def newListener =
        system.actorOf(Props(new Actor {
          def receive = {
            case "bar" =>
              barCount.incrementAndGet
              barLatch.countDown()
            case "foo" =>
              fooLatch.countDown()
          }
        }))

      val a1 = newListener
      val a2 = newListener
      val a3 = newListener

      broadcast ! Listen(a1)
      broadcast ! Listen(a2)
      broadcast ! Listen(a3)

      broadcast ! Deafen(a3)

      broadcast ! WithListeners(_ ! "foo")
      broadcast ! "foo"

      Await.ready(barLatch, TestLatch.DefaultTimeout)
      barCount.get should ===(2)

      Await.ready(fooLatch, TestLatch.DefaultTimeout)

      for (a <- List(broadcast, a1, a2, a3)) system.stop(a)
    }
  }
}
