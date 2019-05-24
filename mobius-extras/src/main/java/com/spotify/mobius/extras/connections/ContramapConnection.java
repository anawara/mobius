package com.spotify.mobius.extras.connections;

import com.spotify.mobius.Connectable;
import com.spotify.mobius.Connection;
import com.spotify.mobius.functions.Consumer;
import com.spotify.mobius.functions.Function;

/** A {@link Connection} implementation that does contravariant mapping. */
public class ContramapConnection<A, B, C> implements Connection<B> {

  public static <A, B, C> Connection<B> create(
      Function<B, A> mapper, Connectable<A, C> connectable, Consumer<C> output) {
    return new ContramapConnection<>(mapper, connectable, output);
  }

  private final Function<B, A> mapper;
  private final Connectable<A, C> connectable;
  private final Consumer<C> output;
  private final Connection<A> delegateConnection;

  private ContramapConnection(
      Function<B, A> mapper, Connectable<A, C> connectable, Consumer<C> output) {
    this.mapper = mapper;
    this.connectable = connectable;
    this.output = output;

    delegateConnection = this.connectable.connect(this.output);
  }

  @Override
  public void accept(B j) {
    final A a = mapper.apply(j);
    if (a != null) {
      delegateConnection.accept(a);
    }
  }

  @Override
  public void dispose() {
    delegateConnection.dispose();
  }
}
