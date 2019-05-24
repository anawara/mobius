package com.spotify.mobius.extras;

import static com.spotify.mobius.extras.TestConnectable.State.CONNECTED;
import static com.spotify.mobius.extras.TestConnectable.State.DISPOSED;
import static org.junit.Assert.assertEquals;

import com.spotify.mobius.Connectable;
import com.spotify.mobius.Connection;
import com.spotify.mobius.extras.domain.B;
import com.spotify.mobius.extras.domain.C;
import com.spotify.mobius.test.RecordingConsumer;
import org.junit.Before;
import org.junit.Test;

public class MergeConnectablesTest {
  TestConnectable c1;
  TestConnectable c2;
  RecordingConsumer<C> consumer;
  private Connectable<B, C> merged;
  private Connection<B> connection;

  @Before
  public void setUp() throws Exception {
    c1 = TestConnectable.createWithReversingTransformation();
    c2 = TestConnectable.create(String::toLowerCase);
    consumer = new RecordingConsumer<>();
    merged = Connectables.merge(c1, c2);
  }

  @Test
  public void propagatesConnectionImmediately() {
    connect();
    assertEquals(CONNECTED, c1.state);
    assertEquals(CONNECTED, c2.state);
  }

  @Test
  public void propagatesMultipleConnections() {
    final Connection<B> conn1 = merged.connect(consumer);
    final Connection<B> conn2 = merged.connect(consumer);

    assertEquals(2, c1.connectionsCount);
    assertEquals(2, c2.connectionsCount);
  }

  @Test
  public void propagatesConnectionDisposal() {
    connect();
    connection.dispose();
    assertEquals(DISPOSED, c1.state);
    assertEquals(DISPOSED, c2.state);
  }

  @Test
  public void appliesBothChildrenOnInputAndForwardsOutput() {
    connect();
    connection.accept(B.create("Hello"));
    consumer.assertValues(C.create("olleH"), C.create("hello"));
  }

  private void connect() {
    connection = merged.connect(consumer);
  }
}
