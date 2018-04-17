package pt.fabm

import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Verticle
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.core.json.JsonObject
import io.vertx.ext.shell.ShellService
import io.vertx.ext.shell.ShellServiceOptions
import io.vertx.ext.shell.command.Command
import io.vertx.ext.shell.command.CommandBuilder
import io.vertx.ext.shell.command.CommandProcess
import io.vertx.ext.shell.command.CommandRegistry
import io.vertx.ext.shell.term.TelnetTermOptions
import io.vertx.maven.MavenVerticleFactory
import io.vertx.maven.ResolverOptions


def vertx = Vertx.vertx()

JsonObject options = new JsonObject()
        .put('httpOptions', new JsonObject()
        .put('host', 'localhost')
        .put('port', 8080)
)

DeploymentOptions depOp = new DeploymentOptions()
depOp.config = options

Map<String, Closure<Verticle>> verticles = [:]
verticles['creator'] = {
    new AbstractVerticle() {
        @Override
        void start() throws Exception {
            println 'hello world'
        }
    }
}

System.properties['my-prop'] = 'cool'

verticles['the-one'] = {
    new AbstractVerticle() {
        @Override
        void start() throws Exception {

            HttpServer httpServer = vertx.createHttpServer()
            httpServer.requestHandler { req ->
                req.response().end(System.properties['my-prop']?.toString())
                println '----'
            }
            httpServer.listen(8080)
        }
    }
}

vertx.registerVerticleFactory(new MavenVerticleFactory(
        new ResolverOptions()
                .setLocalRepository('{user.home}/.m2/repository')
                .setRemoteSnapshotPolicy('always')
                )
);

//vertx.deployVerticle('maven:pt.fabm.vertx.shell:mock-in-maven:1.0-SNAPSHOT::myServiceMock')

Command listVerticles = CommandBuilder.command("mocks")
        .processHandler { CommandProcess process ->
    if (process.args().isEmpty()) {
        verticles.keySet().each { process.write("${it}\n") }
    } else {
        process.args().each {
            def verticle = verticles[it]?.call()
            if (verticle != null) {
                vertx.deployVerticle(verticle)
            }
        }
    }
    process.end()
}.build(vertx)

ShellService service = ShellService.create(vertx, new ShellServiceOptions().setTelnetOptions(
        new TelnetTermOptions().setHost("localhost").setPort(3000)
));
CommandRegistry.getShared(vertx).registerCommand(listVerticles);

service.start { ar ->
    if (!ar.succeeded()) {
        ar.cause().printStackTrace();
    }
}


