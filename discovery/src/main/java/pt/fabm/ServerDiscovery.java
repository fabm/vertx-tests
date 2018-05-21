package pt.fabm;

import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerDiscovery {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerDiscovery.class);
    private Vertx vertx;

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        ServerDiscovery server = new ServerDiscovery();
        server.vertx = vertx;

        LOGGER.info("Starting discovery");
        ServiceDiscovery discovery = ServiceDiscovery.create(vertx);

        discovery.rxGetRecord(record -> record.getName().equals("discoveryServer"), true)
                .subscribe(server::initServer);

        discovery.close();
        LOGGER.info("Ending discovery");
    }

    private void initServer(Record record) {
        DiscoveryRouterManager discoveryRouterManager = new DiscoveryRouterManager(vertx);

        int port = record.getLocation().getInteger("port");
        String location = record.getLocation().getString("host");

        vertx.createHttpServer()
                .requestHandler(discoveryRouterManager)
                .listen(port, location);
    }
}
