import io.reactivex.*;
import io.vertx.core.json.JsonArray;
import io.vertx.reactivex.MaybeHelper;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.servicediscovery.Record;
import org.junit.Assert;
import org.junit.Test;
import pt.fabm.AppClient;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class TestSerialization {
    @Test
    public void serialisationRecords() {

        Vertx vertx = Vertx.vertx();

        Single<List<Record>> singleRecordList = vertx.fileSystem().rxReadFile(getClass().getResource("/initClients.json").getFile())
                .toObservable()
                .map(Buffer::getDelegate)
                .map(JsonArray::new)
                .flatMapIterable(it ->
                        () -> IntStream.range(0, it.size()).mapToObj(it::getJsonObject).iterator()
                )
                .map(AppClient::toRecord)
                .toList();

        List<Record> records = singleRecordList.blockingGet();
        Assert.assertEquals(2, records.size());

        Record record = records.get(0);
        Assert.assertEquals("db-jetty-app", record.getName());
        Assert.assertEquals("jdbc://localhost:9042", record.getLocation().getString("url"));
        Assert.assertEquals("org.h2.Driver", record.getMetadata().getString("class"));
        Assert.assertEquals("jdbc", record.getType());

        record = records.get(1);
        Assert.assertEquals("mock-db-jetty-app", record.getName());
        Assert.assertEquals("jdbc:mock-driver:${discoveryServer}/mock-scripts/jetty-app-jdbc.groovy", record.getLocation().getString("url"));
        Assert.assertEquals("pt.fabm.MockDriver", record.getMetadata().getString("class"));
        Assert.assertEquals("jdbc", record.getType());


    }

    @Test
    public void testCache() {
        Vertx vertx = Vertx.vertx();

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
