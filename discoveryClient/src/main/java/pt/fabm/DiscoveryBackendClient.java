package pt.fabm;

import io.reactivex.Observable;
import io.reactivex.SingleObserver;
import io.reactivex.functions.Consumer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.SingleHelper;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.Status;
import io.vertx.servicediscovery.spi.ServiceDiscoveryBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DiscoveryBackendClient implements ServiceDiscoveryBackend {

    private static final String REGISTRATION = "registration";
    private static final String TYPE = "type";
    private static final String LOCATION = "location";
    private static final String METADATA = "metadata";
    private static final String RECORD = "/record";
    private static final String STATUS = "status";
    private static final String NO_RECORD = "no record";
    private WebClient webClient;
    private Record discoveryServer;

    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryBackendClient.class);

    @Override
    public void init(io.vertx.core.Vertx vertx, JsonObject config) {
        webClient = WebClient.create(Vertx.newInstance(vertx));
        String host = System.getProperty("discovery.server.host");
        int port;

        if (host != null) {
            port = Integer.parseInt(System.getProperty("discovery.server.port"));
        } else {
            String homePath = System.getProperty("user.home");
            File file = new File(homePath, ".discoveryServer");
            if (!file.exists() || !file.isDirectory()) {
                return;
            }
            file = new File(file, "server.yml");
            Yaml yaml = new Yaml();
            try (InputStream in = new FileInputStream(file)) {
                Map<String, Object> map = yaml.load(in);
                host = String.class.cast(map.get("host"));
                port = Integer.class.cast(map.get("port"));
            } catch (IOException e) {
                LOGGER.error(
                        MessageFormat.format("Problem trying to read yaml at:{0}", file.getAbsolutePath()),
                        e
                );
                return;
            }
        }

        Record record = new Record();
        record.setRegistration(UUID.randomUUID().toString());
        record.setName("discoveryServer");
        record.setStatus(Status.OUT_OF_SERVICE);
        record.setType("http-endpoint");
        record.setLocation(
                new JsonObject()
                        .put("host", host)
                        .put("port", port)
        );
        discoveryServer = record;
    }

    private static Buffer fromRecordToBuffer(Record record) {
        return Buffer.newInstance(fromRecord(record).toBuffer());
    }

    private static Record fromBufferToRecord(Buffer buffer) {
        return toRecord(buffer.toJsonObject());
    }

    private static Record toRecord(JsonObject jsonObject) {
        Record record = new Record();
        record.setRegistration(jsonObject.getString(REGISTRATION));
        record.setType(jsonObject.getString(TYPE));
        record.setLocation(jsonObject.getJsonObject(LOCATION));
        record.setMetadata(jsonObject.getJsonObject(METADATA));
        record.setStatus(Status.valueOf(jsonObject.getString(STATUS)));
        return record;
    }

    private static JsonObject fromRecord(Record record) {
        return new JsonObject()
                .put(REGISTRATION, record.getRegistration())
                .put(TYPE, record.getType())
                .put(LOCATION, record.getLocation())
                .put(METADATA, record.getMetadata())
                .put(STATUS, record.getStatus());
    }

    @Override
    public void store(Record record, Handler<AsyncResult<Record>> resultHandler) {
        record.setRegistration(UUID.randomUUID().toString());

        SingleObserver<Record> observer = SingleHelper.toObserver(resultHandler);
        final Observable<Buffer> observable = Observable.just(record).map(DiscoveryBackendClient::fromRecordToBuffer);

        webClient.post(
                discoveryServer.getLocation().getInteger("port"),
                discoveryServer.getLocation().getString("host"),
                RECORD).rxSendStream(observable)
                .subscribe(r -> observer.onSuccess(record), observer::onError);
    }

    @Override
    public void remove(Record record, Handler<AsyncResult<Record>> resultHandler) {
        remove(record.getRegistration(), resultHandler);
    }

    @Override
    public void remove(String uuid, Handler<AsyncResult<Record>> resultHandler) {

        Consumer<Throwable> onError = e -> resultHandler.handle(Future.failedFuture(e));
        Consumer<HttpResponse<Buffer>> onSuccess = r -> resultHandler
                .handle(Future.succeededFuture(fromBufferToRecord(r.bodyAsBuffer())));

        webClient.delete(
                discoveryServer.getLocation().getInteger("port"),
                discoveryServer.getLocation().getString("host"),
                RECORD).addQueryParam(REGISTRATION, uuid)
                .rxSend()
                .subscribe(onSuccess, onError);

    }

    @Override
    public void update(Record record, Handler<AsyncResult<Void>> resultHandler) {

        if (discoveryServer == null) {
            resultHandler.handle(Future.failedFuture(NO_RECORD));
            return;
        }
        if (discoveryServer.getRegistration().equals(record.getRegistration())) {
            discoveryServer = record;
            resultHandler.handle(Future.succeededFuture());
            return;
        }


        Consumer<HttpResponse<Buffer>> onSuccess = r -> resultHandler.handle(Future.succeededFuture());
        Consumer<Throwable> onError = e -> resultHandler.handle(Future.failedFuture(e));

        webClient.put(
                discoveryServer.getLocation().getInteger("port"),
                discoveryServer.getLocation().getString("host"),
                RECORD)
                .rxSendStream(Observable.just(fromRecordToBuffer(record)))
                .subscribe(onSuccess, onError);

    }

    @Override
    public void getRecords(Handler<AsyncResult<List<Record>>> resultHandler) {

        if (discoveryServer == null) {
            resultHandler.handle(Future.failedFuture(NO_RECORD));
            return;
        }
        if (discoveryServer.getStatus().equals(Status.OUT_OF_SERVICE)) {
            resultHandler.handle(Future.succeededFuture(Collections.singletonList(discoveryServer)));
            return;
        }

        Consumer<HttpResponse<Buffer>> onSuccess = buffer -> Observable.just(buffer.bodyAsJsonArray())
                .flatMapIterable(item -> item)
                .map(JsonObject.class::cast)
                .map(DiscoveryBackendClient::toRecord)
                .mergeWith(Observable.just(discoveryServer))
                .toList()
                .subscribe(record -> resultHandler.handle(Future.succeededFuture(record)));

        Consumer<Throwable> onError = e -> resultHandler.handle(Future.failedFuture(e));

        webClient.get(
                discoveryServer.getLocation().getInteger("port"),
                discoveryServer.getLocation().getString("host"),
                RECORD + "/all")
                .rxSend().subscribe(onSuccess, onError);
    }

    @Override
    public void getRecord(String uuid, Handler<AsyncResult<Record>> resultHandler) {

        if (discoveryServer == null) {
            resultHandler.handle(Future.failedFuture(NO_RECORD));
            return;
        }
        if (!discoveryServer.getStatus().equals(Status.UP)) {
            resultHandler.handle(Future.succeededFuture(discoveryServer));
            return;
        }
        if (discoveryServer.getRegistration().equals(uuid)) {
            resultHandler.handle(Future.succeededFuture(discoveryServer));
            return;
        }

        Consumer<HttpResponse<Buffer>> onSuccess = buffer -> Observable
                .just(buffer.bodyAsJsonObject())
                .map(DiscoveryBackendClient::toRecord)
                .subscribe(record -> resultHandler.handle(Future.succeededFuture(record)));

        Consumer<Throwable> onError = e -> resultHandler.handle(Future.failedFuture(e));
        webClient.get(
                discoveryServer.getLocation().getInteger("port"),
                discoveryServer.getLocation().getString("host"),
                RECORD)
                .addQueryParam(REGISTRATION, uuid)
                .rxSend().subscribe(onSuccess, onError);
    }
}
