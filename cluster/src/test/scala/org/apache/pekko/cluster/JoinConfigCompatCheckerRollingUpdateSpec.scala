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

import scala.collection.{ immutable => im }
import scala.concurrent.duration._

import com.typesafe.config.{ Config, ConfigFactory }

import org.apache.pekko
import pekko.testkit.GHExcludeAeronTest
import pekko.testkit.LongRunningTest

object JoinConfigCompatCheckerRollingUpdateSpec {

  val baseConfig = ConfigFactory.parseString(s"""
      pekko.log-dead-letters = off
      pekko.log-dead-letters-during-shutdown = off
      pekko.remote.log-remote-lifecycle-events = off
      pekko.cluster.downing-provider-class = org.apache.pekko.cluster.testkit.AutoDowning
      pekko.cluster.testkit.auto-down-unreachable-after = 0s
      pekko.cluster {
        jmx.enabled                         = off
        gossip-interval                     = 200 ms
        leader-actions-interval             = 200 ms
        unreachable-nodes-reaper-interval   = 500 ms
        periodic-tasks-initial-delay        = 300 ms
        publish-stats-interval              = 0 s # always, when it happens
      }
    """).withFallback(JoinConfigCompatCheckerSpec.baseConfig)

  val v1Config: Config = baseConfig.withFallback(JoinConfigCompatCheckerSpec.configWithChecker)

  private val v2 = ConfigFactory.parseString("""
      pekko.cluster.new-configuration = "v2"
      pekko.cluster.configuration-compatibility-check.checkers {
        rolling-upgrade-test = "org.apache.pekko.cluster.JoinConfigCompatRollingUpdateChecker"
      }
    """)

  val v2Config: Config = v2.withFallback(v1Config)

  val v2ConfigIncompatible: Config = v2.withFallback(baseConfig)

}

class JoinConfigCompatCheckerRollingUpdateSpec
    extends RollingUpgradeClusterSpec(JoinConfigCompatCheckerRollingUpdateSpec.v1Config) {

  import JoinConfigCompatCheckerRollingUpdateSpec._

  "A Node" must {
    val timeout = 20.seconds
    "NOT be allowed to re-join a cluster if it has a new, additional configuration the others do not have and not the old"
      .taggedAs(LongRunningTest, GHExcludeAeronTest) in {
      // confirms the 2 attempted re-joins fail with both nodes being terminated
      upgradeCluster(3, v1Config, v2ConfigIncompatible, timeout, timeout, enforced = true, shouldRejoin = false)
    }
    "be allowed to re-join a cluster if it has a new, additional property and checker the others do not have".taggedAs(
      LongRunningTest,
      GHExcludeAeronTest) in {
      upgradeCluster(3, v1Config, v2Config, timeout, timeout * 3, enforced = true, shouldRejoin = true)
    }
    "be allowed to re-join a cluster if it has a new, additional configuration the others do not have and configured to NOT enforce it"
      .taggedAs(LongRunningTest, GHExcludeAeronTest) in {
      upgradeCluster(3, v1Config, v2Config, timeout, timeout * 3, enforced = false, shouldRejoin = true)
    }
  }
}

class JoinConfigCompatRollingUpdateChecker extends JoinConfigCompatChecker {
  override def requiredKeys: im.Seq[String] = im.Seq("pekko.cluster.new-configuration")
  override def check(toCheck: Config, actualConfig: Config): ConfigValidation = {
    if (toCheck.hasPath(requiredKeys.head))
      JoinConfigCompatChecker.fullMatch(requiredKeys, toCheck, actualConfig)
    else Valid
  }
}
