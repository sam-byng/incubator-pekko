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

package org.apache.pekko.actor.typed.internal

import org.apache.pekko
import pekko.actor.typed.{ DispatcherSelector, MailboxSelector, Props }
import pekko.actor.typed.ActorTags
import pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object PropsImpl {

  /**
   * The empty configuration node, used as a terminator for the internally linked
   * list of each Props.
   */
  case object EmptyProps extends Props {
    override def next = throw new NoSuchElementException("EmptyProps has no next")
    override def withNext(next: Props): Props = next
  }

  final case class DispatcherDefault(next: Props) extends DispatcherSelector {
    override def withNext(next: Props): Props = copy(next = next)
  }
  object DispatcherDefault {
    val empty = DispatcherDefault(EmptyProps)
  }

  final case class DispatcherFromConfig(path: String, next: Props = Props.empty) extends DispatcherSelector {
    override def withNext(next: Props): Props = copy(next = next)
  }

  final case class DispatcherSameAsParent(next: Props) extends DispatcherSelector {
    override def withNext(next: Props): Props = copy(next = next)
  }
  object DispatcherSameAsParent {
    val empty = DispatcherSameAsParent(EmptyProps)
  }

  final case class DefaultMailboxSelector(next: Props = Props.empty) extends MailboxSelector {
    def withNext(next: Props): Props = copy(next = next)
  }
  object DefaultMailboxSelector {
    val empty = DefaultMailboxSelector(EmptyProps)
  }

  final case class BoundedMailboxSelector(capacity: Int, next: Props = Props.empty) extends MailboxSelector {
    def withNext(next: Props): Props = copy(next = next)
  }

  final case class MailboxFromConfigSelector(path: String, next: Props = Props.empty) extends MailboxSelector {
    def withNext(next: Props): Props = copy(next = next)
  }

  final case class ActorTagsImpl(tags: Set[String], next: Props = Props.empty) extends ActorTags {
    if (tags == null)
      throw new IllegalArgumentException("Tags must not be null")
    def withNext(next: Props): Props = copy(next = next)
  }

  object ActorTagsImpl {
    val empty = ActorTagsImpl(Set.empty)
  }

}
