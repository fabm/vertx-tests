package pt.fabm;


import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.junit.Test;

public class ClientTests {
    @Test
    public void testServer() throws InterruptedException {

        Vertx vertx = Vertx.vertx();

        ServerDiscovery.main(new String[]{"localhost", "7000"});

        WebClient webClient = WebClient.create(
                vertx,
                new WebClientOptions().setDefaultPort(7000).setDefaultHost("localhost")
        );

        Single<JsonArray> response = webClient.get("/record/all")
                .rxSend()
                .map(HttpResponse::bodyAsJsonArray);

        System.out.println(response.blockingGet().size());
    }

}
