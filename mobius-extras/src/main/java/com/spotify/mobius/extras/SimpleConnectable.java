package com.spotify.mobius.extras;

import com.spotify.mobius.Connectable;
import com.spotify.mobius.Connection;
import com.spotify.mobius.ConnectionLimitExceededException;
import com.spotify.mobius.functions.Consumer;
import com.spotify.mobius.functions.Function;
import javax.annotation.Nonnull;

/**
 * A simple {@link Connectable} implementation that delegates creation of its {@link Connection} to
 * the specified factory.
 */
public final class SimpleConnectable<T, R> implements Connectable<T, R> {

  private final Function<Consumer<R>, Connection<T>> factory;

  public static <T, R> Connectable<T, R> withConnectionFactory(
      Function<Consumer<R>, Connection<T>> factory) {
    return new SimpleConnectable<>(factory);
  }

  private SimpleConnectable(Function<Consumer<R>, Connection<T>> factory) {
    this.factory = factory;
  }

  @Nonnull
  @Override
  public Connection<T> connect(Consumer<R> output) throws ConnectionLimitExceededException {
    return factory.apply(output);
  }
}
