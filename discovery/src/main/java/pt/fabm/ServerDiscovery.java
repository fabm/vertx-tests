package pt.fabm;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerDiscovery {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerDiscovery.class);

    private Vertx vertx;
    private DiscoveryApp discoveryApp;

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        ServerDiscovery server = new ServerDiscovery();

        server.init(vertx);
    }

    protected Completable init(Vertx vertx) {
        LOGGER.info("Starting discovery");

        this.vertx = vertx;
        ServiceDiscovery discovery = ServiceDiscovery.create(vertx);
        discoveryApp = new DiscoveryAppServer(vertx);

        Single<Record> discoveryServerRecord = discoveryApp.getRecords()
                .filter(record -> DiscoveryApp.SERVER_DISCOVERY.equals(record.getName()))
                .firstOrError();

        return discoveryServerRecord.flatMap(this::initServer).map(server -> {
            discovery.close();
            LOGGER.info("Ending discovery");
            return server;
        }).toCompletable();
    }

    private Single<HttpServer> initServer(Record record) {
        DiscoveryRouterManager discoveryRouterManager = new DiscoveryRouterManager(vertx,discoveryApp.getRecords());

        int port = record.getLocation().getInteger("port");
        String location = record.getLocation().getString("host");

        return vertx.createHttpServer()
                .requestHandler(discoveryRouterManager)
                .rxListen(port, location);
    }
}
