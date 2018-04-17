package pt.fabm

import io.vertx.core.AbstractVerticle
import io.vertx.core.Vertx

def vertx = Vertx.vertx()

L

vertx.deployVerticle(new AbstractVerticle() {
    @Override
    void start() throws Exception {
        "another one"
    }
})

println 'end deploy'