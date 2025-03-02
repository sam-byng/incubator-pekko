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

package org.apache.pekko.cluster.typed

import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.ActorTestKit
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.scaladsl.Behaviors

object RemoteDeployNotAllowedSpec {
  def config = ConfigFactory.parseString(s"""
    pekko {
      loglevel = warning
      actor {
        provider = cluster
      }
      remote.classic.netty.tcp.port = 0
      remote.artery {
        canonical {
          hostname = 127.0.0.1
          port = 0
        }
      }
      cluster.jmx.enabled = false
    }
    """)

  def configWithRemoteDeployment(otherSystemPort: Int) = ConfigFactory.parseString(s"""
      pekko.actor.deployment {
        "/*" {
          remote = "pekko://sampleActorSystem@127.0.0.1:$otherSystemPort"
        }
      }
    """).withFallback(config)
}

class RemoteDeployNotAllowedSpec
    extends ScalaTestWithActorTestKit(RemoteDeployNotAllowedSpec.config)
    with AnyWordSpecLike
    with LogCapturing {

  "Typed cluster" must {

    "not allow remote deployment" in {
      val node1 = Cluster(system)
      node1.manager ! Join(node1.selfMember.address)
      val probe = TestProbe[AnyRef]()(system)

      trait GuardianProtocol
      case class SpawnChild(name: String) extends GuardianProtocol
      case object SpawnAnonymous extends GuardianProtocol

      val guardianBehavior = Behaviors.receive[GuardianProtocol] { (ctx, msg) =>
        msg match {
          case SpawnChild(name) =>
            // this should throw
            try {
              ctx.spawn(Behaviors.setup[AnyRef] { _ =>
                  Behaviors.empty
                }, name)
            } catch {
              case ex: Exception => probe.ref ! ex
            }
            Behaviors.same

          case SpawnAnonymous =>
            // this should throw
            try {
              ctx.spawnAnonymous(Behaviors.setup[AnyRef] { _ =>
                Behaviors.empty
              })
            } catch {
              case ex: Exception => probe.ref ! ex
            }
            Behaviors.same

          case unexpected => throw new RuntimeException(s"Unexpected: $unexpected")
        }

      }

      val system2 =
        ActorSystem(
          guardianBehavior,
          system.name,
          RemoteDeployNotAllowedSpec.configWithRemoteDeployment(node1.selfMember.address.port.get))
      try {
        val node2 = Cluster(system2)
        node2.manager ! Join(node1.selfMember.address)

        system2 ! SpawnChild("remoteDeployed")
        probe.expectMessageType[Exception].getMessage should ===("Remote deployment not allowed for typed actors")

        system2 ! SpawnAnonymous
        probe.expectMessageType[Exception].getMessage should ===("Remote deployment not allowed for typed actors")
      } finally {
        ActorTestKit.shutdown(system2)
      }
    }
  }

}
