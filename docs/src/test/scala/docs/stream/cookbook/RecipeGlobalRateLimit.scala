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

package docs.stream.cookbook

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.{ Actor, ActorRef, Props }
import org.apache.pekko.stream.ClosedShape
import org.apache.pekko.stream.scaladsl._
import org.apache.pekko.stream.testkit._
import org.apache.pekko.testkit._

import scala.collection.immutable
import scala.concurrent.duration._

class RecipeGlobalRateLimit extends RecipeSpec {

  "Global rate limiting recipe" must {

    // #global-limiter-actor
    object Limiter {
      case object WantToPass
      case object MayPass

      case object ReplenishTokens

      def props(maxAvailableTokens: Int, tokenRefreshPeriod: FiniteDuration, tokenRefreshAmount: Int): Props =
        Props(new Limiter(maxAvailableTokens, tokenRefreshPeriod, tokenRefreshAmount))
    }

    class Limiter(val maxAvailableTokens: Int, val tokenRefreshPeriod: FiniteDuration, val tokenRefreshAmount: Int)
        extends Actor {
      import Limiter._
      import context.dispatcher
      import org.apache.pekko.actor.Status

      private var waitQueue = immutable.Queue.empty[ActorRef]
      private var permitTokens = maxAvailableTokens
      private val replenishTimer = system.scheduler.scheduleWithFixedDelay(
        initialDelay = tokenRefreshPeriod,
        delay = tokenRefreshPeriod,
        receiver = self,
        ReplenishTokens)

      override def receive: Receive = open

      val open: Receive = {
        case ReplenishTokens =>
          permitTokens = math.min(permitTokens + tokenRefreshAmount, maxAvailableTokens)
        case WantToPass =>
          permitTokens -= 1
          sender() ! MayPass
          if (permitTokens == 0) context.become(closed)
      }

      val closed: Receive = {
        case ReplenishTokens =>
          permitTokens = math.min(permitTokens + tokenRefreshAmount, maxAvailableTokens)
          releaseWaiting()
        case WantToPass =>
          waitQueue = waitQueue.enqueue(sender())
      }

      private def releaseWaiting(): Unit = {
        val (toBeReleased, remainingQueue) = waitQueue.splitAt(permitTokens)
        waitQueue = remainingQueue
        permitTokens -= toBeReleased.size
        toBeReleased.foreach(_ ! MayPass)
        if (permitTokens > 0) context.become(open)
      }

      override def postStop(): Unit = {
        replenishTimer.cancel()
        waitQueue.foreach(_ ! Status.Failure(new IllegalStateException("limiter stopped")))
      }
    }
    // #global-limiter-actor

    "work" in {

      // #global-limiter-flow
      def limitGlobal[T](limiter: ActorRef, maxAllowedWait: FiniteDuration): Flow[T, T, NotUsed] = {
        import org.apache.pekko
        import pekko.pattern.ask
        import pekko.util.Timeout
        Flow[T].mapAsync(4)((element: T) => {
          import system.dispatcher
          implicit val triggerTimeout = Timeout(maxAllowedWait)
          val limiterTriggerFuture = limiter ? Limiter.WantToPass
          limiterTriggerFuture.map(_ => element)
        })

      }
      // #global-limiter-flow

      // Use a large period and emulate the timer by hand instead
      val limiter = system.actorOf(Limiter.props(2, 100.days, 1), "limiter")

      val source1 = Source.fromIterator(() => Iterator.continually("E1")).via(limitGlobal(limiter, 2.seconds.dilated))
      val source2 = Source.fromIterator(() => Iterator.continually("E2")).via(limitGlobal(limiter, 2.seconds.dilated))

      val probe = TestSubscriber.manualProbe[String]()

      RunnableGraph
        .fromGraph(GraphDSL.create() { implicit b =>
          import GraphDSL.Implicits._
          val merge = b.add(Merge[String](2))
          source1 ~> merge ~> Sink.fromSubscriber(probe)
          source2 ~> merge
          ClosedShape
        })
        .run()

      probe.expectSubscription().request(1000)

      probe.expectNext() should startWith("E")
      probe.expectNext() should startWith("E")
      probe.expectNoMessage()

      limiter ! Limiter.ReplenishTokens
      probe.expectNext() should startWith("E")
      probe.expectNoMessage()

      var resultSet = Set.empty[String]
      for (_ <- 1 to 100) {
        limiter ! Limiter.ReplenishTokens
        resultSet += probe.expectNext()
      }

      resultSet.contains("E1") should be(true)
      resultSet.contains("E2") should be(true)

      probe.expectError()

    }

  }

}
