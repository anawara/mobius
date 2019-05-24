package com.spotify.mobius.extras.connections;

import com.spotify.mobius.Connectable;
import com.spotify.mobius.Connection;
import com.spotify.mobius.extras.Function;
import com.spotify.mobius.functions.Consumer;

/** A {@link Connection} that implements dimap. */
public class DisconnectOnNullDimapConnection<A, B, C, D> implements Connection<A> {

  public static <A, B, C, D> Connection<A> create(
      Function<A, B> aToB,
      com.spotify.mobius.functions.Function<C, D> cToD,
      Connectable<B, C> connectable,
      Consumer<D> output) {
    return new DisconnectOnNullDimapConnection<>(aToB, cToD, connectable, output);
  }

  private final Function<A, B> aToB;
  private final com.spotify.mobius.functions.Function<C, D> cToD;
  private final Connectable<B, C> connectable;
  private final Consumer<D> output;
  private Connection<B> currentDelegate;

  private DisconnectOnNullDimapConnection(
      Function<A, B> aToB,
      com.spotify.mobius.functions.Function<C, D> cToD,
      Connectable<B, C> connectable,
      Consumer<D> output) {
    this.aToB = aToB;
    this.cToD = cToD;
    this.connectable = connectable;
    this.output = output;
  }

  @Override
  public void accept(A a) {
    B b = aToB.apply(a);
    if (b != null) {
      if (currentDelegate == null) {
        currentDelegate =
            connectable.connect(
                new Consumer<C>() {
                  @Override
                  public void accept(C c) {
                    final D d = cToD.apply(c);
                    output.accept(d);
                  }
                });
      }
      currentDelegate.accept(b);
    } else if (currentDelegate != null) {
      currentDelegate.dispose();
      currentDelegate = null;
    }
  }

  @Override
  public void dispose() {
    if (currentDelegate != null) {
      currentDelegate.dispose();
    }
  }
}
