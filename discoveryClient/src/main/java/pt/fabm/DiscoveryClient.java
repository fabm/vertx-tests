package pt.fabm;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.servicediscovery.Record;

public class DiscoveryClient {
    private WebClient webClient;

    public DiscoveryClient(HttpClient httpClient) {
        webClient = WebClient.wrap(io.vertx.reactivex.core.http.HttpClient.newInstance(httpClient));
    }

    public Observable<Record> getRecords() {
        return webClient.get("/record/all").rxSend()
                .flatMap(CheckRX::checkResponseOK)
                .map(HttpResponse::bodyAsJsonArray)
                .toObservable()
                .flatMapIterable(jsonArray -> () -> jsonArray.stream().iterator())
                .map(JsonObject.class::cast)
                .map(DiscoveryApp::toRecord);
    }

    public Single<Record> getRecord(String id){
        return webClient.get("/record").addQueryParam(DiscoveryApp.REGISTRATION,id)
                .rxSend()
                .flatMap(CheckRX::checkResponseOK)
                .map(HttpResponse::bodyAsJsonObject)
                .map(DiscoveryApp::toRecord);
    }

    public Completable loadLHR(Buffer buffer){
        return webClient.post("/lhr-load")
                .rxSendStream(Observable.just(buffer))
                .toCompletable();
    }

    public Single<JsonArray> loadLHRs(){
        return webClient.get("/lhrs")
                .rxSend()
                .map(HttpResponse::bodyAsJsonArray);
    }

}
