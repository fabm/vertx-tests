package pt.fabm;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
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
import java.util.UUID;

public class DiscoveryBackendClient implements ServiceDiscoveryBackend {

    private static final String RECORD = "/record";
    private WebClient webClient;

    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryBackendClient.class);


    @Override
    public void init(io.vertx.core.Vertx vertx, JsonObject config) {
        final Vertx vertxRX = Vertx.newInstance(vertx);
        final JsonObject serverConfig = config.getJsonObject("server");
        webClient = WebClient.create(vertxRX, new WebClientOptions()
                .setDefaultHost(serverConfig.getString("host"))
                .setDefaultPort(serverConfig.getInteger("port"))
        );
    }

    @Override
    public void store(Record record, Handler<AsyncResult<Record>> resultHandler) {
        record.setRegistration(UUID.randomUUID().toString());

        final Observable<Buffer> observable = Observable.just(record)
                .map(DiscoveryApp::fromRecord)
                .map(JsonObject::toBuffer)
                .map(Buffer::newInstance);

        webClient.post(RECORD).rxSendStream(observable)
                .map(HttpResponse::bodyAsJsonObject)
                .map(DiscoveryApp::toRecord)
                .subscribe(SingleHelper.toObserver(resultHandler));
    }

    @Override
    public void remove(Record record, Handler<AsyncResult<Record>> resultHandler) {
        remove(record.getRegistration(), resultHandler);
    }

    @Override
    public void remove(String uuid, Handler<AsyncResult<Record>> resultHandler) {

        webClient.delete(RECORD).addQueryParam(DiscoveryApp.REGISTRATION, uuid)
                .rxSend()
                .subscribe();
    }

    @Override
    public void update(Record record, Handler<AsyncResult<Void>> resultHandler) {

        Single<Buffer> singleBody = Single.just(record)
                .map(DiscoveryApp::fromRecord)
                .map(JsonObject::toBuffer)
                .map(Buffer::newInstance);

        singleBody.flatMap(webClient.put(RECORD)::rxSendBuffer)
                .toCompletable()
                .subscribe(CompletableHelper.toObserver(resultHandler));

    }

    @Override
    public void getRecords(Handler<AsyncResult<List<Record>>> resultHandler) {

        Single<HttpResponse<Buffer>> response = webClient.get(RECORD + "/all")
                .rxSend()
                .map(r -> {
                    LOGGER.info("getRecords response:{}", r.toString());
                    return r;
                });

        response
                .toObservable()
                .map(HttpResponse::bodyAsJsonArray)
                .flatMapIterable(item -> item)
                .map(JsonObject.class::cast)
                .map(DiscoveryApp::toRecord)
                .toList()
                .subscribe(SingleHelper.toObserver(resultHandler));
    }

    @Override
    public void getRecord(String uuid, Handler<AsyncResult<Record>> resultHandler) {
        webClient.get(RECORD)
                .addQueryParam(DiscoveryApp.REGISTRATION, uuid)
                .rxSend()
                .map(HttpResponse::bodyAsBuffer)
                .map(DiscoveryApp::fromBufferToRecord)
                .subscribe(SingleHelper.toObserver(resultHandler));
    }
}
