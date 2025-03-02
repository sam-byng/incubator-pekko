/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.ddata

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.cluster.Cluster
import pekko.remote.testconductor.RoleName
import pekko.remote.testkit.MultiNodeConfig
import pekko.remote.testkit.MultiNodeSpec
import pekko.remote.transport.ThrottlerTransportAdapter.Direction
import pekko.testkit._

object ReplicatorChaosSpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")
  val fourth = role("fourth")
  val fifth = role("fifth")

  commonConfig(ConfigFactory.parseString("""
    pekko.loglevel = INFO
    pekko.actor.provider = "cluster"
    pekko.cluster.roles = ["backend"]
    pekko.log-dead-letters-during-shutdown = off
    """))

  testTransport(on = true)
}

class ReplicatorChaosSpecMultiJvmNode1 extends ReplicatorChaosSpec
class ReplicatorChaosSpecMultiJvmNode2 extends ReplicatorChaosSpec
class ReplicatorChaosSpecMultiJvmNode3 extends ReplicatorChaosSpec
class ReplicatorChaosSpecMultiJvmNode4 extends ReplicatorChaosSpec
class ReplicatorChaosSpecMultiJvmNode5 extends ReplicatorChaosSpec

class ReplicatorChaosSpec extends MultiNodeSpec(ReplicatorChaosSpec) with STMultiNodeSpec with ImplicitSender {
  import Replicator._
  import ReplicatorChaosSpec._

  override def initialParticipants = roles.size

  val cluster = Cluster(system)
  implicit val selfUniqueAddress: SelfUniqueAddress = DistributedData(system).selfUniqueAddress
  val replicator = system.actorOf(
    Replicator.props(ReplicatorSettings(system).withRole("backend").withGossipInterval(1.second)),
    "replicator")
  val timeout = 3.seconds.dilated

  val KeyA = GCounterKey("A")
  val KeyB = PNCounterKey("B")
  val KeyC = GCounterKey("C")
  val KeyD = GCounterKey("D")
  val KeyE = GSetKey[String]("E")
  val KeyF = ORSetKey[String]("F")
  val KeyX = GCounterKey("X")

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      cluster.join(node(to).address)
    }
    enterBarrier(from.name + "-joined")
  }

  def assertValue(key: Key[ReplicatedData], expected: Any): Unit =
    within(10.seconds) {
      awaitAssert {
        replicator ! Get(key, ReadLocal)
        val value = expectMsgPF() {
          case g @ GetSuccess(`key`, _) =>
            g.dataValue match {
              case c: GCounter  => c.value
              case c: PNCounter => c.value
              case c: GSet[_]   => c.elements
              case c: ORSet[_]  => c.elements
              case _            => fail()
            }
        }
        value should be(expected)
      }
    }

  def assertDeleted(key: Key[ReplicatedData]): Unit =
    within(5.seconds) {
      awaitAssert {
        replicator ! Get(key, ReadLocal)
        expectMsg(GetDataDeleted(key, None))
      }
    }

  "Replicator in chaotic cluster" must {

    "replicate data in initial phase" in {
      join(first, first)
      join(second, first)
      join(third, first)
      join(fourth, first)
      join(fifth, first)

      within(10.seconds) {
        awaitAssert {
          replicator ! GetReplicaCount
          expectMsg(ReplicaCount(5))
        }
      }
      enterBarrier("all-joined")

      runOn(first) {
        for (_ <- 0 until 5) {
          replicator ! Update(KeyA, GCounter(), WriteLocal)(_ :+ 1)
          replicator ! Update(KeyB, PNCounter(), WriteLocal)(_.decrement(1))
          replicator ! Update(KeyC, GCounter(), WriteAll(timeout))(_ :+ 1)
        }
        receiveN(15).map(_.getClass).toSet should be(Set(classOf[UpdateSuccess[_]]))
      }

      runOn(second) {
        replicator ! Update(KeyA, GCounter(), WriteLocal)(_ :+ 20)
        replicator ! Update(KeyB, PNCounter(), WriteTo(2, timeout))(_ :+ 20)
        replicator ! Update(KeyC, GCounter(), WriteAll(timeout))(_ :+ 20)
        receiveN(3).toSet should be(
          Set(UpdateSuccess(KeyA, None), UpdateSuccess(KeyB, None), UpdateSuccess(KeyC, None)))

        replicator ! Update(KeyE, GSet(), WriteLocal)(_ + "e1" + "e2")
        expectMsg(UpdateSuccess(KeyE, None))

        replicator ! Update(KeyF, ORSet(), WriteLocal)(_ :+ "e1" :+ "e2")
        expectMsg(UpdateSuccess(KeyF, None))
      }

      runOn(fourth) {
        replicator ! Update(KeyD, GCounter(), WriteLocal)(_ :+ 40)
        expectMsg(UpdateSuccess(KeyD, None))

        replicator ! Update(KeyE, GSet(), WriteLocal)(_ + "e2" + "e3")
        expectMsg(UpdateSuccess(KeyE, None))

        replicator ! Update(KeyF, ORSet(), WriteLocal)(_ :+ "e2" :+ "e3")
        expectMsg(UpdateSuccess(KeyF, None))
      }

      runOn(fifth) {
        replicator ! Update(KeyX, GCounter(), WriteTo(2, timeout))(_ :+ 50)
        expectMsg(UpdateSuccess(KeyX, None))
        replicator ! Delete(KeyX, WriteLocal)
        expectMsg(DeleteSuccess(KeyX, None))
      }

      enterBarrier("initial-updates-done")

      assertValue(KeyA, 25)
      assertValue(KeyB, 15)
      assertValue(KeyC, 25)
      assertValue(KeyD, 40)
      assertValue(KeyE, Set("e1", "e2", "e3"))
      assertValue(KeyF, Set("e1", "e2", "e3"))
      assertDeleted(KeyX)

      enterBarrier("after-1")
    }

    "be available during network split" in {
      val side1 = Seq(first, second)
      val side2 = Seq(third, fourth, fifth)
      runOn(first) {
        for (a <- side1; b <- side2)
          testConductor.blackhole(a, b, Direction.Both).await
      }
      enterBarrier("split")

      runOn(first) {
        replicator ! Update(KeyA, GCounter(), WriteTo(2, timeout))(_ :+ 1)
        expectMsg(UpdateSuccess(KeyA, None))
      }

      runOn(third) {
        replicator ! Update(KeyA, GCounter(), WriteTo(2, timeout))(_ :+ 2)
        expectMsg(UpdateSuccess(KeyA, None))

        replicator ! Update(KeyE, GSet(), WriteTo(2, timeout))(_ + "e4")
        expectMsg(UpdateSuccess(KeyE, None))

        replicator ! Update(KeyF, ORSet(), WriteTo(2, timeout))(_.remove("e2"))
        expectMsg(UpdateSuccess(KeyF, None))
      }
      runOn(fourth) {
        replicator ! Update(KeyD, GCounter(), WriteTo(2, timeout))(_ :+ 1)
        expectMsg(UpdateSuccess(KeyD, None))
      }
      enterBarrier("update-during-split")

      runOn(side1: _*) {
        assertValue(KeyA, 26)
        assertValue(KeyB, 15)
        assertValue(KeyD, 40)
        assertValue(KeyE, Set("e1", "e2", "e3"))
        assertValue(KeyF, Set("e1", "e2", "e3"))
      }
      runOn(side2: _*) {
        assertValue(KeyA, 27)
        assertValue(KeyB, 15)
        assertValue(KeyD, 41)
        assertValue(KeyE, Set("e1", "e2", "e3", "e4"))
        assertValue(KeyF, Set("e1", "e3"))
      }
      enterBarrier("update-during-split-verified")

      runOn(first) {
        testConductor.exit(fourth, 0).await
      }

      enterBarrier("after-2")
    }

    "converge after partition" in {
      val side1 = Seq(first, second)
      val side2 = Seq(third, fifth) // fourth was shutdown
      runOn(first) {
        for (a <- side1; b <- side2)
          testConductor.passThrough(a, b, Direction.Both).await
      }
      enterBarrier("split-repaired")

      assertValue(KeyA, 28)
      assertValue(KeyB, 15)
      assertValue(KeyC, 25)
      assertValue(KeyD, 41)
      assertValue(KeyE, Set("e1", "e2", "e3", "e4"))
      assertValue(KeyF, Set("e1", "e3"))
      assertDeleted(KeyX)

      enterBarrier("after-3")
    }
  }

}
