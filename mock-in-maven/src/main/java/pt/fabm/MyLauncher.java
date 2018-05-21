package pt.fabm;

import io.vertx.core.Vertx;

public class MyLauncher {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle("groovy:/Users/francisco/git/vertx-tests/mock-in-maven/src/main/resources/MyScript.groovy", it ->{
            System.out.println(it.result());
        });
    }
}
