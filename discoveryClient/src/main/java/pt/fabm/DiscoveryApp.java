package pt.fabm;

import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.functions.Function;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.Status;
import io.vertx.servicediscovery.types.HttpEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;


public abstract class DiscoveryApp {

    public static final String REGISTRATION = "registration";
    private static final String TYPE = "type";
    private static final String LOCATION = "location";
    private static final String METADATA = "metadata";
    private static final String STATUS = "status";

    public static final String PATH_SERVER_DISCOVERY_REPLACER = "${discoveryServer}";
    protected static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryApp.class);
    public static final String SERVER_DISCOVERY = "serverDiscovery";
    private static final String NAME = "name";
    protected Vertx vertx;
    protected ServiceDiscovery serviceDiscovery;
    protected Observable<Record> records;
    protected Single<Path> serviceDiscoveryPath;

    DiscoveryApp(Vertx vertx) {
        this.vertx = vertx;
        init();
    }

    protected void init() {
        Optional<File> opDiscoveryHome = Optional
                .ofNullable(System.getProperty("discovery.home"))
                .map(File::new);

        Maybe<Path> discoveryHomeCustom = Maybe.just(opDiscoveryHome)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(File::exists)
                .filter(File::isDirectory)
                .map(File::toPath);

        Maybe<Path> discoveryHomeDefault = Maybe.just(System.getProperty("user.home"))
                .map(path -> new File(path, ".discoveryServer"))
                .filter(File::exists)
                .filter(File::isDirectory)
                .map(File::toPath);

        Single<Path> sDPath = discoveryHomeCustom
                .switchIfEmpty(discoveryHomeDefault.toSingle());

        Function<Path, String> toInitClient = path -> path.resolve("initClients.json").toAbsolutePath().toString();

        Maybe<Record> propertiesLoaded = loadWithProperties();

        Observable<Record> sdPathLoaded = sDPath
                .flatMap(path -> vertx.fileSystem().rxReadFile(toInitClient.apply(path)))
                .map(Buffer::toJsonArray)
                .toObservable()
                .flatMapIterable(jsonArray -> () -> jsonArray.stream().map(JsonObject.class::cast).iterator())
                .map(DiscoveryApp::toRecord);

        records = propertiesLoaded.isEmpty().map(empty -> !empty).flatMapObservable(loadedFromProperties -> {
            if (loadedFromProperties) {
                return sdPathLoaded
                        .filter(record -> !DiscoveryApp.SERVER_DISCOVERY.equals(record.getName()))
                        .concatWith(propertiesLoaded.toObservable());
            } else {
                return sdPathLoaded;
            }
        }).filter(this::filterInitialRecord)
                .map(this::mapInitialRecord);
    }

    protected abstract boolean filterInitialRecord(Record record);

    protected abstract Record mapInitialRecord(Record record);


    public static Record fromBufferToRecord(Buffer buffer) {
        return toRecord(buffer.toJsonObject());
    }

    public static Record toRecord(JsonObject jsonObject) {
        Record record = new Record();
        record.setRegistration(jsonObject.getString(REGISTRATION));
        record.setName(jsonObject.getString("name"));
        record.setType(jsonObject.getString(TYPE));
        record.setLocation(jsonObject.getJsonObject(LOCATION));
        record.setMetadata(jsonObject.getJsonObject(METADATA));
        Status status = Optional.ofNullable(jsonObject.getString(STATUS))
                .map(Status::valueOf).orElse(Status.OUT_OF_SERVICE);
        record.setStatus(status);
        return record;
    }

    public static JsonObject fromRecord(Record record) {
        return new JsonObject()
                .put(REGISTRATION, record.getRegistration())
                .put(TYPE, record.getType())
                .put(NAME, record.getName())
                .put(LOCATION, record.getLocation())
                .put(METADATA, record.getMetadata())
                .put(STATUS, record.getStatus());
    }

    private Maybe<Record> loadWithProperties() {
        String hostProperty = System.getProperty("discovery.server.host");
        if (hostProperty == null) {
            return Maybe.empty();
        }
        String portProperty = System.getProperty("discovery.server.port");
        if (portProperty == null) {
            return Maybe.empty();
        }
        int intPort = Integer.parseInt(portProperty);
        return Maybe.just(createDiscoveryServerRecord(hostProperty, intPort));
    }

    private Record createDiscoveryServerRecord(String host, int port) {
        Record record = new Record();
        record.setRegistration(UUID.randomUUID().toString());
        record.setName(SERVER_DISCOVERY);
        record.setStatus(Status.OUT_OF_SERVICE);
        record.setType(HttpEndpoint.TYPE);
        record.setLocation(
                new JsonObject()
                        .put("host", host)
                        .put("port", port)
        );

        return record;
    }

    public Observable<Record> getRecords() {
        return records;
    }

}
