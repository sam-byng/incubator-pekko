/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import scala.concurrent.duration._
import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.Done
import pekko.pattern.pipe
import pekko.stream._
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.scaladsl.TestSink
import pekko.stream.testkit.scaladsl.TestSource

class FlowWatchTerminationSpec extends StreamSpec {

  "A WatchTermination" must {

    "complete future when stream is completed" in {
      val (future, p) = Source(1 to 4).watchTermination()(Keep.right).toMat(TestSink.probe[Int])(Keep.both).run()
      p.request(4).expectNext(1, 2, 3, 4)
      future.futureValue should ===(Done)
      p.expectComplete()
    }

    "complete future when stream is cancelled from downstream" in {
      val (future, p) = Source(1 to 4).watchTermination()(Keep.right).toMat(TestSink.probe[Int])(Keep.both).run()
      p.request(3).expectNext(1, 2, 3).cancel()
      future.futureValue should ===(Done)
    }

    "fail future when stream is failed" in {
      val ex = new RuntimeException("Stream failed.") with NoStackTrace
      val (p, future) = TestSource.probe[Int].watchTermination()(Keep.both).to(Sink.ignore).run()
      p.sendNext(1)
      p.sendError(ex)
      whenReady(future.failed) { _ shouldBe ex }
    }

    "complete the future for an empty stream" in {
      val (future, p) = Source.empty[Int].watchTermination()(Keep.right).toMat(TestSink.probe[Int])(Keep.both).run()
      p.request(1)
      future.futureValue should ===(Done)
    }

    "complete future for graph" in {
      implicit val ec = system.dispatcher

      val ((sourceProbe, future), sinkProbe) = TestSource
        .probe[Int]
        .watchTermination()(Keep.both)
        .concat(Source(2 to 5))
        .toMat(TestSink.probe[Int])(Keep.both)
        .run()
      future.pipeTo(testActor)
      sinkProbe.request(5)
      sourceProbe.sendNext(1)
      sinkProbe.expectNext(1)
      expectNoMessage(300.millis)

      sourceProbe.sendComplete()
      expectMsg(Done)

      sinkProbe.expectNextN(2 to 5).expectComplete()
    }

    "fail future when stream abruptly terminated" in {
      val mat = Materializer(system)

      val (_, future) = TestSource.probe[Int].watchTermination()(Keep.both).to(Sink.ignore).run()(mat)
      mat.shutdown()

      future.failed.futureValue shouldBe an[AbruptTerminationException]
    }

  }

}
