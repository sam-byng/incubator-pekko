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

package org.apache.pekko.remote.transport.netty

import java.nio.channels.ClosedChannelException

import scala.util.control.NonFatal

import org.jboss.netty.channel._

import org.apache.pekko
import pekko.PekkoException
import pekko.util.unused

/**
 * INTERNAL API
 */
private[netty] trait NettyHelpers {

  protected def onConnect(@unused ctx: ChannelHandlerContext, @unused e: ChannelStateEvent): Unit = ()

  protected def onDisconnect(@unused ctx: ChannelHandlerContext, @unused e: ChannelStateEvent): Unit = ()

  protected def onOpen(@unused ctx: ChannelHandlerContext, @unused e: ChannelStateEvent): Unit = ()

  protected def onMessage(@unused ctx: ChannelHandlerContext, @unused e: MessageEvent): Unit = ()

  protected def onException(@unused ctx: ChannelHandlerContext, @unused e: ExceptionEvent): Unit = ()

  final protected def transformException(ctx: ChannelHandlerContext, ev: ExceptionEvent): Unit = {
    val cause = if (ev.getCause ne null) ev.getCause else new PekkoException("Unknown cause")
    cause match {
      case _: ClosedChannelException => // Ignore
      case null | NonFatal(_)        => onException(ctx, ev)
      case e: Throwable              => throw e // Rethrow fatals
    }
  }
}

/**
 * INTERNAL API
 */
private[netty] trait NettyServerHelpers extends SimpleChannelUpstreamHandler with NettyHelpers {

  final override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent): Unit = {
    super.messageReceived(ctx, e)
    onMessage(ctx, e)
  }

  final override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent): Unit = transformException(ctx, e)

  final override def channelConnected(ctx: ChannelHandlerContext, e: ChannelStateEvent): Unit = {
    super.channelConnected(ctx, e)
    onConnect(ctx, e)
  }

  final override def channelOpen(ctx: ChannelHandlerContext, e: ChannelStateEvent): Unit = {
    super.channelOpen(ctx, e)
    onOpen(ctx, e)
  }

  final override def channelDisconnected(ctx: ChannelHandlerContext, e: ChannelStateEvent): Unit = {
    super.channelDisconnected(ctx, e)
    onDisconnect(ctx, e)
  }
}

/**
 * INTERNAL API
 */
private[netty] trait NettyClientHelpers extends SimpleChannelHandler with NettyHelpers {
  final override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent): Unit = {
    super.messageReceived(ctx, e)
    onMessage(ctx, e)
  }

  final override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent): Unit = transformException(ctx, e)

  final override def channelConnected(ctx: ChannelHandlerContext, e: ChannelStateEvent): Unit = {
    super.channelConnected(ctx, e)
    onConnect(ctx, e)
  }

  final override def channelOpen(ctx: ChannelHandlerContext, e: ChannelStateEvent): Unit = {
    super.channelOpen(ctx, e)
    onOpen(ctx, e)
  }

  final override def channelDisconnected(ctx: ChannelHandlerContext, e: ChannelStateEvent): Unit = {
    super.channelDisconnected(ctx, e)
    onDisconnect(ctx, e)
  }
}
