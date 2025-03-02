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

package org.apache.pekko.persistence.snapshot

import scala.concurrent.Future

import org.apache.pekko
import pekko.persistence.{ SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria }

/**
 * Used as default snapshot-store in case no other store was configured.
 *
 * If a [[pekko.persistence.PersistentActor]] calls the [[pekko.persistence.PersistentActor#saveSnapshot]] method,
 * and at the same time does not configure a specific snapshot-store to be used *and* no default snapshot-store
 * is available, then the `NoSnapshotStore` will be used to signal a snapshot store failure.
 */
final class NoSnapshotStore extends SnapshotStore {

  final class NoSnapshotStoreException extends RuntimeException("No snapshot store configured!")

  private val flop: Future[Nothing] =
    Future.failed(new NoSnapshotStoreException)

  private val none: Future[Option[SelectedSnapshot]] =
    Future.successful(None)

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] =
    none

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] =
    flop

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] =
    flop

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] =
    flop

}
