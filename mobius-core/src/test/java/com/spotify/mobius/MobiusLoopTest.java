/*
 * -\-\-
 * Mobius
 * --
 * Copyright (c) 2017-2018 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */
package com.spotify.mobius;

import static com.spotify.mobius.Effects.effects;
import static com.spotify.mobius.internal_util.Throwables.propagate;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.util.concurrent.SettableFuture;
import com.spotify.mobius.MobiusLoopTest.Domain.Crash;
import com.spotify.mobius.MobiusLoopTest.Domain.EventWithCrashingEffect;
import com.spotify.mobius.MobiusLoopTest.Domain.EventWithSafeEffect;
import com.spotify.mobius.MobiusLoopTest.Domain.SafeEffect;
import com.spotify.mobius.MobiusLoopTest.Domain.TestEffect;
import com.spotify.mobius.MobiusLoopTest.Domain.TestEvent;
import com.spotify.mobius.disposables.Disposable;
import com.spotify.mobius.functions.Consumer;
import com.spotify.mobius.runners.ExecutorServiceWorkRunner;
import com.spotify.mobius.runners.ImmediateWorkRunner;
import com.spotify.mobius.runners.WorkRunner;
import com.spotify.mobius.test.RecordingConsumer;
import com.spotify.mobius.test.RecordingModelObserver;
import com.spotify.mobius.test.SimpleConnection;
import com.spotify.mobius.test.TestWorkRunner;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import org.awaitility.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MobiusLoopTest {

  MobiusLoop<String, TestEvent, TestEffect> mobiusLoop;
  MobiusStore<String, TestEvent, TestEffect> mobiusStore;
  Connectable<TestEffect, TestEvent> effectHandler;

  final WorkRunner immediateRunner = new ImmediateWorkRunner();
  WorkRunner backgroundRunner;

  EventSource<TestEvent> eventSource =
      new EventSource<TestEvent>() {
        @Nonnull
        @Override
        public Disposable subscribe(Consumer<TestEvent> eventConsumer) {
          return new Disposable() {
            @Override
            public void dispose() {}
          };
        }
      };

  RecordingModelObserver<String> observer;
  RecordingConsumer<TestEffect> effectObserver;
  Update<String, TestEvent, TestEffect> update;

  @Before
  public void setUp() throws Exception {
    backgroundRunner = new ExecutorServiceWorkRunner(Executors.newSingleThreadExecutor());
    Init<String, TestEffect> init =
        new Init<String, TestEffect>() {
          @Nonnull
          @Override
          public First<String, TestEffect> init(String model) {
            return First.first(model);
          }
        };

    update =
        new Update<String, TestEvent, TestEffect>() {
          @Nonnull
          @Override
          public Next<String, TestEffect> update(String model, TestEvent mobiusEvent) {

            if (mobiusEvent instanceof EventWithCrashingEffect) {
              return Next.next("will crash", effects(new Crash()));
            } else if (mobiusEvent instanceof Domain.EventWithSafeEffect) {
              EventWithSafeEffect event = (EventWithSafeEffect) mobiusEvent;
              return Next.next(
                  model + "->" + mobiusEvent.toString(), effects(new SafeEffect(event.toString())));
            } else {
              return Next.next(model + "->" + mobiusEvent.toString());
            }
          }
        };

    mobiusStore = MobiusStore.create(init, update, "init");

    effectHandler =
        eventConsumer ->
            new SimpleConnection<TestEffect>() {
              @Override
              public void accept(TestEffect effect) {
                if (effectObserver != null) {
                  effectObserver.accept(effect);
                }
                if (effect instanceof Crash) {
                  throw new RuntimeException("Crashing!");
                }
              }
            };

    setupWithEffects(effectHandler, immediateRunner);
  }

  @After
  public void tearDown() throws Exception {
    backgroundRunner.dispose();
  }

  public static class InitializationBehavior extends MobiusLoopTest {
    @Test
    public void shouldProcessInitBeforeEventsFromEffectHandler() throws Exception {
      mobiusStore = MobiusStore.create(m -> First.first("I" + m), update, "init");

      // when an effect handler that emits events before returning the connection
      setupWithEffects(
          new Connectable<TestEffect, TestEvent>() {
            @Nonnull
            @Override
            public Connection<TestEffect> connect(Consumer<TestEvent> output)
                throws ConnectionLimitExceededException {
              output.accept(new TestEvent("1"));

              return new SimpleConnection<TestEffect>() {
                @Override
                public void accept(TestEffect value) {
                  // do nothing
                }
              };
            }
          },
          immediateRunner);

      // in this scenario, the init and the first event get processed before the observer
      // is connected, meaning the 'Iinit' state is never seen
      observer.assertStates("Iinit->1");
    }

    @Test
    public void shouldProcessInitBeforeEventsFromEventSource() throws Exception {
      mobiusStore = MobiusStore.create(m -> First.first("First" + m), update, "init");

      eventSource =
          new EventSource<TestEvent>() {
            @Nonnull
            @Override
            public Disposable subscribe(Consumer<TestEvent> eventConsumer) {
              eventConsumer.accept(new TestEvent("1"));
              return new Disposable() {
                @Override
                public void dispose() {
                  // do nothing
                }
              };
            }
          };

      setupWithEffects(new FakeEffectHandler(), immediateRunner);

      // in this scenario, the init and the first event get processed before the observer
      // is connected, meaning the 'Firstinit' state is never seen
      observer.assertStates("Firstinit->1");
    }
  }

  public static class ObservabilityBehavior extends MobiusLoopTest {
    @Test
    public void shouldTransitionToNextStateBasedOnInput() throws Exception {
      mobiusLoop.dispatchEvent(new TestEvent("first"));
      mobiusLoop.dispatchEvent(new TestEvent("second"));

      observer.assertStates("init", "init->first", "init->first->second");
    }

    @Test
    public void shouldSupportUnregisteringObserver() throws Exception {
      observer = new RecordingModelObserver<>();

      mobiusLoop =
          MobiusLoop.create(
              mobiusStore,
              effectHandler,
              EventSourceConnectable.create(eventSource),
              immediateRunner,
              immediateRunner);

      Disposable unregister = mobiusLoop.observe(observer);

      mobiusLoop.dispatchEvent(new TestEvent("active observer"));
      unregister.dispose();
      mobiusLoop.dispatchEvent(new TestEvent("shouldn't be seen"));

      observer.assertStates("init", "init->active observer");
    }
  }

  public static class BehaviorWithEventSources extends MobiusLoopTest {
    @Test
    public void invokesEventSourceOnEveryModelUpdate() {
      Semaphore s = new Semaphore(0);
      ConnectableEventSource eventSource = new ConnectableEventSource(s);

      mobiusLoop =
          MobiusLoop.create(
              mobiusStore, effectHandler, eventSource, backgroundRunner, immediateRunner);
      observer = new RecordingModelObserver<>();
      mobiusLoop.observe(observer);
      s.acquireUninterruptibly();
      mobiusLoop.dispose();
      observer.assertStates("init", "init->1", "init->1->2", "init->1->2->3");
      assertTrue(eventSource.disposed);
    }

    @Test
    public void disposesOfEventSourceWhenDisposed() {
      Semaphore s = new Semaphore(0);
      ConnectableEventSource eventSource = new ConnectableEventSource(s);
      mobiusLoop =
          MobiusLoop.create(
              mobiusStore, effectHandler, eventSource, backgroundRunner, immediateRunner);

      mobiusLoop.dispose();
      assertTrue(eventSource.disposed);
    }
  }

  public static class BehaviorWithEffectHandlers extends MobiusLoopTest {
    @Test
    public void shouldSurviveEffectPerformerThrowing() throws Exception {
      mobiusLoop.dispatchEvent(new EventWithCrashingEffect());
      mobiusLoop.dispatchEvent(new TestEvent("should happen"));

      observer.assertStates("init", "will crash", "will crash->should happen");
    }

    @Test
    public void shouldSurviveEffectPerformerThrowingMultipleTimes() throws Exception {
      mobiusLoop.dispatchEvent(new EventWithCrashingEffect());
      mobiusLoop.dispatchEvent(new TestEvent("should happen"));
      mobiusLoop.dispatchEvent(new EventWithCrashingEffect());
      mobiusLoop.dispatchEvent(new TestEvent("should happen, too"));

      observer.assertStates(
          "init",
          "will crash",
          "will crash->should happen",
          "will crash",
          "will crash->should happen, too");
    }

    @Test
    public void shouldSupportEffectsThatGenerateEvents() throws Exception {
      setupWithEffects(
          eventConsumer ->
              new SimpleConnection<TestEffect>() {
                @Override
                public void accept(TestEffect effect) {
                  eventConsumer.accept(new TestEvent(effect.toString()));
                }
              },
          immediateRunner);

      mobiusLoop.dispatchEvent(new EventWithSafeEffect("hi"));

      observer.assertStates("init", "init->hi", "init->hi->effecthi");
    }

    @Test
    public void shouldOrderStateChangesCorrectlyWhenEffectsAreSlow() throws Exception {
      final SettableFuture<TestEvent> future = SettableFuture.create();

      setupWithEffects(
          eventConsumer ->
              new SimpleConnection<TestEffect>() {
                @Override
                public void accept(TestEffect effect) {
                  try {
                    eventConsumer.accept(future.get());

                  } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                  }
                }
              },
          backgroundRunner);

      mobiusLoop.dispatchEvent(new EventWithSafeEffect("1"));
      mobiusLoop.dispatchEvent(new TestEvent("2"));

      await().atMost(Duration.ONE_SECOND).until(() -> observer.valueCount() >= 3);

      future.set(new TestEvent("3"));

      await().atMost(Duration.ONE_SECOND).until(() -> observer.valueCount() >= 4);
      observer.assertStates("init", "init->1", "init->1->2", "init->1->2->3");
    }

    @Test
    public void shouldSupportHandlingEffectsWhenOneEffectNeverCompletes() throws Exception {
      setupWithEffects(
          eventConsumer ->
              new SimpleConnection<TestEffect>() {
                @Override
                public void accept(TestEffect effect) {
                  if (effect instanceof SafeEffect) {
                    if (((SafeEffect) effect).id.equals("1")) {
                      try {
                        // Rough approximation of waiting infinite amount of time.
                        Thread.sleep(2000);
                      } catch (InterruptedException e) {
                        // ignored.
                      }
                      return;
                    }
                  }

                  eventConsumer.accept(new TestEvent(effect.toString()));
                }
              },
          new ExecutorServiceWorkRunner(Executors.newFixedThreadPool(2)));

      // the effectHandler associated with "1" should never happen
      mobiusLoop.dispatchEvent(new EventWithSafeEffect("1"));
      mobiusLoop.dispatchEvent(new TestEvent("2"));
      mobiusLoop.dispatchEvent(new EventWithSafeEffect("3"));

      await().atMost(Duration.FIVE_SECONDS).until(() -> observer.valueCount() >= 5);

      observer.assertStates(
          "init", "init->1", "init->1->2", "init->1->2->3", "init->1->2->3->effect3");
    }

    @Test
    public void shouldPerformEffectFromInit() throws Exception {
      Init<String, TestEffect> init =
          new Init<String, TestEffect>() {
            @Nonnull
            @Override
            public First<String, TestEffect> init(String model) {
              return First.first(model, effects(new SafeEffect("frominit")));
            }
          };

      Update<String, TestEvent, TestEffect> update =
          new Update<String, TestEvent, TestEffect>() {
            @Nonnull
            @Override
            public Next<String, TestEffect> update(String model, TestEvent event) {
              return Next.next(model + "->" + event.toString());
            }
          };

      mobiusStore = MobiusStore.create(init, update, "init");
      TestWorkRunner testWorkRunner = new TestWorkRunner();

      setupWithEffects(
          eventConsumer ->
              new SimpleConnection<TestEffect>() {
                @Override
                public void accept(TestEffect effect) {
                  eventConsumer.accept(new TestEvent(effect.toString()));
                }
              },
          testWorkRunner);

      observer.waitForChange(100);
      testWorkRunner.runAll();

      observer.assertStates("init", "init->effectfrominit");
    }
  }

  public static class DisposalBehavior extends MobiusLoopTest {
    @Test(expected = IllegalStateException.class)
    public void dispatchingEventsAfterDisposalThrowsException() throws Exception {
      mobiusLoop.dispose();
      mobiusLoop.dispatchEvent(new TestEvent("2"));
    }

    @Test
    public void disposingTheLoopDisposesTheWorkRunners() throws Exception {
      TestWorkRunner eventRunner = new TestWorkRunner();
      TestWorkRunner effectRunner = new TestWorkRunner();

      mobiusLoop =
          MobiusLoop.create(
              mobiusStore,
              effectHandler,
              EventSourceConnectable.create(eventSource),
              eventRunner,
              effectRunner);

      mobiusLoop.dispose();

      assertTrue("expecting event WorkRunner to be disposed", eventRunner.isDisposed());
      assertTrue("expecting effect WorkRunner to be disposed", effectRunner.isDisposed());
    }

    @Test
    public void shouldThrowForEventSourceEventsAfterDispose() throws Exception {
      FakeEventSource<TestEvent> eventSource = new FakeEventSource<>();

      mobiusLoop =
          MobiusLoop.create(
              mobiusStore,
              effectHandler,
              EventSourceConnectable.create(eventSource),
              immediateRunner,
              immediateRunner);

      observer = new RecordingModelObserver<>(); // to clear out the init from the previous setup
      mobiusLoop.observe(observer);

      eventSource.emit(new EventWithSafeEffect("one"));
      mobiusLoop.dispose();

      assertThatThrownBy(() -> eventSource.emit(new EventWithSafeEffect("two")))
          .isInstanceOf(IllegalStateException.class);

      observer.assertStates("init", "init->one");
    }

    @Test
    public void shouldThrowForEffectHandlerEventsAfterDispose() throws Exception {
      final FakeEffectHandler effectHandler = new FakeEffectHandler();

      setupWithEffects(effectHandler, immediateRunner);

      effectHandler.emitEvent(new EventWithSafeEffect("good one"));

      mobiusLoop.dispose();

      assertThatThrownBy(() -> effectHandler.emitEvent(new EventWithSafeEffect("bad one")))
          .isInstanceOf(IllegalStateException.class);

      observer.assertStates("init", "init->good one");
    }

    @Test
    public void eventsFromEventSourceDuringDisposeAreIgnored() throws Exception {
      // Events emitted by the event source during dispose should be ignored.

      AtomicBoolean updateWasCalled = new AtomicBoolean();

      final MobiusLoop.Builder<String, TestEvent, TestEffect> builder =
          Mobius.loop(
              (model, event) -> {
                updateWasCalled.set(true);
                return Next.noChange();
              },
              effectHandler);

      builder
          .eventSource(new EmitDuringDisposeEventSource(new TestEvent("bar")))
          .startFrom("foo")
          .dispose();

      assertFalse(updateWasCalled.get());
    }

    @Test
    public void eventsFromEffectHandlerDuringDisposeAreIgnored() throws Exception {
      // Events emitted by the effect handler during dispose should be ignored.

      AtomicBoolean updateWasCalled = new AtomicBoolean();

      final MobiusLoop.Builder<String, TestEvent, TestEffect> builder =
          Mobius.loop(
              (model, event) -> {
                updateWasCalled.set(true);
                return Next.noChange();
              },
              new EmitDuringDisposeEffectHandler());

      builder.startFrom("foo").dispose();

      assertFalse(updateWasCalled.get());
    }

    @Test
    public void disposingLoopWhileInitIsRunningDoesNotEmitNewState() throws Exception {
      // Model changes emitted from the init function during dispose should be ignored.

      // This test will start a loop and wait until (using the initRequested semaphore) the runnable
      // that runs Init is posted to the event runner. The init function will then be blocked using
      // the initLock semaphore. At this point, we proceed to add the observer then dispose of the
      // loop. The loop is setup with an event source that returns a disposable that will unlock
      // init when it is disposed. So when we dispose of the loop, that will unblock init as part of
      // the disposal procedure. The test then waits until the init runnable has completed running.
      // Completion of the init runnable means:
      // a) init has returned a First
      // b) that first has been unpacked and the model has been set on the store
      // c) that model has been passed back to the loop to be emitted to any state observers
      // Since we're in the process of disposing of the loop, we should see no states in our observer
      observer = new RecordingModelObserver<>();
      Semaphore initLock = new Semaphore(0);
      Semaphore initRequested = new Semaphore(0);
      Semaphore initFinished = new Semaphore(0);

      final Update<String, TestEvent, TestEffect> update = (model, event) -> Next.noChange();
      final MobiusLoop.Builder<String, TestEvent, TestEffect> builder =
          Mobius.loop(update, effectHandler)
              .init(
                  m -> {
                    initLock.acquireUninterruptibly();
                    return First.first(m);
                  })
              .eventRunner(
                  () ->
                      new WorkRunner() {
                        @Override
                        public void post(Runnable runnable) {
                          backgroundRunner.post(
                              () -> {
                                initRequested.release();
                                runnable.run();
                                initFinished.release();
                              });
                        }

                        @Override
                        public void dispose() {
                          backgroundRunner.dispose();
                        }
                      });

      mobiusLoop = builder.startFrom("foo");
      initRequested.acquireUninterruptibly();
      mobiusLoop.observe(observer);
      initLock.release();
      mobiusLoop.dispose();
      initFinished.acquireUninterruptibly(1);
      observer.assertStates();
    }

    @Test
    public void disposingLoopBeforeInitRunsIgnoresModelFromInit() throws Exception {
      // Model changes emitted from the init function after dispose should be ignored.
      // This test sets up the following scenario:
      // 1. The loop is created and initialized on a separate thread
      // 2. The loop is configured with an event runner that will block before executing the init function
      // 3. The test will then dispose of the loop
      // 4. Once the loop is disposed, the test will proceed to unblock the initialization runnable
      // 5. Once the initialization is completed, the test will proceed to examine the observer

      observer = new RecordingModelObserver<>();

      Semaphore awaitInitExecutionRequest = new Semaphore(0);
      Semaphore blockInitExecution = new Semaphore(0);
      Semaphore initExecutionCompleted = new Semaphore(0);

      final Update<String, TestEvent, TestEffect> update = (model, event) -> Next.noChange();
      final MobiusLoop.Builder<String, TestEvent, TestEffect> builder =
          Mobius.loop(update, effectHandler)
              .eventRunner(
                  () ->
                      new WorkRunner() {
                        @Override
                        public void post(Runnable runnable) {
                          backgroundRunner.post(
                              () -> {
                                awaitInitExecutionRequest.release();
                                blockInitExecution.acquireUninterruptibly();
                                runnable.run();
                                initExecutionCompleted.release();
                              });
                        }

                        @Override
                        public void dispose() {
                          backgroundRunner.dispose();
                        }
                      });

      new Thread(() -> mobiusLoop = builder.startFrom("foo")).start();

      awaitInitExecutionRequest.acquireUninterruptibly();

      mobiusLoop.observe(observer);
      mobiusLoop.dispose();

      blockInitExecution.release();
      initExecutionCompleted.acquireUninterruptibly();

      observer.assertStates();
    }

    @Test
    public void modelsFromUpdateDuringDisposeAreIgnored() throws Exception {
      // Model changes emitted from the update function during dispose should be ignored.

      observer = new RecordingModelObserver<>();
      Semaphore lock = new Semaphore(0);

      final Update<String, TestEvent, TestEffect> update =
          (model, event) -> {
            lock.acquireUninterruptibly();
            return Next.next("baz");
          };

      final MobiusLoop.Builder<String, TestEvent, TestEffect> builder =
          Mobius.loop(update, effectHandler)
              .eventRunner(
                  () -> InitImmediatelyThenUpdateConcurrentlyWorkRunner.create(backgroundRunner));

      mobiusLoop = builder.startFrom("foo");
      mobiusLoop.observe(observer);

      mobiusLoop.dispatchEvent(new TestEvent("bar"));
      releaseLockAfterDelay(lock, 30);
      mobiusLoop.dispose();

      observer.assertStates("foo");
    }

    @Test
    public void effectsFromUpdateDuringDisposeAreIgnored() throws Exception {
      // Effects emitted from the update function during dispose should be ignored.

      effectObserver = new RecordingConsumer<>();
      Semaphore lock = new Semaphore(0);

      final MobiusLoop.Builder<String, TestEvent, TestEffect> builder =
          Mobius.loop(
              (model, event) -> {
                lock.acquireUninterruptibly();
                return Next.dispatch(effects(new SafeEffect("baz")));
              },
              effectHandler);

      mobiusLoop = builder.startFrom("foo");

      mobiusLoop.dispatchEvent(new TestEvent("bar"));
      releaseLockAfterDelay(lock, 30);
      mobiusLoop.dispose();

      effectObserver.assertValues();
    }

    @Test
    public void shouldSupportDisposingInObserver() throws Exception {
      RecordingModelObserver<String> secondObserver = new RecordingModelObserver<>();

      // ensure there are some observers to iterate over, and that one of them modifies the
      // observer list.
      // ConcurrentModificationException only triggered if three observers added, for some reason
      Disposable disposable = mobiusLoop.observe(s -> {});
      mobiusLoop.observe(
          s -> {
            if (s.contains("heyho")) {
              disposable.dispose();
            }
          });
      mobiusLoop.observe(s -> {});
      mobiusLoop.observe(secondObserver);

      mobiusLoop.dispatchEvent(new TestEvent("heyho"));

      secondObserver.assertStates("init", "init->heyho");
    }

    @Test
    public void shouldDisposeMultiThreadedEventSourceSafely() throws Exception {
      // event source that just pushes stuff every X ms on a thread.

      RecurringEventSource source = new RecurringEventSource();

      final MobiusLoop.Builder<String, TestEvent, TestEffect> builder =
          Mobius.loop(update, effectHandler).eventSource(source);

      Random random = new Random();

      for (int i = 0; i < 100; i++) {
        mobiusLoop = builder.startFrom("foo");

        Thread.sleep(random.nextInt(30));

        mobiusLoop.dispose();
      }
    }
  }

  protected void setupWithEffects(
      Connectable<TestEffect, TestEvent> effectHandler, WorkRunner effectRunner) {
    observer = new RecordingModelObserver<>();

    mobiusLoop =
        MobiusLoop.create(
            mobiusStore,
            effectHandler,
            EventSourceConnectable.create(eventSource),
            immediateRunner,
            effectRunner);

    mobiusLoop.observe(observer);
  }

  private static void releaseLockAfterDelay(Semaphore lock, int delay) {
    new Thread(
            () -> {
              try {
                Thread.sleep(delay);
              } catch (InterruptedException e) {
                throw propagate(e);
              }

              lock.release();
            })
        .start();
  }

  static class Domain {
    static class TestEvent {

      private final String name;

      TestEvent(String name) {
        this.name = name;
      }

      @Override
      public String toString() {
        return name;
      }
    }

    static class EventWithCrashingEffect extends TestEvent {

      EventWithCrashingEffect() {
        super("crash!");
      }
    }

    static class EventWithSafeEffect extends TestEvent {

      private EventWithSafeEffect(String id) {
        super(id);
      }
    }

    interface TestEffect {}

    static class Crash implements TestEffect {}

    static class SafeEffect implements TestEffect {

      private final String id;

      private SafeEffect(String id) {
        this.id = id;
      }

      @Override
      public String toString() {
        return "effect" + id;
      }
    }
  }

  private static class FakeEffectHandler implements Connectable<TestEffect, TestEvent> {

    private volatile Consumer<TestEvent> eventConsumer = null;

    void emitEvent(TestEvent event) {
      // throws NPE if not connected; that's OK
      eventConsumer.accept(event);
    }

    @Nonnull
    @Override
    public Connection<TestEffect> connect(Consumer<TestEvent> output)
        throws ConnectionLimitExceededException {
      if (eventConsumer != null) {
        throw new ConnectionLimitExceededException();
      }

      eventConsumer = output;

      return new Connection<TestEffect>() {
        @Override
        public void accept(TestEffect value) {
          // do nothing
        }

        @Override
        public void dispose() {
          // do nothing
        }
      };
    }
  }

  private static class EmitDuringDisposeEventSource implements EventSource<TestEvent> {

    private final TestEvent event;

    public EmitDuringDisposeEventSource(TestEvent event) {
      this.event = event;
    }

    @Nonnull
    @Override
    public Disposable subscribe(Consumer<TestEvent> eventConsumer) {
      return () -> eventConsumer.accept(event);
    }
  }

  private static class EmitDuringDisposeEffectHandler
      implements Connectable<TestEffect, TestEvent> {

    @Nonnull
    @Override
    public Connection<TestEffect> connect(Consumer<TestEvent> eventConsumer) {
      return new Connection<TestEffect>() {
        @Override
        public void accept(TestEffect value) {
          // ignored
        }

        @Override
        public void dispose() {
          eventConsumer.accept(new TestEvent("bar"));
        }
      };
    }
  }

  private static class RecurringEventSource implements EventSource<TestEvent> {

    final SettableFuture<Void> completion = SettableFuture.create();

    @Nonnull
    @Override
    public Disposable subscribe(Consumer<TestEvent> eventConsumer) {
      if (completion.isDone()) {
        try {
          completion.get(); // should throw since the only way it can complete is exceptionally
        } catch (InterruptedException | ExecutionException e) {
          throw new RuntimeException("handle this", e);
        }
      }

      final Generator generator = new Generator(eventConsumer);

      Thread t = new Thread(generator);
      t.start();

      return () -> {
        generator.generate = false;
        try {
          t.join();
        } catch (InterruptedException e) {
          throw propagate(e);
        }
      };
    }

    private class Generator implements Runnable {

      private volatile boolean generate = true;
      private final Consumer<TestEvent> consumer;

      private Generator(Consumer<TestEvent> consumer) {
        this.consumer = consumer;
      }

      @Override
      public void run() {
        while (generate) {
          try {
            consumer.accept(new TestEvent("hi"));
            Thread.sleep(15);
          } catch (Exception e) {
            completion.setException(e);
          }
        }
      }
    }
  }

  private static class InitImmediatelyThenUpdateConcurrentlyWorkRunner implements WorkRunner {
    private final WorkRunner delegate;

    private boolean ranOnce;

    private InitImmediatelyThenUpdateConcurrentlyWorkRunner(WorkRunner delegate) {
      this.delegate = delegate;
    }

    public static WorkRunner create(WorkRunner eventRunner) {
      return new InitImmediatelyThenUpdateConcurrentlyWorkRunner(eventRunner);
    }

    @Override
    public synchronized void post(Runnable runnable) {
      if (ranOnce) {
        delegate.post(runnable);
        return;
      }

      ranOnce = true;
      runnable.run();
    }

    @Override
    public void dispose() {
      delegate.dispose();
    }
  }

  private static class ConnectableEventSource implements Connectable<String, TestEvent> {

    private final Semaphore lock;
    boolean disposed;

    ConnectableEventSource(Semaphore lock) {
      this.lock = lock;
    }

    @Nonnull
    @Override
    public Connection<String> connect(Consumer<TestEvent> output)
        throws ConnectionLimitExceededException {
      return new Connection<String>() {

        int count;

        @Override
        public void accept(String value) {
          if (++count > 3) {
            lock.release();
            return;
          }
          output.accept(new TestEvent(Integer.toString(count)));
        }

        @Override
        public void dispose() {
          disposed = true;
        }
      };
    }
  }
}
