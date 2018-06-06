package pt.fabm;

import io.vertx.core.Future;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.http.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerDiscovery extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerDiscovery.class);
    private HttpServer server;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        LOGGER.info("start server discovery");
        String host = config().getString("host");
        int port = config().getInteger("port");
        DiscoveryRouterManager discoveryRouterManager = new DiscoveryRouterManager(vertx);

        vertx.createHttpServer()
                .requestHandler(discoveryRouterManager)
                .rxListen(port, host)
                .subscribe(s -> {
                    this.server = s;
                    startFuture.complete();
                });
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        server.close(handler -> {
            LOGGER.info("stop server discovery");
            stopFuture.complete();
        });
    }
}
