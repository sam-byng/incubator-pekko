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

package org.apache.pekko.persistence.typed

/**
 * Facility to convert snapshots from and to a specialized data model.
 * Can be used when migration from different state types e.g. when migration
 * from Persistent FSM to Typed EventSourcedBehavior.
 *
 * @tparam State The state type of the `EventSourcedBehavior`
 */
trait SnapshotAdapter[State] {

  /**
   * Transform the state to a different type before sending to the journal.
   */
  def toJournal(state: State): Any

  /**
   * Transform the stored state into the current state type.
   * Can be used for migrations from different serialized state types.
   */
  def fromJournal(from: Any): State
}
