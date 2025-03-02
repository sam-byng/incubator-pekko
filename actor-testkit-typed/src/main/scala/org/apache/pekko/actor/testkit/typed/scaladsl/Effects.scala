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

package org.apache.pekko.actor.testkit.typed.scaladsl

import scala.concurrent.duration.FiniteDuration

import org.apache.pekko
import pekko.actor.typed.{ ActorRef, Behavior, Props }

/**
 * Factories for behavior effects for [[BehaviorTestKit]], each effect has a suitable equals and can be used to compare
 * actual effects to expected ones.
 */
object Effects {
  import pekko.actor.testkit.typed.Effect._

  /**
   * The behavior spawned a named child with the given behavior with no specific props
   */
  def spawned[T](behavior: Behavior[T], childName: String): Spawned[T] = Spawned(behavior, childName)

  /**
   * The behavior spawned a named child with the given behavior with no specific props
   */
  def spawned[T](behavior: Behavior[T], childName: String, ref: ActorRef[T]): Spawned[T] =
    new Spawned(behavior, childName, Props.empty, ref)

  /**
   * The behavior spawned a named child with the given behavior and specific props
   */
  def spawned[T](behavior: Behavior[T], childName: String, props: Props): Spawned[T] =
    Spawned(behavior, childName, props)

  /**
   * The behavior spawned a named child with the given behavior and specific props
   */
  def spawned[T](behavior: Behavior[T], childName: String, props: Props, ref: ActorRef[T]): Spawned[T] =
    new Spawned(behavior, childName, props, ref)

  /**
   * The behavior spawned an anonymous child with the given behavior with no specific props
   */
  def spawnedAnonymous[T](behavior: Behavior[T]): SpawnedAnonymous[T] = SpawnedAnonymous(behavior)

  /**
   * The behavior spawned an anonymous child with the given behavior with no specific props
   */
  def spawnedAnonymous[T](behavior: Behavior[T], ref: ActorRef[T]): SpawnedAnonymous[T] =
    new SpawnedAnonymous(behavior, Props.empty, ref)

  /**
   * The behavior spawned an anonymous child with the given behavior with specific props
   */
  def spawnedAnonymous[T](behavior: Behavior[T], props: Props): SpawnedAnonymous[T] = SpawnedAnonymous(behavior, props)

  /**
   * The behavior spawned an anonymous child with the given behavior with specific props
   */
  def spawnedAnonymous[T](behavior: Behavior[T], props: Props, ref: ActorRef[T]): SpawnedAnonymous[T] =
    new SpawnedAnonymous(behavior, props, ref)

  /**
   * The behavior stopped `childName`
   */
  def stopped(childName: String): Stopped = Stopped(childName)

  /**
   * The behavior started watching `other`, through `context.watch(other)`
   */
  def watched[T](other: ActorRef[T]): Watched[T] = Watched(other)

  /**
   * The behavior started watching `other`, through `context.watchWith(other, message)`
   */
  def watchedWith[U, T](other: ActorRef[U], message: T): WatchedWith[U, T] = WatchedWith(other, message)

  /**
   * The behavior stopped watching `other`, through `context.unwatch(other)`
   */
  def unwatched[T](other: ActorRef[T]): Unwatched[T] = Unwatched(other)

  /**
   * The behavior set a new receive timeout, with `message` as timeout notification
   */
  def receiveTimeoutSet[T](d: FiniteDuration, message: T): ReceiveTimeoutSet[T] = ReceiveTimeoutSet(d, message)

  /**
   * The behavior used `context.schedule` to schedule `message` to be sent to `target` after `delay`
   * FIXME what about events scheduled through the scheduler?
   */
  def scheduled[U](delay: FiniteDuration, target: ActorRef[U], message: U): Scheduled[U] =
    Scheduled(delay, target, message)

  /**
   * Used to represent an empty list of effects - in other words, the behavior didn't do anything observable
   */
  def noEffects(): NoEffects = NoEffects

}
