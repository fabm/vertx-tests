package pt.fabm;

import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.reactivex.Observable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.Route;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.Status;
import org.codehaus.groovy.control.CompilationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DiscoveryRouterManager implements Handler<HttpServerRequest> {
    private static final String REGISTRATION = "registration";
    private static final String LOCATION = "location";
    private static final String STATUS = "status";
    private static final String METADATA = "metadata";
    private static final String TYPE = "type";
    private static final String RESULT = "result";
    private Map<String, List<Route>> lrhRoutes = new HashMap<>();
    private Map<String, Record> records = new HashMap<>();
    private Router router;
    private static final String LHR_PATH = Stream
            .of("lhr", "action")
            .collect(
                    Collectors.joining("/", "/", "/")
            );

    private Vertx vertx;
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryRouterManager.class);

    public DiscoveryRouterManager(Vertx vertx) {
        this.vertx = vertx;
        LOGGER.info("start discovery server");
        init();
    }

    private Route createLhrRoute(String id, String subpath) {
        final String path = LHR_PATH + id + '/' + subpath;

        return router.route(path);
    }

    private Route createRecordRoute() {
        return router.route("/record")
                .produces("application/json");
    }

    private Route createRecordAllRoute() {
        return router.route("/record/all")
                .produces("application/json");
    }

    private Buffer okResponse() {
        return Buffer.newInstance(new JsonObject().put(RESULT, "ok").toBuffer());
    }

    private static JsonObject toJsonObject(Record record) {
        return new JsonObject()
                .put(REGISTRATION, record.getRegistration())
                .put(LOCATION, record.getLocation())
                .put(STATUS, record.getStatus())
                .put(METADATA, record.getMetadata())
                .put(TYPE, record.getType());
    }

    private Buffer createOrUpdate(JsonObject jsonObject) {
        Record record = new Record();
        record.setLocation(jsonObject.getJsonObject(LOCATION));
        record.setMetadata(jsonObject.getJsonObject(METADATA));
        record.setType(jsonObject.getString(TYPE));
        record.setName(jsonObject.getString("name"));
        record.setStatus(Status.valueOf(jsonObject.getString(STATUS)));
        record.setRegistration(jsonObject.getString(REGISTRATION));

        records.put(jsonObject.getString(REGISTRATION), record);

        return okResponse();
    }

    private void init() {
        router = Router.router(vertx);

        createRecordRoute()
                .method(HttpMethod.POST)
                .method(HttpMethod.PUT)
                .handler(BodyHandler.create())
                .handler(rc ->
                        Observable.just(rc.getBodyAsJson())
                                .map(this::createOrUpdate)
                                .subscribe(rc.response()::end)
                );

        createRecordRoute()
                .method(HttpMethod.GET)
                .handler(rc ->
                        Observable.just(rc.queryParam(REGISTRATION).get(0))
                                .map(records::get)
                                .map(DiscoveryRouterManager::recordToBuffer)
                                .subscribe(rc.response()::end)
                );

        createRecordRoute()
                .method(HttpMethod.GET)
                .handler(rc ->
                        Observable.just(rc.queryParam(REGISTRATION).get(0))
                                .map(records::get)
                                .map(DiscoveryRouterManager::recordToBuffer)
                                .subscribe(rc.response()::end)
                );

        createRecordAllRoute()
                .method(HttpMethod.GET)
                .handler(rc ->
                        Observable.just(records.values())
                                .flatMapIterable(r -> r)
                                .map(DiscoveryRouterManager::toJsonObject)
                                .collect(JsonArray::new, JsonArray::add)
                                .map(JsonArray::toBuffer)
                                .map(Buffer::newInstance)
                                .subscribe((Consumer<Buffer>) rc.response()::end)
                );

        router.post("/lhr")
                .handler(BodyHandler.create())
                .handler(rc ->
                        Observable.just(rc.getBodyAsJson())
                                .flatMap(DiscoveryRouterManager::loadScript)
                                .subscribe(
                                        script -> scriptHandling(rc, script),
                                        error -> handlingError(rc, error)
                                )
                );

        router.post("/save-script-lhr")
                .handler(BodyHandler.create())
                .handler(rc -> {

                    Consumer<Throwable> errorHandler = e -> handlingError(rc, e);

                    JsonObject jsonObject = rc.getBodyAsJson();
                    Observable<String> fileObs = observableWithNullCheck(
                            jsonObject.getString("file"),
                            "file field must be not empty"
                    ).flatMap(file -> {
                        File currentFile = new File(AppClient.getInstance().getScriptsPath(), file);
                        if (!currentFile.createNewFile() && !currentFile.exists()) {
                            return Observable.error(new IllegalStateException("it's not possible to create the file"));
                        }
                        return Observable.just(currentFile.getAbsolutePath());
                    });

                    Observable<Buffer> bufferObs = observableWithNullCheck(
                            jsonObject.getString("script"),
                            "script field must be not empty"
                    ).map(Buffer::buffer);

                    writeFile(bufferObs, fileObs, errorHandler, () -> handlingResultOk(rc));

                });

        router.get("/lhrs")
                .handler(rc ->
                        Observable.just(lrhRoutes.entrySet())
                                .flatMapIterable(entries -> entries)
                                .map(entry -> {
                                    JsonArray routes = Observable.just(entry.getValue())
                                            .map(Object::toString)
                                            .collect(JsonArray::new, JsonArray::add)
                                            .blockingGet();
                                    return new JsonObject()
                                            .put("id", entry.getKey())
                                            .put("routes", routes);
                                })
                                .collect(JsonArray::new, JsonArray::add)
                                .map(jsonArray -> Buffer.newInstance(jsonArray.toBuffer()))
                                .subscribe(
                                        rc.response()::end,
                                        error -> handlingError(rc, error)
                                )
                );


        router.delete("/lhr")
                .handler(rc ->
                        Observable.just(rc.queryParam("id"))
                                .flatMap(ids -> {
                                    if (lrhRoutes.containsKey(ids.get(0))) {
                                        lrhRoutes.remove(ids.get(0)).forEach(Route::remove);
                                        return Observable.just(new JsonObject().put(RESULT, "ok"));
                                    }
                                    return Observable
                                            .error(
                                                    new NoSuchElementException("no light request handler with ids=" + ids)
                                            );
                                })
                                .subscribe(
                                        jo -> rc
                                                .response()
                                                .end(Buffer.newInstance(jo.toBuffer())),
                                        error -> handlingError(rc, error)
                                )
                );
    }

    private void writeFile(Observable<Buffer> bufferObs, Observable<String> fileObs, Consumer<Throwable> errorHandler, Action onSuccess) {
        bufferObs.subscribe(buffer -> {
            fileObs.subscribe(path -> {
                Action onComplete = () -> {
                    vertx.fileSystem().rxWriteFile(path, buffer).subscribe(onSuccess, errorHandler);
                };

                vertx.fileSystem().rxExists(path)
                        .subscribe(exists -> {
                            if (exists) {
                                onComplete.run();
                            } else {
                                vertx.fileSystem().rxCreateFile(path).subscribe(onComplete, errorHandler);
                            }
                        });
            }, errorHandler);
        }, errorHandler);
    }

    private static <T> Observable<T> observableWithNullCheck(T value, String errorOnNull) {
        if (value != null) {
            return Observable.just(value);
        }
        return Observable.error(new IllegalStateException(errorOnNull));
    }

    private void scriptHandling(RoutingContext rc, Script script) {
        Map vars = script.getBinding().getVariables();
        List<Route> currentRoutesList = new ArrayList<>();
        Closure routeClosure = new Closure(this) {

            public Object doCall(Object... args) {
                String path = String.class.cast(args[0]);
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
                final Closure closure;
                Observable<HttpMethod> methodsObservable;
                if (args.length == 3) {

                    final Observable<Object> listObservable = Observable.just(Map.class.cast(args[1]))
                            .map(map -> map.get("methods"));

                    final Observable<String> stringObservable = listObservable
                            .cast(List.class)
                            .flatMapIterable(it -> it)
                            .cast(String.class);

                    methodsObservable = stringObservable
                            .map(strMethod -> HttpMethod.valueOf(strMethod.toUpperCase()));

                    closure = Closure.class.cast(args[2]);
                } else {
                    methodsObservable = Observable.fromArray(HttpMethod.GET);
                    closure = Closure.class.cast(args[1]);
                }

                Route lrhRoute = createLhrRoute(String.class.cast(vars.get("id")), path);

                for (HttpMethod method : methodsObservable.blockingIterable()) {
                    lrhRoute = lrhRoute.method(method);
                }

                Supplier<String> methodsSupplier = () -> methodsObservable.map(Enum::name)
                        .collect(() -> new StringJoiner(","), StringJoiner::add)
                        .blockingGet()
                        .toString();

                LOGGER.info("route[path: {}, methods:{}]", path, methodsSupplier.get());

                lrhRoute = lrhRoute.handler(lhrRoutingContext -> lhrRoutingContext.response().end(
                        Buffer.newInstance(
                                Json.encodeToBuffer(closure.call(lhrRoutingContext))
                        )
                ));

                currentRoutesList.add(lrhRoute);
                return null;
            }
        };

        vars.put("routeJson", routeClosure);

        script.run();

        String lrhId = String.class.cast(vars.get("id"));
        List<Route> routesList = lrhRoutes.get(lrhId);
        if (routesList != null) {
            routesList.forEach(Route::remove);
            lrhRoutes.computeIfPresent(lrhId, (id, route) -> currentRoutesList);
        } else {
            lrhRoutes.put(lrhId, currentRoutesList);
        }

        LOGGER.info("number of routes size:{}", router.getRoutes().size());

        handlingResultOk(rc);
    }

    private static void handlingResultOk(RoutingContext rc) {
        LOGGER.info("Ok response");
        rc.response().end(
                Buffer.newInstance(new JsonObject().put("result", "ok").toBuffer())
        );
    }

    private static void handlingError(RoutingContext rc, Throwable error) {
        LOGGER.error(error.getMessage(), error);
        LOGGER.info("error");
        rc.response().end(
                Buffer.newInstance(
                        new JsonObject().put(RESULT, "error")
                                .put("message", error.getMessage())
                                .toBuffer()
                )
        );
    }

    private static Observable<Script> loadScript(JsonObject jsonObject) {
        String path = jsonObject.getString("file");
        if (path == null || path.isEmpty()) {
            return Observable.error(new NoSuchElementException("Script path is empty"));
        }
        File file = new File(AppClient.getInstance().getScriptsPath(),path);
        if (!file.exists()) {
            return Observable.error(new FileNotFoundException("Path " + path + " not found"));
        }
        try {
            GroovyShell groovyShell = new GroovyShell();
            return Observable.just(groovyShell.parse(file));
        } catch (CompilationFailedException | IOException e) {
            return Observable.error(e);
        }
    }

    private static Buffer recordToBuffer(Record record) {
        return Buffer.newInstance(toJsonObject(record).toBuffer());
    }

    @Override
    public void handle(HttpServerRequest httpServerRequest) {
        router.accept(httpServerRequest);
    }
}
