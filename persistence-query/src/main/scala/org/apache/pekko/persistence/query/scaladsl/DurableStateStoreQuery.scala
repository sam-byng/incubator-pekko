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

package org.apache.pekko.persistence.query.scaladsl

import org.apache.pekko
import pekko.NotUsed
import pekko.persistence.query.DurableStateChange
import pekko.persistence.state.scaladsl.DurableStateStore
import pekko.persistence.query.Offset
import pekko.stream.scaladsl.Source

/**
 * Query API for reading durable state objects.
 *
 * For Java API see [[pekko.persistence.query.javadsl.DurableStateStoreQuery]].
 */
trait DurableStateStoreQuery[A] extends DurableStateStore[A] {

  /**
   * Get a source of the most recent changes made to objects with the given tag since the passed in offset.
   *
   * Note that this only returns the most recent change to each object, if an object has been updated multiple times
   * since the offset, only the most recent of those changes will be part of the stream.
   *
   * This will return changes that occurred up to when the `Source` returned by this call is materialized. Changes to
   * objects made since materialization are not guaranteed to be included in the results.
   *
   * The [[DurableStateChange]] elements can be [[pekko.persistence.query.UpdatedDurableState]] or
   * [[pekko.persistence.query.DeletedDurableState]].
   *
   * @param tag The tag to get changes for.
   * @param offset The offset to get changes since. Must either be [[pekko.persistence.query.NoOffset]] to get
   *               changes since the beginning of time, or an offset that has been previously returned by this query.
   *               Any other offsets are invalid.
   * @return A source of change in state.
   */
  def currentChanges(tag: String, offset: Offset): Source[DurableStateChange[A], NotUsed]

  /**
   * Get a source of the most recent changes made to objects of the given tag since the passed in offset.
   *
   * The returned source will never terminate, it effectively watches for changes to the objects and emits changes as
   * they happen.
   *
   * Not all changes that occur are guaranteed to be emitted, this call only guarantees that eventually, the most
   * recent change for each object since the offset will be emitted. In particular, multiple updates to a given object
   * in quick succession are likely to be skipped, with only the last update resulting in a change from this
   * source.
   *
   * The [[DurableStateChange]] elements can be [[pekko.persistence.query.UpdatedDurableState]] or
   * [[pekko.persistence.query.DeletedDurableState]].
   *
   * @param tag The tag to get changes for.
   * @param offset The offset to get changes since. Must either be [[pekko.persistence.query.NoOffset]] to get
   *               changes since the beginning of time, or an offset that has been previously returned by this query.
   *               Any other offsets are invalid.
   * @return A source of change in state.
   */
  def changes(tag: String, offset: Offset): Source[DurableStateChange[A], NotUsed]
}
