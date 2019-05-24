package com.spotify.mobius.extras;

import static com.spotify.mobius.extras.TestConnectable.State.CONNECTED;
import static com.spotify.mobius.extras.TestConnectable.State.DISPOSED;

import com.spotify.mobius.Connectable;
import com.spotify.mobius.Connection;
import com.spotify.mobius.extras.domain.B;
import com.spotify.mobius.extras.domain.C;
import com.spotify.mobius.functions.Consumer;
import com.spotify.mobius.functions.Function;
import javax.annotation.Nonnull;

class TestConnectable implements Connectable<B, C> {

  enum State {
    DISPOSED,
    CONNECTED
  }

  private final com.spotify.mobius.functions.Function<String, String> transform;

  State state;
  int connectionsCount;

  public static TestConnectable create(Function<String, String> transform) {
    return new TestConnectable(transform);
  }

  public static TestConnectable createWithReversingTransformation() {
    return create(value -> new StringBuilder(value).reverse().toString());
  }

  TestConnectable(Function<String, String> transform) {
    this.transform = transform;
  }

  @Nonnull
  @Override
  public Connection<B> connect(Consumer<C> output) {
    state = CONNECTED;
    connectionsCount++;
    return new Connection<B>() {
      @Override
      public void accept(B value) {
        output.accept(C.create(transform.apply(value.something())));
      }

      @Override
      public void dispose() {
        state = DISPOSED;
        connectionsCount--;
      }
    };
  }
}
