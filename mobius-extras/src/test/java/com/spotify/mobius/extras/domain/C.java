package com.spotify.mobius.extras.domain;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class C {
  public abstract String somethingElse();

  public static C create(String somethingElse) {
    return new AutoValue_C(somethingElse);
  }
}
