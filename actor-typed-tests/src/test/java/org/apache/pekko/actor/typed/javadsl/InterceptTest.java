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

package org.apache.pekko.actor.typed.javadsl;

import org.apache.pekko.actor.testkit.typed.javadsl.LogCapturing;
import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJunitResource;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.*;
import org.apache.pekko.testkit.PekkoSpec;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

public class InterceptTest extends JUnitSuite {

  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource(PekkoSpec.testConf());

  @Rule public final LogCapturing logCapturing = new LogCapturing();

  @Test
  public void interceptMessage() {
    final TestProbe<String> interceptProbe = testKit.createTestProbe();
    BehaviorInterceptor<String, String> interceptor =
        new BehaviorInterceptor<String, String>(String.class) {
          @Override
          public Behavior<String> aroundReceive(
              TypedActorContext<String> ctx, String msg, ReceiveTarget<String> target) {
            interceptProbe.getRef().tell(msg);
            return target.apply(ctx, msg);
          }

          @Override
          public Behavior<String> aroundSignal(
              TypedActorContext<String> ctx, Signal signal, SignalTarget<String> target) {
            return target.apply(ctx, signal);
          }
        };

    final TestProbe<String> probe = testKit.createTestProbe();
    ActorRef<String> ref =
        testKit.spawn(
            Behaviors.intercept(
                () -> interceptor,
                Behaviors.receiveMessage(
                    (String msg) -> {
                      probe.getRef().tell(msg);
                      return Behaviors.same();
                    })));
    ref.tell("Hello");

    interceptProbe.expectMessage("Hello");
    probe.expectMessage("Hello");
  }

  interface Message {}

  static class A implements Message {}

  static class B implements Message {}

  @Test
  public void interceptMessageSubclasses() {
    final TestProbe<Message> interceptProbe = testKit.createTestProbe();
    BehaviorInterceptor<Message, Message> interceptor =
        new BehaviorInterceptor<Message, Message>(Message.class) {

          @Override
          public Behavior<Message> aroundReceive(
              TypedActorContext<Message> ctx, Message msg, ReceiveTarget<Message> target) {
            interceptProbe.getRef().tell(msg);
            return target.apply(ctx, msg);
          }

          @Override
          public Behavior<Message> aroundSignal(
              TypedActorContext<Message> ctx, Signal signal, SignalTarget<Message> target) {
            return target.apply(ctx, signal);
          }
        };

    final TestProbe<Message> probe = testKit.createTestProbe();
    ActorRef<Message> ref =
        testKit.spawn(
            Behaviors.intercept(
                () -> interceptor,
                Behaviors.receiveMessage(
                    (Message msg) -> {
                      probe.getRef().tell(msg);
                      return Behaviors.same();
                    })));
    ref.tell(new A());
    ref.tell(new B());

    interceptProbe.expectMessageClass(A.class);
    probe.expectMessageClass(A.class);
    interceptProbe.expectMessageClass(B.class);
    probe.expectMessageClass(B.class);
  }
}
