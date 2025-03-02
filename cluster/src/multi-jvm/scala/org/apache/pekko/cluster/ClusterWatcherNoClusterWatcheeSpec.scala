/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorIdentity
import pekko.actor.ActorRef
import pekko.actor.Identify
import pekko.actor.Props
import pekko.actor.Terminated
import pekko.remote.RARP
import pekko.remote.RemoteWatcher.Heartbeat
import pekko.remote.RemoteWatcher.Stats
import pekko.remote.testconductor.RoleName
import pekko.remote.testkit.MultiNodeConfig
import pekko.testkit.ImplicitSender
import pekko.testkit.TestProbe

class ClusterWatcherNoClusterWatcheeConfig(val useUnsafe: Boolean, artery: Boolean) extends MultiNodeConfig {

  val clustered = role("clustered")
  val remoting = role("remoting")

  commonConfig(debugConfig(on = false).withFallback(ConfigFactory.parseString(s"""
      pekko.remote.use-unsafe-remote-features-outside-cluster = $useUnsafe
      pekko.remote.log-remote-lifecycle-events = off
      pekko.remote.artery.enabled = $artery
      pekko.log-dead-letters = off
      pekko.loggers =["org.apache.pekko.testkit.TestEventListener"]
      pekko.actor.allow-java-serialization = on
     """)))

  nodeConfig(remoting)(ConfigFactory.parseString(s"""
      pekko.actor.provider = remote"""))

  nodeConfig(clustered)(ConfigFactory.parseString("""
      pekko.actor.provider = cluster
      pekko.cluster.jmx.enabled = off"""))

}

class ClusterWatcherNoClusterWatcheeUnsafeArterySpecMultiJvmNode1
    extends ClusterWatcherNoClusterWatcheeArterySpec(useUnsafe = true)
class ClusterWatcherNoClusterWatcheeUnsafeArterySpecMultiJvmNode2
    extends ClusterWatcherNoClusterWatcheeArterySpec(useUnsafe = true)

class ClusterWatcherNoClusterWatcheeSafeArterySpecMultiJvmNode1
    extends ClusterWatcherNoClusterWatcheeArterySpec(useUnsafe = false)
class ClusterWatcherNoClusterWatcheeSafeArterySpecMultiJvmNode2
    extends ClusterWatcherNoClusterWatcheeArterySpec(useUnsafe = false)

class ClusterWatcherNoClusterWatcheeUnsafeClassicSpecMultiJvmNode1
    extends ClusterWatcherNoClusterWatcheeClassicSpec(useUnsafe = true)
class ClusterWatcherNoClusterWatcheeUnsafeClassicSpecMultiJvmNode2
    extends ClusterWatcherNoClusterWatcheeClassicSpec(useUnsafe = true)

class ClusterWatcherNoClusterWatcheeSafeClassicSpecMultiJvmNode1
    extends ClusterWatcherNoClusterWatcheeClassicSpec(useUnsafe = false)
class ClusterWatcherNoClusterWatcheeSafeClassicSpecMultiJvmNode2
    extends ClusterWatcherNoClusterWatcheeClassicSpec(useUnsafe = false)

abstract class ClusterWatcherNoClusterWatcheeArterySpec(useUnsafe: Boolean)
    extends ClusterWatcherNoClusterWatcheeSpec(new ClusterWatcherNoClusterWatcheeConfig(useUnsafe, artery = true))

abstract class ClusterWatcherNoClusterWatcheeClassicSpec(useUnsafe: Boolean)
    extends ClusterWatcherNoClusterWatcheeSpec(new ClusterWatcherNoClusterWatcheeConfig(useUnsafe, artery = true))

private object ClusterWatcherNoClusterWatcheeSpec {
  final case class WatchIt(watchee: ActorRef)
  case object Ack
  final case class WrappedTerminated(t: Terminated)

  class Listener(testActor: ActorRef) extends Actor {
    def receive: Receive = {
      case WatchIt(watchee) =>
        context.watch(watchee)
        sender() ! Ack
      case t: Terminated =>
        testActor.forward(WrappedTerminated(t))
    }
  }
}

abstract class ClusterWatcherNoClusterWatcheeSpec(multiNodeConfig: ClusterWatcherNoClusterWatcheeConfig)
    extends MultiNodeClusterSpec(multiNodeConfig)
    with ImplicitSender
    with ScalaFutures {

  import ClusterWatcherNoClusterWatcheeSpec._
  import multiNodeConfig._

  override def initialParticipants: Int = roles.size

  muteDeadLetters(Heartbeat.getClass)()

  protected val probe = TestProbe()

  protected def identify(role: RoleName, actorName: String, within: FiniteDuration = 10.seconds): ActorRef =
    identifyWithPath(role, "user", actorName, within)

  protected def identifyWithPath(
      role: RoleName,
      path: String,
      actorName: String,
      within: FiniteDuration = 10.seconds): ActorRef = {
    system.actorSelection(node(role) / path / actorName) ! Identify(actorName)
    val id = expectMsgType[ActorIdentity](within)
    assert(id.ref.isDefined, s"Unable to Identify actor [$actorName] on node [$role].")
    id.ref.get
  }

  private val provider = RARP(system).provider

  s"Remoting with UseUnsafeRemoteFeaturesWithoutCluster enabled=$useUnsafe, " +
  "watcher system using `cluster`, but watchee system using `remote`" must {

    val send = if (system.settings.HasCluster || (!system.settings.HasCluster && useUnsafe)) "send" else "not send"

    s"$send `Watch`/`Unwatch`/`Terminate` when watching from cluster to non-cluster remoting watchee" in {
      runOn(remoting) {
        system.actorOf(Props(classOf[Listener], probe.ref), "watchee")
        enterBarrier("watchee-created")
        enterBarrier("watcher-created")
      }

      runOn(clustered) {
        enterBarrier("watchee-created")
        val watcher = system.actorOf(Props(classOf[Listener], probe.ref), "watcher")
        enterBarrier("watcher-created")

        val watchee = identify(remoting, "watchee")
        probe.send(watcher, WatchIt(watchee))
        probe.expectMsg(1.second, Ack)
        provider.remoteWatcher.get ! Stats
        awaitAssert(expectMsgType[Stats].watchingRefs == Set((watchee, watcher)), 2.seconds)
      }
      enterBarrier("cluster-watching-remote")

      runOn(remoting) {
        system.stop(identify(remoting, "watchee"))
        enterBarrier("watchee-stopped")
      }

      runOn(clustered) {
        enterBarrier("watchee-stopped")
        if (useUnsafe)
          probe.expectMsgType[WrappedTerminated](2.seconds)
        else
          probe.expectNoMessage(2.seconds)
      }
    }

    s"$send `Watch`/`Unwatch`/`Terminate` when watching from non-cluster remoting to cluster watchee" in {
      runOn(clustered) {
        system.actorOf(Props(classOf[Listener], probe.ref), "watchee2")
        enterBarrier("watchee2-created")
        enterBarrier("watcher2-created")
      }

      runOn(remoting) {
        enterBarrier("watchee2-created")
        val watchee = identify(clustered, "watchee2")

        val watcher = system.actorOf(Props(classOf[Listener], probe.ref), "watcher2")
        enterBarrier("watcher2-created")

        probe.send(watcher, WatchIt(watchee))
        probe.expectMsg(1.second, Ack)

        if (useUnsafe) {
          provider.remoteWatcher.get ! Stats
          awaitAssert(expectMsgType[Stats].watchingRefs == Set((watchee, watcher)), 2.seconds)
        }
      }

      runOn(clustered) {
        system.stop(identify(clustered, "watchee2"))
        enterBarrier("watchee2-stopped")
      }

      runOn(remoting) {
        enterBarrier("watchee2-stopped")
        if (useUnsafe)
          probe.expectMsgType[WrappedTerminated](2.seconds)
        else
          probe.expectNoMessage(2.seconds)
      }

      enterBarrier("done")
    }
  }
}
