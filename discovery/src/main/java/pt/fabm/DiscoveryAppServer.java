package pt.fabm;

import io.vertx.reactivex.core.Vertx;
import io.vertx.servicediscovery.Record;

import java.util.UUID;

public class DiscoveryAppServer extends DiscoveryApp{
    DiscoveryAppServer(Vertx vertx) {
        super(vertx);
    }

    @Override
    protected boolean filterInitialRecord(Record record) {
        return true;
    }

    @Override
    protected Record mapInitialRecord(Record record) {
        record.setRegistration(UUID.randomUUID().toString());
        return record;
    }
}
