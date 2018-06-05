package pt.fabm;


import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscoveryOptions;
import io.vertx.servicediscovery.Status;
import io.vertx.servicediscovery.types.HttpEndpoint;
import org.junit.Assert;
import org.junit.Test;

public class ClientTests {
    @Test
    public void testServer() {

        Vertx vertx = Vertx.vertx();

        new ServerDiscovery().init(vertx, "localhost", 7000).blockingAwait();

        ServiceDiscovery serviceDiscovery = ServiceDiscovery.create(vertx, new ServiceDiscoveryOptions()
                .setBackendConfiguration(new JsonObject()
                        .put("server", new JsonObject()
                                .put("host", "localhost")
                                .put("port", 7000)
                        )
                )
        );

        Record recordExpected = new Record();
        recordExpected.setName("record-name");
        recordExpected.setRegistration("reg");
        recordExpected.setStatus(Status.OUT_OF_SERVICE);
        recordExpected.setLocation(new JsonObject()
                .put("host", "localhost")
                .put("port", "3000")
        );
        recordExpected.setType(HttpEndpoint.TYPE);

        serviceDiscovery.rxPublish(recordExpected).toCompletable().blockingAwait();
        Record recordReturned = serviceDiscovery.rxGetRecord(r -> true, true).blockingGet();

        Assert.assertEquals(recordExpected, recordExpected);

        recordExpected.setStatus(Status.UP);
        serviceDiscovery.rxUpdate(recordExpected).toCompletable().blockingAwait();
        recordReturned = serviceDiscovery.rxGetRecord(r->true,true).blockingGet();

        Assert.assertEquals(recordExpected,recordReturned);
    }

}
