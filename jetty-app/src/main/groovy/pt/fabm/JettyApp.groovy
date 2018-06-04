package pt.fabm

import io.reactivex.functions.Consumer
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.servicediscovery.ServiceDiscovery
import org.h2.tools.Server
import org.slf4j.Logger
import org.slf4j.LoggerFactory

Logger logger = LoggerFactory.getLogger(JettyApp.class)

logger.info('Start script to test database')

final vertx = Vertx.vertx()
ServiceDiscovery serviceDiscovery = ServiceDiscovery.create(vertx)
Server server = Server.createTcpServer().start()

serviceDiscovery.rxGetRecord({
    boolean toFilter=it.name == 'db-jetty-app'
    return toFilter
},true).subscribe({
    println(it.location)
} as Consumer)

server.stop()
