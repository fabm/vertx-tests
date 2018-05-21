package pt.fabm;

import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStreamReader;
import java.util.*;

public class LooseTests {
    @Test
    public void testClosure() {
        GroovyShell shell = new GroovyShell();

        Script script = shell.parse(new InputStreamReader(getClass().getResourceAsStream("/scripts/test-route.groovy")));

        List<Object[]> argsList = new ArrayList<>();
        Closure closure = new Closure(this) {
            public Object doCall(Object... args) {
                argsList.add(args);
                return null;
            }
        };
        script.getBinding().setVariable("routeJson", closure);
        script.run();

        Map<String, List<String>> map = new HashMap<>();
        map.put("methods", Collections.singletonList("get"));

        Assert.assertEquals("/my/url", argsList.get(0)[0]);
        Assert.assertEquals(map, argsList.get(0)[1]);
        Assert.assertTrue(argsList.get(0)[2] instanceof Closure);
        Assert.assertEquals(3, argsList.get(0).length);

        Assert.assertEquals("another", argsList.get(1)[0]);
        Assert.assertTrue(argsList.get(1)[1] instanceof Closure);
        Assert.assertEquals(2, argsList.get(1).length);
    }

    @Test
    public void testObservableStream() {

        HashMap<Object, Object> options = new HashMap<>();
        options.put("method", Arrays.asList("get", "post"));

        final Observable<String> stringObservable = Observable.just(options)
                .map(map -> map.get("method"))
                .cast(List.class)
                .flatMapIterable(it -> it)
                .cast(String.class);

        Observable<HttpMethod> httpMethodObservable = stringObservable
                .map(strMethod -> HttpMethod.valueOf(strMethod.toUpperCase()));

        for (HttpMethod method : httpMethodObservable.blockingIterable()) {
            System.out.println(method);
        }
    }

    @Test
    public void testJoin() throws InterruptedException {


        final Observable<Integer> observable = Observable.range(1, 5);
        Single<StringJoiner> stringJoiner = observable.map(Object::toString)
                .collect(() -> new StringJoiner(","), StringJoiner::add);

        Assert.assertEquals("1,2,3,4,5", stringJoiner.blockingGet().toString());
    }


}
