package pt.fabm;

import com.google.common.collect.ImmutableMap;
import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
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
import org.codehaus.groovy.control.CompilationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final String NAME = "name";
    private Map<String, List<Route>> lrhRoutes = new HashMap<>();
    private Map<String, JsonObject> records = new HashMap<>();
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

    private Buffer createOrUpdate(JsonObject jsonObject) {
        ImmutableMap<String, Object> map = ImmutableMap.<String, Object>builder()
                .put(LOCATION, jsonObject.getJsonObject(LOCATION))
                .put(METADATA, jsonObject.getJsonObject(METADATA))
                .put(TYPE, jsonObject.getString(TYPE))
                .put(NAME, jsonObject.getString(NAME))
                .put(STATUS, jsonObject.getString(STATUS))
                .put(REGISTRATION, jsonObject.getString(REGISTRATION))
                .build();

        map.forEach((k, v) -> Optional.ofNullable(v).orElseThrow(() -> new IllegalStateException("Null not allowed in:" + k)));

        records.put(jsonObject.getString(REGISTRATION), jsonObject);

        return Buffer.newInstance(jsonObject.toBuffer());
    }

    private void init() {
        router = Router.router(vertx);

        createRecordRoute()
                .method(HttpMethod.POST)
                .method(HttpMethod.PUT)
                .handler(BodyHandler.create())
                .handler(this::routeCreateOrUpdateRecord);

        createRecordRoute()
                .method(HttpMethod.GET)
                .handler(this::routeGetRecord);

        createRecordAllRoute()
                .method(HttpMethod.GET)
                .handler(this::routeAllRecords);

        router.post("/lhr-load")
                .handler(BodyHandler.create())
                .handler(this::routeLhrLoad);


        router.get("/lhrs")
                .handler(this::routeAllLHRs);

        router.delete("/lhr")
                .handler(this::routeDeleteLhr);
    }

    private Disposable routeCreateOrUpdateRecord(RoutingContext rc) {
        return Observable.just(rc.getBodyAsJson())
                .map(this::createOrUpdate)
                .subscribe(rc.response()::end);
    }

    private Disposable routeGetRecord(RoutingContext rc) {
        return Observable.just(rc.queryParam(REGISTRATION).get(0))
                .map(records::get)
                .map(JsonObject::toBuffer)
                .map(Buffer::newInstance)
                .subscribe(rc.response()::end);
    }

    private Disposable routeDeleteLhr(RoutingContext rc) {
        return Observable.just(rc.queryParam("id"))
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
                );
    }

    private static JsonObject routeToJsonObject(Route route) {
        return new JsonObject()
                .put("path", route.getPath())
                .put("asString", route.toString());
    }

    private Disposable routeAllLHRs(RoutingContext rc) {
        return Observable.just(lrhRoutes.entrySet())
                .flatMapIterable(entries -> entries)
                .flatMap((Map.Entry<String, List<Route>> entry) -> {
                    Single<JsonArray> routes = Observable.fromIterable(entry.getValue())
                            .map(DiscoveryRouterManager::routeToJsonObject)
                            .collect(JsonArray::new, JsonArray::add);

                    return routes.map(routesArray -> new JsonObject()
                            .put("id", entry.getKey())
                            .put("routes", routesArray)
                    ).toObservable();
                })
                .collect(JsonArray::new, JsonArray::add)
                .map(JsonArray::toBuffer)
                .map(Buffer::newInstance)
                .subscribe(
                        rc.response()::end,
                        error -> handlingError(rc, error)
                );
    }

    private void routeAllRecords(RoutingContext rc) {
        LOGGER.info("records number {}", records.size());
        Observable.just(records.values())
                .flatMapIterable(r -> r)
                .collect(JsonArray::new, JsonArray::add)
                .map(JsonArray::toBuffer)
                .map(Buffer::newInstance)
                .subscribe((Consumer<Buffer>) rc.response()::end);
    }

    private Disposable routeLhrLoad(RoutingContext rc) {
        return Observable.just(rc.getBodyAsString("UTF-8"))
                .map(Optional::ofNullable)
                .map(op -> op.filter(e -> !e.isEmpty()))
                .map(Optional::get)
                .flatMap(DiscoveryRouterManager::loadScript)
                .subscribe(
                        script -> scriptHandling(rc, script),
                        error -> handlingError(rc, error)
                );
    }

    private void scriptHandling(RoutingContext rc, Script script) {
        Map vars = script.getBinding().getVariables();
        List<Route> currentRoutesList = new ArrayList<>();
        Closure routeClosure = getClosure(vars, currentRoutesList);

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

    private Closure getClosure(Map vars, List<Route> currentRoutesList) {
        return new Closure(this) {

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
    }

    private static void handlingResultOk(RoutingContext rc) {
        LOGGER.info("Ok response");
        rc.response().end(
                Buffer.newInstance(new JsonObject().put(RESULT, "ok").toBuffer())
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

    private static Observable<Script> loadScript(String content) {
        try {
            GroovyShell groovyShell = new GroovyShell();
            return Observable.just(groovyShell.parse(content));
        } catch (CompilationFailedException e) {
            return Observable.error(e);
        }
    }

    private static Observable<Script> loadScript(Buffer buffer) {
        try {
            GroovyShell groovyShell = new GroovyShell();
            return Observable.just(groovyShell.parse(buffer.toString()));
        } catch (CompilationFailedException e) {
            return Observable.error(e);
        }
    }

    @Override
    public void handle(HttpServerRequest httpServerRequest) {
        router.accept(httpServerRequest);
    }
}
