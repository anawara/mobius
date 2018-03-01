package com.spotify.mobius.rx2;

import io.reactivex.Observable;
import io.reactivex.ObservableTransformer;
import io.reactivex.functions.Function;
import io.reactivex.observers.TestObserver;
import io.reactivex.subjects.PublishSubject;
import java.util.Collections;
import javax.annotation.Nonnull;
import org.junit.Test;

public class EffectHandlersTest {

  public static class FlatMapTests {

    Function<String, Observable<String>> operation =
        new Function<String, Observable<String>>() {
          int count = 0;

          @Nonnull
          @Override
          public Observable<String> apply(String value) {
            String result = String.join(" ", Collections.nCopies(count + 1, value));
            return count++ % 2 == 0
                ? Observable.just(result)
                : Observable.<String>error(new RuntimeException(result));
          }
        };

    @Test
    public void flatMaps() {

      ObservableTransformer<String, String> transformer = FlatMapEffectHandler.<String, String, String>builder()
          .operationFactory(operation)
          .itemsMapper(String::toUpperCase)
          .errorMapper(Throwable::getMessage)
          .buildTransformer();

      PublishSubject<String> upstream = PublishSubject.create();
      TestObserver<String> observer = new TestObserver<>();
      upstream.compose(transformer).subscribe(observer);

      observer.assertSubscribed();
      upstream.onNext("hello");
      upstream.onNext("world!");
      upstream.onNext("what up");

      observer.assertValues("HELLO", "world! world!", "WHAT UP WHAT UP WHAT UP");
    }
  }
}
