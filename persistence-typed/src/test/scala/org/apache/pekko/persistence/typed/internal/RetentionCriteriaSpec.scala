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

package org.apache.pekko.persistence.typed.internal

import org.scalatest.TestSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.persistence.typed.scaladsl.RetentionCriteria

class RetentionCriteriaSpec extends TestSuite with Matchers with AnyWordSpecLike with LogCapturing {

  "RetentionCriteria" must {

    "snapshotWhen the sequenceNr matches numberOfEvents" in {
      val criteria = RetentionCriteria.snapshotEvery(3, 2).asInstanceOf[SnapshotCountRetentionCriteriaImpl]
      criteria.snapshotWhen(1) should ===(false)
      criteria.snapshotWhen(2) should ===(false)
      criteria.snapshotWhen(3) should ===(true)
      criteria.snapshotWhen(4) should ===(false)
      criteria.snapshotWhen(6) should ===(true)
      criteria.snapshotWhen(21) should ===(true)
      criteria.snapshotWhen(31) should ===(false)
    }

    "have valid sequenceNr range based on keepNSnapshots" in {
      val criteria = RetentionCriteria.snapshotEvery(3, 2).asInstanceOf[SnapshotCountRetentionCriteriaImpl]
      val expected = List(
        1 -> (0 -> 0),
        3 -> (0 -> 0),
        4 -> (0 -> 0),
        6 -> (0 -> 0),
        7 -> (0 -> 1),
        9 -> (0 -> 3),
        10 -> (0 -> 4),
        12 -> (0 -> 6),
        13 -> (1 -> 7),
        15 -> (3 -> 9),
        18 -> (6 -> 12),
        20 -> (8 -> 14))
      expected.foreach {
        case (seqNr, (lower, upper)) =>
          withClue(s"seqNr=$seqNr:") {
            criteria.deleteUpperSequenceNr(seqNr) should ===(upper)
            criteria.deleteLowerSequenceNr(upper) should ===(lower)
          }
      }
    }

    "require keepNSnapshots >= 1" in {
      RetentionCriteria.snapshotEvery(100, 1) // ok
      intercept[IllegalArgumentException] {
        RetentionCriteria.snapshotEvery(100, 0)
      }
      intercept[IllegalArgumentException] {
        RetentionCriteria.snapshotEvery(100, -1)
      }
    }

    "require numberOfEvents >= 1" in {
      RetentionCriteria.snapshotEvery(1, 2) // ok
      intercept[IllegalArgumentException] {
        RetentionCriteria.snapshotEvery(0, 0)
      }
      intercept[IllegalArgumentException] {
        RetentionCriteria.snapshotEvery(-1, -1)
      }
    }
  }
}
