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

package org.apache.pekko.testkit.metrics

import java.util.concurrent.TimeUnit._
import java.util.concurrent.atomic.AtomicLong

import com.codahale.metrics._

/**
 * Specialized "one-shot" Timer.
 * Given a known number of operations performed within a time span (to be measured) it displays the average time one operation took.
 *
 * Please note that this is a *very coarse* estimation; The gain though is that we do not have to perform the counting inside of the measured thing (we can adding in tight loops).
 */
class KnownOpsInTimespanTimer(expectedOps: Long) extends Metric with Counting {

  val startTime = System.nanoTime
  val stopTime = new AtomicLong(0)

  /**
   * Stops the Timer.
   * Can be called multiple times, though only the first call will be taken into account.
   *
   * @return true if this was the first call to `stop`, false otherwise
   */
  def stop(): Boolean = stopTime.compareAndSet(0, System.nanoTime)

  override def getCount: Long = expectedOps

  def elapsedTime: Long = stopTime.get - startTime

  def avgDuration: Long = (elapsedTime.toDouble / expectedOps).toLong

  def opsPerSecond: Double = expectedOps.toDouble / (elapsedTime.toDouble / NANOSECONDS.convert(1, SECONDS))

}
