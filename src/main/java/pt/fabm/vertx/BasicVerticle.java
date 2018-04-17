package pt.fabm.vertx;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.maven.MavenVerticleFactory;
import io.vertx.maven.ResolverOptions;

public class BasicVerticle extends AbstractVerticle {

    @Override
    public void start() throws Exception {
        vertx.registerVerticleFactory(new MavenVerticleFactory(
                new ResolverOptions().setRemoteSnapshotPolicy("always"))
        );
        vertx.createHttpServer().requestHandler(ar -> {
            ar.bodyHandler(body -> {
                ar.response().putHeader("content-type", "application/json; charset=utf-8");
                if (body.toString().startsWith("deploy:")) {
                    String stringToDeploy = body.getString(7, body.length());
                    vertx.deployVerticle(stringToDeploy, arv -> {
                        ar.response().end(new JsonObject().put("id", arv.result()).toBuffer());
                    });
                } else if (body.toString().startsWith("undeploy:")) {
                    final String stringToUndeploy = body.getString(9, body.length());
                    ar.response().end(new JsonObject().put("result", "OK").toBuffer());
                    vertx.undeploy(stringToUndeploy);
                } else if (body.toString().startsWith("list-all")) {
                    JsonObject jsonObject = new JsonObject();
                    JsonArray jsonArray = new JsonArray();
                    vertx.deploymentIDs().forEach(jsonArray::add);
                    ar.response().end(new JsonObject().put("ids", jsonArray).toBuffer());
                }
                //vertx.deployVerticle("maven:pt.fabm.vertx.shell:mock-in-maven:1.0-SNAPSHOT::myServiceMock");
            });

        }).listen(8081);
    }
}