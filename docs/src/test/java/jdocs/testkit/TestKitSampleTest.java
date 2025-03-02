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

package jdocs.testkit;

// #fullsample
import jdocs.AbstractJavaTest;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.AbstractActor;

import java.time.Duration;

public class TestKitSampleTest extends AbstractJavaTest {

  public static class SomeActor extends AbstractActor {
    ActorRef target = null;

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .matchEquals(
              "hello",
              message -> {
                getSender().tell("world", getSelf());
                if (target != null) target.forward(message, getContext());
              })
          .match(
              ActorRef.class,
              actorRef -> {
                target = actorRef;
                getSender().tell("done", getSelf());
              })
          .build();
    }
  }

  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create();
  }

  @AfterClass
  public static void teardown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  @Test
  public void testIt() {
    /*
     * Wrap the whole test procedure within a testkit constructor
     * if you want to receive actor replies or use Within(), etc.
     */
    new TestKit(system) {
      {
        final Props props = Props.create(SomeActor.class);
        final ActorRef subject = system.actorOf(props);

        // can also use JavaTestKit “from the outside”
        final TestKit probe = new TestKit(system);
        // “inject” the probe by passing it to the test subject
        // like a real resource would be passed in production
        subject.tell(probe.getRef(), getRef());
        // await the correct response
        expectMsg(Duration.ofSeconds(1), "done");

        // the run() method needs to finish within 3 seconds
        within(
            Duration.ofSeconds(3),
            () -> {
              subject.tell("hello", getRef());

              // This is a demo: would normally use expectMsgEquals().
              // Wait time is bounded by 3-second deadline above.
              awaitCond(probe::msgAvailable);

              // response must have been enqueued to us before probe
              expectMsg(Duration.ZERO, "world");
              // check that the probe we injected earlier got the msg
              probe.expectMsg(Duration.ZERO, "hello");
              Assert.assertEquals(getRef(), probe.getLastSender());

              // Will wait for the rest of the 3 seconds
              expectNoMessage();
              return null;
            });
      }
    };
  }
}
// #fullsample
