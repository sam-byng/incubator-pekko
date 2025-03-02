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

package org.apache.pekko.cluster

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.ActorIdentity
import pekko.actor.Identify
import pekko.actor.PoisonPill
import pekko.remote.artery.ArteryMultiNodeSpec
import pekko.testkit.ImplicitSender
import pekko.testkit.TestActors

/**
 * Reproducer for issue #24847
 */
class ResetSystemMessageSeqNrSpec extends ArteryMultiNodeSpec("""
  pekko.loglevel = INFO
  pekko.actor.provider=cluster
  pekko.cluster.jmx.multi-mbeans-in-same-jvm = on
  """) with ImplicitSender {

  "System messages sequence numbers" should {

    "be reset when connecting to new incarnation" in {

      val sys2 = newRemoteSystem(name = Some(system.name))

      Cluster(system).join(Cluster(system).selfAddress)
      Cluster(sys2).join(Cluster(system).selfAddress)
      within(10.seconds) {
        awaitAssert {
          Cluster(system).state.members.map(_.uniqueAddress) should ===(
            Set(Cluster(system).selfUniqueAddress, Cluster(sys2).selfUniqueAddress))

          Cluster(system).state.members.forall(_.status == MemberStatus.Up) shouldBe true
        }
      }

      sys2.actorOf(TestActors.echoActorProps, name = "echo1")
      system.actorSelection(rootActorPath(sys2) / "user" / "echo1") ! Identify("1")
      val echo1 = expectMsgType[ActorIdentity].ref.get
      watch(echo1)

      sys2.actorOf(TestActors.echoActorProps, name = "echo2")
      system.actorSelection(rootActorPath(sys2) / "user" / "echo2") ! Identify("2")
      val echo2 = expectMsgType[ActorIdentity].ref.get
      watch(echo2)
      echo2 ! PoisonPill
      expectTerminated(echo2) // now we know that the watch of echo1 has been established

      Cluster(sys2).leave(Cluster(sys2).selfAddress)
      within(10.seconds) {
        awaitAssert {
          Cluster(system).state.members.map(_.uniqueAddress) should not contain Cluster(sys2).selfUniqueAddress
        }
      }

      expectTerminated(echo1)
      shutdown(sys2)

      val sys3 = newRemoteSystem(
        name = Some(system.name),
        extraConfig = Some(s"pekko.remote.artery.canonical.port=${Cluster(sys2).selfAddress.port.get}"))
      Cluster(sys3).join(Cluster(system).selfAddress)
      within(10.seconds) {
        awaitAssert {
          Cluster(system).state.members.map(_.uniqueAddress) should ===(
            Set(Cluster(system).selfUniqueAddress, Cluster(sys3).selfUniqueAddress))

          Cluster(system).state.members.forall(_.status == MemberStatus.Up) shouldBe true
        }
      }

      sys3.actorOf(TestActors.echoActorProps, name = "echo3")
      system.actorSelection(rootActorPath(sys3) / "user" / "echo3") ! Identify("3")
      val echo3 = expectMsgType[ActorIdentity].ref.get
      watch(echo3)

      // To clearly see the reproducer for issue #24847 one could put a sleep here and observe the
      // "negative acknowledgment" log messages, but it also failed on the next expectTerminated because
      // the Watch message was never delivered.

      echo3 ! PoisonPill
      expectTerminated(echo3)
    }

  }
}
