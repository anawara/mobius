package com.spotify.mobius.extras.domain;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

@AutoValue
public abstract class A {
  @Nullable
  public abstract B b();

  public static A create(@Nullable B b) {
    return new AutoValue_A(b);
  }
}
