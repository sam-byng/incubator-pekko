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

package docs.stream

import org.apache.pekko.stream.scaladsl._
import org.apache.pekko.stream.{ DelayOverflowStrategy, KillSwitches }
import org.apache.pekko.testkit.PekkoSpec
import docs.CompileOnlySpec

import scala.concurrent.Await
import scala.concurrent.duration._

class KillSwitchDocSpec extends PekkoSpec with CompileOnlySpec {

  "Unique kill switch" must {

    "control graph completion with shutdown" in compileOnlySpec {

      // format: OFF
      //#unique-shutdown
      val countingSrc = Source(Stream.from(1)).delay(1.second, DelayOverflowStrategy.backpressure)
      val lastSnk = Sink.last[Int]

      val (killSwitch, last) = countingSrc
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(lastSnk)(Keep.both)
        .run()

      doSomethingElse()

      killSwitch.shutdown()

      Await.result(last, 1.second) shouldBe 2
      //#unique-shutdown
      // format: ON
    }

    "control graph completion with abort" in compileOnlySpec {

      // format: OFF
      //#unique-abort
      val countingSrc = Source(Stream.from(1)).delay(1.second, DelayOverflowStrategy.backpressure)
      val lastSnk = Sink.last[Int]

      val (killSwitch, last) = countingSrc
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(lastSnk)(Keep.both).run()

      val error = new RuntimeException("boom!")
      killSwitch.abort(error)

      Await.result(last.failed, 1.second) shouldBe error
      //#unique-abort
      // format: ON
    }
  }

  "Shared kill switch" must {

    "control graph completion with shutdown" in compileOnlySpec {
      // format: OFF
      //#shared-shutdown
      val countingSrc = Source(Stream.from(1)).delay(1.second, DelayOverflowStrategy.backpressure)
      val lastSnk = Sink.last[Int]
      val sharedKillSwitch = KillSwitches.shared("my-kill-switch")

      val last = countingSrc
        .via(sharedKillSwitch.flow)
        .runWith(lastSnk)

      val delayedLast = countingSrc
        .delay(1.second, DelayOverflowStrategy.backpressure)
        .via(sharedKillSwitch.flow)
        .runWith(lastSnk)

      doSomethingElse()

      sharedKillSwitch.shutdown()

      Await.result(last, 1.second) shouldBe 2
      Await.result(delayedLast, 1.second) shouldBe 1
      //#shared-shutdown
      // format: ON
    }

    "control graph completion with abort" in compileOnlySpec {

      // format: OFF
      //#shared-abort
      val countingSrc = Source(Stream.from(1)).delay(1.second)
      val lastSnk = Sink.last[Int]
      val sharedKillSwitch = KillSwitches.shared("my-kill-switch")

      val last1 = countingSrc.via(sharedKillSwitch.flow).runWith(lastSnk)
      val last2 = countingSrc.via(sharedKillSwitch.flow).runWith(lastSnk)

      val error = new RuntimeException("boom!")
      sharedKillSwitch.abort(error)

      Await.result(last1.failed, 1.second) shouldBe error
      Await.result(last2.failed, 1.second) shouldBe error
      //#shared-abort
      // format: ON
    }
  }

  private def doSomethingElse() = ???
}
