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

package org.apache.pekko.remote.classic.transport

import scala.concurrent.{ Await, Future, Promise }
import scala.util.Failure
import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.PekkoException
import pekko.remote.transport.TestTransport.SwitchableLoggedBehavior
import pekko.testkit.{ DefaultTimeout, PekkoSpec }

object SwitchableLoggedBehaviorSpec {
  object TestException extends PekkoException("Test exception") with NoStackTrace
}

class SwitchableLoggedBehaviorSpec extends PekkoSpec with DefaultTimeout {
  import pekko.remote.classic.transport.SwitchableLoggedBehaviorSpec._

  private def defaultBehavior = new SwitchableLoggedBehavior[Unit, Int](_ => Future.successful(3), _ => ())

  "A SwitchableLoggedBehavior" must {

    "execute default behavior" in {
      val behavior = defaultBehavior

      Await.result(behavior(()), timeout.duration) should ===(3)
    }

    "be able to push generic behavior" in {
      val behavior = defaultBehavior

      behavior.push(_ => Future.successful(4))
      Await.result(behavior(()), timeout.duration) should ===(4)

      behavior.push(_ => Future.failed(TestException))
      behavior(()).value match {
        case Some(Failure(`TestException`)) =>
        case _                              => fail("Expected exception")
      }
    }

    "be able to push constant behavior" in {
      val behavior = defaultBehavior
      behavior.pushConstant(5)

      Await.result(behavior(()), timeout.duration) should ===(5)
      Await.result(behavior(()), timeout.duration) should ===(5)
    }

    "be able to push failure behavior" in {
      val behavior = defaultBehavior
      behavior.pushError(TestException)

      behavior(()).value match {
        case Some(Failure(e)) if e eq TestException =>
        case _                                      => fail("Expected exception")
      }
    }

    "be able to push and pop behavior" in {
      val behavior = defaultBehavior

      behavior.pushConstant(5)
      Await.result(behavior(()), timeout.duration) should ===(5)

      behavior.pushConstant(7)
      Await.result(behavior(()), timeout.duration) should ===(7)

      behavior.pop()
      Await.result(behavior(()), timeout.duration) should ===(5)

      behavior.pop()
      Await.result(behavior(()), timeout.duration) should ===(3)

    }

    "protect the default behavior from popped out" in {
      val behavior = defaultBehavior
      behavior.pop()
      behavior.pop()
      behavior.pop()

      Await.result(behavior(()), timeout.duration) should ===(3)
    }

    "enable delayed completion" in {
      val behavior = defaultBehavior
      val controlPromise = behavior.pushDelayed
      val f = behavior(())

      f.isCompleted should ===(false)
      controlPromise.success(())

      awaitCond(f.isCompleted)
    }

    "log calls and parameters" in {
      val logPromise = Promise[Int]()
      val behavior = new SwitchableLoggedBehavior[Int, Int](_ => Future.successful(3), i => logPromise.success(i))

      behavior(11)
      Await.result(logPromise.future, timeout.duration) should ===(11)
    }

  }

}
