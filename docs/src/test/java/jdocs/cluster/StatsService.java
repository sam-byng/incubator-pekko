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

package jdocs.cluster;

import org.apache.pekko.cluster.routing.ClusterRouterGroup;
import org.apache.pekko.cluster.routing.ClusterRouterGroupSettings;
import org.apache.pekko.cluster.routing.ClusterRouterPool;
import org.apache.pekko.cluster.routing.ClusterRouterPoolSettings;
import org.apache.pekko.routing.ConsistentHashingGroup;
import org.apache.pekko.routing.ConsistentHashingPool;
import jdocs.cluster.StatsMessages.StatsJob;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.routing.ConsistentHashingRouter.ConsistentHashableEnvelope;
import org.apache.pekko.routing.FromConfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

// #service
public class StatsService extends AbstractActor {

  // This router is used both with lookup and deploy of routees. If you
  // have a router with only lookup of routees you can use Props.empty()
  // instead of Props.create(StatsWorker.class).
  ActorRef workerRouter =
      getContext()
          .actorOf(FromConfig.getInstance().props(Props.create(StatsWorker.class)), "workerRouter");

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            StatsJob.class,
            job -> !job.getText().isEmpty(),
            job -> {
              String[] words = job.getText().split(" ");
              ActorRef replyTo = getSender();

              // create actor that collects replies from workers
              ActorRef aggregator =
                  getContext().actorOf(Props.create(StatsAggregator.class, words.length, replyTo));

              // send each word to a worker
              for (String word : words) {
                workerRouter.tell(new ConsistentHashableEnvelope(word, word), aggregator);
              }
            })
        .build();
  }
}
// #service

// not used, only for documentation
abstract class StatsService2 extends AbstractActor {
  // #router-lookup-in-code
  int totalInstances = 100;
  Iterable<String> routeesPaths = Collections.singletonList("/user/statsWorker");
  boolean allowLocalRoutees = true;
  Set<String> useRoles = new HashSet<>(Arrays.asList("compute"));
  ActorRef workerRouter =
      getContext()
          .actorOf(
              new ClusterRouterGroup(
                      new ConsistentHashingGroup(routeesPaths),
                      new ClusterRouterGroupSettings(
                          totalInstances, routeesPaths, allowLocalRoutees, useRoles))
                  .props(),
              "workerRouter2");
  // #router-lookup-in-code
}

// not used, only for documentation
abstract class StatsService3 extends AbstractActor {
  // #router-deploy-in-code
  int totalInstances = 100;
  int maxInstancesPerNode = 3;
  boolean allowLocalRoutees = false;
  Set<String> useRoles = new HashSet<>(Arrays.asList("compute"));
  ActorRef workerRouter =
      getContext()
          .actorOf(
              new ClusterRouterPool(
                      new ConsistentHashingPool(0),
                      new ClusterRouterPoolSettings(
                          totalInstances, maxInstancesPerNode, allowLocalRoutees, useRoles))
                  .props(Props.create(StatsWorker.class)),
              "workerRouter3");
  // #router-deploy-in-code
}
