package com.spotify.mobius.rx2;

import io.reactivex.Observable;
import io.reactivex.functions.Function;

public class EffectHandlers<F, T, E> {

  private final Function<F, Observable<T>> operationFactory;
  private final Function<T, E> itemsMapper;
  private final Function<Throwable, E> errorMapper;

  public EffectHandlers(
      Function<F, Observable<T>> operationFactory,
      Function<T, E> itemsMapper,
      Function<Throwable, E> errorMapper) {
    this.operationFactory = operationFactory;
    this.itemsMapper = itemsMapper;
    this.errorMapper = errorMapper;
  }
//
//  public ObservableTransformer<F, E> buildFlatMapTransformer() {
//    return new com.spotify.mobius.rx2.EffectHandlers.FlatMapEffectHandler(map(operationFactory, itemsMapper, errorMapper));
//  }
//
//  public ObservableTransformer<F, E> buildSwitchMapTransformer() {
//    return new SwitchMapEffectHandler<>(map(operationFactory, itemsMapper, errorMapper));
//  }
//
//  public ObservableTransformer<F, E> buildConcatMapTransformer() {
//    return new ConcatMapEffectHandler<>(map(operationFactory, itemsMapper, errorMapper));
//  }
//
//  public ObservableTransformer<F, E> buildConcatMapEagerTransformer() {
//    return new ConcatMapEagerEffectHandler<>(map(operationFactory, itemsMapper, errorMapper));
//  }

//  private static class FlatMapEffectHandler<F, E> implements ObservableTransformer<F, E> {
//
//    private final Function<F, Observable<E>> mappingFunction;
//
//    private FlatMapEffectHandler(Function<F, Observable<E>> mappingFunction) {
//      this.mappingFunction = mappingFunction;
//    }
//
//    @Override
//    public ObservableSource<E> apply(Observable<F> upstream) {
//      return upstream.flatMap(mappingFunction);
//    }
//  }
//
//  private static class SwitchMapEffectHandler<F, E> implements ObservableTransformer<F, E> {
//
//    private final Function<F, Observable<E>> mappingFunction;
//
//    private SwitchMapEffectHandler(Function<F, Observable<E>> mappingFunction) {
//      this.mappingFunction = mappingFunction;
//    }
//
//    @Override
//    public ObservableSource<E> apply(Observable<F> upstream) {
//      return upstream.switchMap(mappingFunction);
//    }
//  }
//
//  private static class ConcatMapEffectHandler<F, E> implements ObservableTransformer<F, E> {
//
//    private final Function<F, Observable<E>> mappingFunction;
//
//    private ConcatMapEffectHandler(Function<F, Observable<E>> mappingFunction) {
//      this.mappingFunction = mappingFunction;
//    }
//
//    @Override
//    public ObservableSource<E> apply(Observable<F> upstream) {
//      return upstream.concatMap(mappingFunction);
//    }
//  }
//
//  private static class ConcatMapEagerEffectHandler<F, E> implements ObservableTransformer<F, E> {
//
//    private final Function<F, Observable<E>> mappingFunction;
//
//    private ConcatMapEagerEffectHandler(Function<F, Observable<E>> mappingFunction) {
//      this.mappingFunction = mappingFunction;
//    }
//
//    @Override
//    public ObservableSource<E> apply(Observable<F> upstream) {
//      return upstream.concatMapEager(mappingFunction);
//    }
//  }

  static <F, T, E> Function<F, Observable<E>> createMapper(
      final Function<F, Observable<E>> operationFactory,
      final Function<Throwable, E> errorMapper) {
    return new Function<F, Observable<E>>() {
      @Override
      public Observable<E> apply(F f) throws Exception {
        return operationFactory.apply(f).onErrorReturn(errorMapper);
      }
    };
  }
}
