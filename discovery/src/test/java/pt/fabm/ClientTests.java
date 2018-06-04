package pt.fabm;

import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.Record;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

public class ClientTests {
    @Test
    public void testServer() throws InterruptedException {

        Vertx vertx = Vertx.vertx();

        Path path = Paths.get(getClass().getResource("/initClients.json").getFile()).getParent();
        System.setProperty("discovery.home", path.toString());

        ServerDiscovery serverDiscovery = new ServerDiscovery();
        serverDiscovery.init(vertx).blockingAwait();

        ServiceDiscovery serviceDiscovery = ServiceDiscovery.create(vertx);
        List<Record> records = serviceDiscovery.rxGetRecords(record -> true, true).blockingGet();
        Assert.assertEquals(3, records.size());

        Single<Record> serverRecord = serviceDiscovery.rxGetRecord(record ->
                        Objects.equals(record.getName(), DiscoveryApp.SERVER_DISCOVERY),
                true
        );
        Assert.assertEquals(DiscoveryApp.SERVER_DISCOVERY, serverRecord.blockingGet().getName());

        DiscoveryClient discoveryClient = serviceDiscovery.getReference(serverRecord.blockingGet()).getAs(DiscoveryClient.class);
        records = discoveryClient.getRecords().toList().blockingGet();

        Assert.assertEquals(3, records.size());

        Single<Record> singleRecord = discoveryClient.getRecord(records.get(0).getRegistration());
        Assert.assertEquals(DiscoveryApp.SERVER_DISCOVERY, serverRecord.blockingGet().getName());

        vertx.fileSystem()
                .rxReadFile(getClass().getResource("/scripts/test-route.groovy").getFile())
                .map(discoveryClient::loadLHR)
                .blockingGet().blockingAwait();

        JsonArray arrays = discoveryClient.loadLHRs().blockingGet();

        Assert.assertEquals(1,arrays.size());
        final JsonObject routeScript = arrays.getJsonObject(0);
        Assert.assertEquals("mock-unit-test-1", routeScript.getString("id"));
        Assert.assertEquals("mock-unit-test-1", routeScript.getString("id"));

        final JsonArray routes = routeScript.getJsonArray("routes");

        Assert.assertEquals(1,routes.size());

        System.out.println(routes.getString(0));
    }

}
