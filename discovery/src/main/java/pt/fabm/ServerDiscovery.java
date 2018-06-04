package pt.fabm;

import io.reactivex.Completable;
import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerDiscovery {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerDiscovery.class);

    public static void main(String[] args) {

        ServerDiscovery server = new ServerDiscovery();

        String host = args[0];
        String strPort = args[1];
        int port = Integer.parseInt(strPort);
        Vertx vertx = Vertx.vertx();
        server.init(vertx, host, port).blockingAwait();
    }

    public Completable init(Vertx vertx, String host, int port) {
        LOGGER.info("Starting discovery");

        DiscoveryRouterManager discoveryRouterManager = new DiscoveryRouterManager(vertx);

        return vertx.createHttpServer()
                .requestHandler(discoveryRouterManager)
                .rxListen(port, host).toCompletable();
    }

}
