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
package com.spotify.mobius.extras;

import com.spotify.mobius.Connectable;
import com.spotify.mobius.Connection;
import com.spotify.mobius.extras.connections.ContramapConnection;
import com.spotify.mobius.extras.connections.DisconnectOnNullDimapConnection;
import com.spotify.mobius.extras.connections.MergeConnectablesConnection;
import com.spotify.mobius.functions.Consumer;
import com.spotify.mobius.functions.Function;
import com.spotify.mobius.internal_util.Preconditions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;

/** Contains utility functions for working with {@link Connectables}. */
public final class Connectables {

  private Connectables() {
    // prevent instantiation
  }

  /**
   * Convert a {@code Connectable<I, O>} to a {@code Connectable<J, O>} by applying the supplied
   * function from J to I for each J received, before passing it on to a {@code Connection<I>}
   * received from the underlying {@code Connectable<I, O>}. This makes {@link Connectable} a <a
   * href="https://hackage.haskell.org/package/contravariant-1.4.1/docs/Data-Functor-Contravariant.html">contravariant
   * functor</a> in functional programming terms.
   *
   * <p>The returned {@link Connectable} doesn't enforce a connection limit, but of course the
   * connection limit of the wrapped {@link Connectable} applies.
   *
   * <p>This is useful for instance if you want your UI to use a subset or a transformed version of
   * the full model used in the {@code MobiusLoop}. As a simplified example, suppose that your model
   * consists of a {@code Long} timestamp that you want to format to a {@code String} before
   * rendering it in the UI. Your UI could then implement {@code Connectable<String, Event>}, and
   * you could create a {@code Function<Long, String>} that does the formatting. The {@link
   * com.spotify.mobius.MobiusLoop} would be outputting {@code Long} models that you need to convert
   * to Strings before they can be accepted by the UI.
   *
   * <pre>
   * public class Formatter {
   *    public static String format(Long timestamp) { ... }
   * }
   *
   * public class MyUi implements Connectable<String, Event> {
   *    // other things in the UI implementation
   *
   *   {@literal @}Override
   *    public Connection<String> connect(Consumer<Event> output) {
   *       return new Connection<String>() {
   *        {@literal @}Override
   *         public void accept(String value) {
   *           // bind the value to the right UI element
   *         }
   *
   *        {@literal @}Override
   *         public void dispose() {
   *           // dispose of any resources, if needed
   *         }
   *       }
   *    }
   * }
   *
   * // Then, to connect the UI to a MobiusLoop.Controller with a Long model:
   * MobiusLoop.Controller<Long, Event> controller = ... ;
   * MyUi myUi = ... ;
   *
   * controller.connect(Connectables.contramap(Formatter::format, myUi));
   * </pre>
   *
   * @param mapper the mapping function to apply
   * @param connectable the underlying connectable
   * @param <I> the type the underlying connectable accepts
   * @param <J> the type the resulting connectable accepts
   * @param <O> the output type; usually the event type
   */
  @Nonnull
  public static <I, J, O> Connectable<J, O> contramap(
      final Function<J, I> mapper, final Connectable<I, O> connectable) {
    return SimpleConnectable.withConnectionFactory(
        new Function<Consumer<O>, Connection<J>>() {
          @Nonnull
          @Override
          public Connection<J> apply(Consumer<O> output) {
            return ContramapConnection.create(mapper, connectable, output);
          }
        });
  }

  /**
   * Convert a {@link Connectable} from B to C to a {@link Connectable} from A to D by converting
   * every incoming A to a B using the provided function. The function can then return either a B or
   * null. On the first B returned, a connection to the provided connectable is established, and
   * that B is passed through. If the aToB function returns null, this connection will be disposed.
   *
   * <p>Whenever the provided connectable dispatches a C through the consumer it receives, that C is
   * converted to a D and is then dispatched to whomever connected to the resulting connectable.
   *
   * <p>The mechanism described is the dimap function from Haskell's <a
   * href="http://hackage.haskell.org/package/profunctors-5.4/docs/Data-Profunctor.html#v:dimap">
   * Profunctor</a> typecalss
   *
   * <pre>
   * class A {
   *     final B b;
   *     A(B b) { this.b = b; }
   *     B b() { return b }
   * }
   *
   * abstract class B {
   *     abstract int x();
   * }
   *
   * abstract class C {
   *     abstract String y();
   * }
   *
   * class D {
   *     final C c;
   *     D(C c) { this.c = c; }
   * }
   *
   * Connectable<B,C> innerConnectable = o -> new Connection() {
   *     public void accept(B b) {
   *         o.accept(new C(b.x().toString()));
   *     }
   *
   *     public void dispose() {
   *
   *     }
   * }
   *
   * Connectable<A,D> outerConnectable = dimap(A::b, D::new, innerConnectable);
   * RecordingConsumer<D> consumer = new RecordingConsumer<>();
   * Connection<A> connection = outerConnectable.connect(consumer);
   * connection.accept(new A(new B(5))); // connects to innerConnectable and forwards value
   * consumer.assertValues(new D(new C("5")))
   *
   * connection.accept(new A(null)); // disconnects from innerConnectable
   * connection.dispose(); // also disconnects from inner connectable
   * </pre>
   *
   * @param aToB mapping from A to B. Results in a connection to provided connectable with the first
   *     B. Disconnects from the connectable if B is null
   * @param cToD mapping from C generated by connectable to D
   * @param connectable connectable being wrapped
   * @param <A> Outer connectable input type
   * @param <B> Inner connectable input type
   * @param <C> Inner connectable output type
   * @param <D> Outer connectable output type
   */
  @Nonnull
  public static <A, B, C, D> Connectable<A, D> dimap(
      final com.spotify.mobius.extras.Function<A, B> aToB,
      final Function<C, D> cToD,
      final Connectable<B, C> connectable) {
    return SimpleConnectable.withConnectionFactory(
        new Function<Consumer<D>, Connection<A>>() {
          @Nonnull
          @Override
          public Connection<A> apply(Consumer<D> output) {
            return DisconnectOnNullDimapConnection.create(aToB, cToD, connectable, output);
          }
        });
  }

  /**
   * Merges two connectables into one. The resulting connectable will invoke both connectables on
   * every item received, and will forward results of both in no particular order.
   */
  @Nonnull
  public static <A, B> Connectable<A, B> merge(
      final Connectable<A, B> fst, final Connectable<A, B> snd) {
    return mergeAll(fst, snd);
  }

  /**
   * Merges all provided connectables into one. The resulting connectable will invoke all
   * connectables on evvery item received, and will forward all results in no particular order.
   */
  @Nonnull
  public static <A, B> Connectable<A, B> mergeAll(
      final Connectable<A, B> fst, final Connectable<A, B> snd, final Connectable<A, B>... cs) {
    return SimpleConnectable.withConnectionFactory(
        new Function<Consumer<B>, Connection<A>>() {
          @Nonnull
          @Override
          public Connection<A> apply(Consumer<B> output) {
            List<Connectable<A, B>> result = new ArrayList<>(cs.length + 2);
            result.add(fst);
            result.add(snd);
            Collections.addAll(result, (Connectable<A, B>[]) Preconditions.checkArrayNoNulls(cs));
            return MergeConnectablesConnection.create(result, output);
          }
        });
  }
}
