package pt.fabm;
import io.vertx.core.AbstractVerticle;

public class MyServiceMock extends AbstractVerticle {
    @Override
    public void start() throws Exception {
        System.out.println("init:"+deploymentID());
    }

    @Override
    public void stop() throws Exception {
        System.out.println("closing:"+deploymentID());
    }
}
