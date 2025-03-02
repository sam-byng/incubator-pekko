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

package org.apache.pekko.cluster

import scala.collection.immutable
import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorSystem
import pekko.actor.Address
import pekko.actor.Deploy
import pekko.actor.Props
import pekko.actor.RootActorPath
import pekko.cluster.MemberStatus._
import pekko.remote.testkit.MultiNodeConfig
import pekko.remote.testkit.MultiNodeSpec
import pekko.testkit._
import pekko.util.ccompat._

@ccompatUsedUntil213
object RestartNode2SpecMultiJvmSpec extends MultiNodeConfig {
  val seed1 = role("seed1")
  val seed2 = role("seed2")

  commonConfig(
    debugConfig(on = false)
      .withFallback(ConfigFactory.parseString("""
      pekko.cluster.downing-provider-class = org.apache.pekko.cluster.testkit.AutoDowning
      pekko.cluster.testkit.auto-down-unreachable-after = 2s
      pekko.cluster.retry-unsuccessful-join-after = 3s
      pekko.cluster.allow-weakly-up-members = off
      pekko.remote.retry-gate-closed-for = 45s
      pekko.remote.log-remote-lifecycle-events = INFO
      # test is using Java serialization and not priority to rewrite
      pekko.actor.allow-java-serialization = on
      pekko.actor.warn-about-java-serializer-usage = off
      """))
      .withFallback(MultiNodeClusterSpec.clusterConfig))

}

class RestartNode2SpecMultiJvmNode1 extends RestartNode2SpecSpec
class RestartNode2SpecMultiJvmNode2 extends RestartNode2SpecSpec

abstract class RestartNode2SpecSpec extends MultiNodeClusterSpec(RestartNode2SpecMultiJvmSpec) with ImplicitSender {

  import RestartNode2SpecMultiJvmSpec._

  @volatile var seedNode1Address: Address = _

  // use a separate ActorSystem, to be able to simulate restart
  lazy val seed1System = ActorSystem(system.name, MultiNodeSpec.configureNextPortIfFixed(system.settings.config))

  override def verifySystemShutdown: Boolean = true

  def seedNodes: immutable.IndexedSeq[Address] = Vector(seedNode1Address, seed2)

  // this is the node that will attempt to re-join, keep gate times low so it can retry quickly
  lazy val restartedSeed1System = ActorSystem(
    system.name,
    ConfigFactory.parseString(s"""
      pekko.remote.classic.netty.tcp.port = ${seedNodes.head.port.get}
      pekko.remote.artery.canonical.port = ${seedNodes.head.port.get}
      #pekko.remote.retry-gate-closed-for = 1s
      """).withFallback(system.settings.config))

  override def afterAll(): Unit = {
    runOn(seed1) {
      shutdown(if (seed1System.whenTerminated.isCompleted) restartedSeed1System else seed1System)
    }
    super.afterAll()
  }

  "Cluster seed nodes" must {
    "be able to restart first seed node and join other seed nodes" taggedAs LongRunningTest in within(60.seconds) {
      // seed1System is a separate ActorSystem, to be able to simulate restart
      // we must transfer its address to seed2
      runOn(seed2) {
        system.actorOf(Props(new Actor {
            def receive = {
              case a: Address =>
                seedNode1Address = a
                sender() ! "ok"
            }
          }).withDeploy(Deploy.local), name = "address-receiver")
        enterBarrier("seed1-address-receiver-ready")
      }

      runOn(seed1) {
        enterBarrier("seed1-address-receiver-ready")
        seedNode1Address = Cluster(seed1System).selfAddress
        List(seed2).foreach { r =>
          system.actorSelection(RootActorPath(r) / "user" / "address-receiver") ! seedNode1Address
          expectMsg(5.seconds, "ok")
        }
      }
      enterBarrier("seed1-address-transferred")

      // now we can join seed1System, seed2 together

      runOn(seed1) {
        Cluster(seed1System).joinSeedNodes(seedNodes)
        awaitAssert(Cluster(seed1System).readView.members.size should be(2))
        awaitAssert(Cluster(seed1System).readView.members.unsorted.map(_.status) should be(Set(Up)))
      }
      runOn(seed2) {
        cluster.joinSeedNodes(seedNodes)
        awaitMembersUp(2)
      }
      enterBarrier("started")

      // shutdown seed1System
      runOn(seed1) {
        shutdown(seed1System, remainingOrDefault)
      }
      enterBarrier("seed1-shutdown")

      // then start restartedSeed1System, which has the same address as seed1System
      runOn(seed1) {
        Cluster(restartedSeed1System).joinSeedNodes(seedNodes)
        within(30.seconds) {
          awaitAssert(Cluster(restartedSeed1System).readView.members.size should be(2))
          awaitAssert(Cluster(restartedSeed1System).readView.members.unsorted.map(_.status) should be(Set(Up)))
        }
      }
      runOn(seed2) {
        awaitMembersUp(2)
      }
      enterBarrier("seed1-restarted")

    }

  }
}
