package pt.fabm;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.CompletableHelper;
import io.vertx.reactivex.SingleHelper;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.spi.ServiceDiscoveryBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class DiscoveryBackendClient implements ServiceDiscoveryBackend {

    private static final String RECORD = "/record";
    private WebClient webClient;
    private Single<Record> serverRecord;

    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryBackendClient.class);

    protected String discoveryPath;

    @Override
    public void init(io.vertx.core.Vertx vertx, JsonObject config) {
        final Vertx vertxRX = Vertx.newInstance(vertx);
        webClient = WebClient.create(vertxRX);
        DiscoveryApp discoveryApp = new DiscoveryAppClient(vertxRX);
        serverRecord = discoveryApp.getRecords().filter(record ->
                Objects.equals(record.getName(), DiscoveryApp.SERVER_DISCOVERY)
        ).firstOrError();
    }

    @Override
    public void store(Record record, Handler<AsyncResult<Record>> resultHandler) {
        record.setRegistration(UUID.randomUUID().toString());

        final Observable<Buffer> observable = Observable.just(record)
                .map(DiscoveryApp::fromRecord)
                .map(JsonObject::toBuffer)
                .map(Buffer::newInstance);

        getDiscoveryServerRecord().map(Record::getLocation).flatMap(location -> webClient.post(
                location.getInteger("port"),
                location.getString("host"),
                RECORD).rxSendStream(observable)
        ).map(HttpResponse::bodyAsJsonObject)
                .map(DiscoveryApp::toRecord)
                .subscribe(SingleHelper.toObserver(resultHandler));
    }

    @Override
    public void remove(Record record, Handler<AsyncResult<Record>> resultHandler) {
        remove(record.getRegistration(), resultHandler);
    }

    protected Single<Record> getDiscoveryServerRecord() {
        return serverRecord;
    }

    @Override
    public void remove(String uuid, Handler<AsyncResult<Record>> resultHandler) {

        getDiscoveryServerRecord().map(Record::getLocation).subscribe(location ->
                webClient.delete(
                        location.getInteger("port"),
                        location.getString("host"),
                        RECORD).addQueryParam(DiscoveryApp.REGISTRATION, uuid)
                        .rxSend()
                        .subscribe()
        );

    }

    @Override
    public void update(Record record, Handler<AsyncResult<Void>> resultHandler) {

        getDiscoveryServerRecord().map(Record::getLocation).flatMap(location -> {
                    final Observable<Buffer> body = Observable.just(record)
                            .map(DiscoveryApp::fromRecord)
                            .map(JsonObject::toBuffer)
                            .map(Buffer::newInstance);
                    return webClient.put(
                            location.getInteger("port"),
                            location.getString("host"),
                            RECORD).rxSendStream(body);
                }
        ).toCompletable()
                .subscribe(CompletableHelper.toObserver(resultHandler));

    }

    @Override
    public void getRecords(Handler<AsyncResult<List<Record>>> resultHandler) {

        final Single<JsonObject> discoveryServer = getDiscoveryServerRecord()
                .map(Record::getLocation)
                .doOnError(error -> LOGGER.error("error getting record"));

        discoveryServer.flatMap(location -> {
                    Single<HttpResponse<Buffer>> response = webClient.get(
                            location.getInteger("port"),
                            location.getString("host"),
                            RECORD + "/all"
                    ).rxSend();

                    return response.map(r -> {
                        LOGGER.info("getRecords response:{}", r.toString());
                        return r;
                    })
                            .toObservable()
                            .map(HttpResponse::bodyAsJsonArray)
                            .flatMapIterable(item -> item)
                            .map(JsonObject.class::cast)
                            .map(DiscoveryApp::toRecord)
                            .toList();
                }
        ).subscribe(SingleHelper.toObserver(resultHandler));
    }

    @Override
    public void getRecord(String uuid, Handler<AsyncResult<Record>> resultHandler) {
        getDiscoveryServerRecord().map(Record::getLocation).flatMap(location -> webClient.get(
                location.getInteger("port"),
                location.getString("host"),
                RECORD)
                .addQueryParam(DiscoveryApp.REGISTRATION, uuid)
                .rxSend()
                .map(HttpResponse::bodyAsBuffer)
                .map(DiscoveryApp::fromBufferToRecord)).subscribe(SingleHelper.toObserver(resultHandler));
    }
}
