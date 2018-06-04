import io.reactivex.*;
import io.vertx.reactivex.MaybeHelper;
import io.vertx.reactivex.core.Vertx;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class TestSerialization {

    @Test
    public void testCache() {

        AtomicInteger atomicInteger = new AtomicInteger();
        Single<String> single = Single.<String>create(source -> {
            atomicInteger.getAndIncrement();
            source.onSuccess("test");
        }).cache();

        single.subscribe();
        single.subscribe();
        single.subscribe();
        single.subscribe();
        Assert.assertEquals(1, atomicInteger.get());

        atomicInteger.set(0);
        single = Single.create(source -> {
            atomicInteger.getAndIncrement();
            source.onSuccess("test");
        });

        single.subscribe();
        single.subscribe();
        single.subscribe();
        single.subscribe();
        Assert.assertEquals(4, atomicInteger.get());
    }

    @Test
    public void testMaybes() {

        MaybeSource<String> maybeSourceString = source -> source.onSuccess("alternative");
        MaybeSource<Integer> maybeSourceInteger = source -> source.onSuccess(0);

        Maybe<String> orDefaultString = Maybe.<String>create(MaybeEmitter::onComplete).switchIfEmpty(maybeSourceString);
        Maybe<Integer> orDefaultInt = Maybe.create(MaybeEmitter::onComplete);

        Assert.assertEquals("alternative", orDefaultString.blockingGet());
        Assert.assertEquals((Integer) 0, orDefaultInt.switchIfEmpty(maybeSourceInteger).blockingGet());

        orDefaultInt = orDefaultInt.filter(e -> !e.equals(0));

        Assert.assertTrue(orDefaultInt.isEmpty().blockingGet());
        Assert.assertFalse(orDefaultString.isEmpty().blockingGet());

        Maybe<Boolean> trueMaybe = Maybe.just(true);
        Maybe<Boolean> falseMaybe = Maybe.just(false);


        Maybe<Boolean> result = trueMaybe
                .toObservable()
                .concatWith(falseMaybe.toObservable())
                .reduce((tm, fm) -> tm || fm);

        Assert.assertTrue(result.blockingGet());

        result = trueMaybe
                .toObservable()
                .concatWith(falseMaybe.toObservable())
                .reduce((tm, fm) -> tm && fm);

        Assert.assertFalse(result.blockingGet());
    }

    @Test
    public void testFilterWithoutExecuteDefer() {

        AtomicInteger atomicInteger = new AtomicInteger(0);
        Observable<String> undeferObservable = Observable.just("undefer");

        Observable<String> deferObservable = Single.defer(() -> {
            atomicInteger.incrementAndGet();
            return Single.just("defer");
        }).toObservable();

        Single<String> filtered = undeferObservable
                .concatWith(deferObservable)
                .filter(e -> e.startsWith("un"))
                .firstOrError();

        Assert.assertEquals("undefer", filtered.blockingGet());
        Assert.assertEquals(0, atomicInteger.get());

        filtered = deferObservable
                .concatWith(undeferObservable)
                .filter(e -> e.startsWith("un"))
                .firstOrError();

        Assert.assertEquals("undefer", filtered.blockingGet());
        Assert.assertEquals(1, atomicInteger.get());


        atomicInteger.set(0);
        Maybe.<Void>empty().subscribe(MaybeHelper.toObserver(v -> atomicInteger.incrementAndGet()));

        Assert.assertEquals(1, atomicInteger.get());

    }


}
