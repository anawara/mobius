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
package com.spotify.mobius.android;

import com.spotify.mobius.Connectable;
import com.spotify.mobius.Connection;
import com.spotify.mobius.ConnectionLimitExceededException;
import com.spotify.mobius.android.runners.MainThreadWorkRunner;
import com.spotify.mobius.functions.Consumer;
import javax.annotation.Nonnull;

public class MainThreadConnectable<I, O> implements Connectable<I, O> {
  private final Connectable<I, O> delegate;

  public MainThreadConnectable(Connectable<I, O> delegate) {
    this.delegate = delegate;
  }

  @Nonnull
  @Override
  public Connection<I> connect(Consumer<O> output) throws ConnectionLimitExceededException {
    final MainThreadWorkRunner workRunner = MainThreadWorkRunner.create();
    final Connection<I> delegateConnection = delegate.connect(output);
    return new Connection<I>() {
      @Override
      public void accept(final I value) {
        workRunner.post(
            new Runnable() {
              @Override
              public void run() {
                delegateConnection.accept(value);
              }
            });
      }

      @Override
      public void dispose() {
        workRunner.dispose();
        delegateConnection.dispose();
      }
    };
  }
}
