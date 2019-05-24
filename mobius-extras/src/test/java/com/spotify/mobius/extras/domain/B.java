package com.spotify.mobius.extras.domain;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class B {
  public abstract String something();

  public static B create(String something) {
    return new AutoValue_B(something);
  }
}
