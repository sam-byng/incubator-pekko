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

package org.apache.pekko.stream.scaladsl

import java.util.concurrent.TimeoutException

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.TestSubscriber

class FlowInitialDelaySpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
  """) {

  "Flow initialDelay" must {

    "work with zero delay" in {
      Await.result(Source(1 to 10).initialDelay(Duration.Zero).grouped(100).runWith(Sink.head), 1.second) should ===(
        1 to 10)
    }

    "delay elements by the specified time but not more" in {
      a[TimeoutException] shouldBe thrownBy {
        Await.result(Source(1 to 10).initialDelay(2.seconds).initialTimeout(1.second).runWith(Sink.ignore), 2.seconds)
      }

      Await.ready(Source(1 to 10).initialDelay(1.seconds).initialTimeout(2.second).runWith(Sink.ignore), 2.seconds)
    }

    "properly ignore timer while backpressured" in {
      val probe = TestSubscriber.probe[Int]()
      Source(1 to 10).initialDelay(0.5.second).runWith(Sink.fromSubscriber(probe))

      probe.ensureSubscription()
      probe.expectNoMessage(1.5.second)
      probe.request(20)
      probe.expectNextN(1 to 10)

      probe.expectComplete()
    }

  }

}
