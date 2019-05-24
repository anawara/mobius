package com.spotify.mobius.extras;

import javax.annotation.Nullable;

/** Interface for simple functions that can return null. */
public interface Function<T, R> {
  @Nullable
  R apply(T value);
}
