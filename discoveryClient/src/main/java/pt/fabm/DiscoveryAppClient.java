package pt.fabm;

import io.vertx.reactivex.core.Vertx;
import io.vertx.servicediscovery.Record;

import java.util.Objects;

public class DiscoveryAppClient extends DiscoveryApp{

    public DiscoveryAppClient(Vertx vertx) {
        super(vertx);
    }

    @Override
    protected boolean filterInitialRecord(Record record) {
        return Objects.equals(DiscoveryApp.SERVER_DISCOVERY,record.getName());
    }

    @Override
    protected Record mapInitialRecord(Record record) {
        return record;
    }
}
