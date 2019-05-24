package com.spotify.mobius.extras.domain;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class D {
  public abstract C c();

  public static D create(C c) {
    return new AutoValue_D(c);
  }
}
