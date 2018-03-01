package com.spotify.mobius.rx2;

import static com.spotify.mobius.rx2.EffectHandlers.createMapper;

import com.google.auto.value.AutoValue;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.ObservableTransformer;
import io.reactivex.functions.Function;

@AutoValue
public abstract class FlatMapEffectHandler<F, E> implements ObservableTransformer<F, E> {
  abstract Function<F, Observable<E>> operationFactory();
  abstract Function<Throwable, E> errorMapper();

  @Override
  public ObservableSource<E> apply(Observable<F> upstream) {
    return upstream.flatMap(createMapper(operationFactory(), errorMapper()));
  }

  public static <F, E> Builder<F, E> builder() {
    return new AutoValue_FlatMapEffectHandler.Builder<>();
  }

  @AutoValue.Builder
  public abstract static class Builder<F, E> {

    public abstract Builder<F, E> operationFactory(Function<F, Observable<E>> operationFactory);

    public abstract Builder<F, E> errorMapper(Function<Throwable, E> errorMapper);

    abstract FlatMapEffectHandler<F, E> build();

    public ObservableTransformer<F, E> buildTransformer() {
      return build();
    }
  }
}
