package pt.fabm

import io.vertx.core.Handler
import io.vertx.core.VertxOptions
import io.vertx.core.spi.cluster.ClusterManager
import io.vertx.rxjava.core.Future
import io.vertx.rxjava.core.Vertx
import io.vertx.spi.cluster.ignite.IgniteClusterManager
import org.junit.Test
import rx.functions.Action1

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier
import java.util.function.Predicate

class MockDriverTests {

    static List<String> messagesToCheck
    static Map<String, ?> vars
    static {
        SqlStatements sqlSts = new SqlStatements()
        sqlSts.run()
        vars = sqlSts.binding.variables
        messagesToCheck = [
                "executeUpdate ${vars['createDB']}",
                "executeUpdate ${vars['createTableStudents']}",
                "executeUpdate ${vars['insertRows'][0]}",
                "executeUpdate ${vars['insertRows'][1]}",
                "executeUpdate ${vars['insertRows'][2]}",
                "executeUpdate ${vars['insertRows'][3]}",
                "executeUpdate ${vars['update']}",
                "executeQuery ${vars['selectToCheckUpdate']}"
        ]
    }

    @Test
    void testMock() {
        System.properties['discovery.server.host'] = 'localhost'
        System.properties['discovery.server.port'] = '8082'

        ServerDiscovery.main()


    }

    void testMockDeprecated() {

        CountDownLatch cdl = new CountDownLatch(1)

        int counter = 0
        Iterator<String> msgsIterator = messagesToCheck.iterator()

        def mockEventBusConsumer = { future, msg ->
            assert msgsIterator.next().toString() == msg.body().toString()
            counter++
            if (counter == messagesToCheck.size()) {
                future.complete()
                cdl.countDown()
            }
        }

        ClusterManager clusterManagerMock = new IgniteClusterManager()
        Vertx.rxClusteredVertx(new VertxOptions().setClusterManager(clusterManagerMock)).subscribe({ vertx ->

            vertx.eventBus().consumer('mock.event') { msg ->
                vertx.rxExecuteBlocking({ future ->
                    mockEventBusConsumer(future, msg)
                }).subscribe()
            }

            int tries = 0

            Action1 onDone = { println "sync with $tries tries" }

            Action1 onError
            Handler<Future> trySend = { future ->
                vertx.eventBus().rxSend('mock.event.sync', 'sync').subscribe({}, onError)
                future.complete()
            }

            onError = { error ->
                if (tries < 10) {
                    println "trying again $tries"
                    tries++
                    vertx.setTimer(1000, {
                        Future future = Future.future()
                        future.rxSetHandler().subscribe(onDone, onError)
                        trySend.handle(future)
                    })
                }
            }

            vertx.rxExecuteBlocking(trySend).toObservable().subscribe(onDone, onError)


        })

        ClusterManager clusterManagerRunner = new IgniteClusterManager()
        Vertx.rxClusteredVertx(new VertxOptions().setClusterManager(clusterManagerRunner)).subscribe { vertx ->

            vertx.eventBus().consumer('mock.event.sync') { msg ->
                println 'sync done'
                vertx.rxExecuteBlocking(MockDriverTests.&testMockDriver).subscribe()
            }

        }


        assert cdl.await(30, TimeUnit.SECONDS)

        cdl = new CountDownLatch(2)

        println 'nodes mock'
        clusterManagerMock.nodes.each { println it }
        clusterManagerMock.leave({
            cdl.countDown()
        })
        clusterManagerRunner.nodes.each { println it }
        clusterManagerRunner.leave({
            cdl.countDown()
        })
        assert cdl.await(20, TimeUnit.SECONDS)

        assert counter == messagesToCheck.size()
    }

    static void testMockDriver(Future future) {

        Class.forName('pt.fabm.MockDriver')

        URL resource = MockDriverTests.getClass().getResource('/script.groovy')
        assert resource != null
        String url = resource.toURI().toString()
        url = 'jdbc:' + url

        Connection connection = DriverManager.getConnection(url)
        assert connection != null

        SqlStatements sqlStatements = new SqlStatements()
        sqlStatements.run()

        Statement statement = connection.createStatement()
        statement.executeUpdate(vars['createDB'])
        statement = connection.createStatement()
        statement.executeUpdate(vars['createTableStudents'])
        vars['insertRows'].collect { statement.executeUpdate(it) }

        statement.executeUpdate(vars['update'])

        ResultSet rs = statement.executeQuery(vars['selectToCheckUpdate'])

        assert rs.next()
        assert 100 == rs.getInt('id')
        assert 30 == rs.getInt('age')
        assert 'Zara' == rs.getString('first')
        assert 'Ali' == rs.getString('last')

        assert rs.next()
        assert 101 == rs.getInt('id')
        assert 30 == rs.getInt('age')
        assert 'Mahnaz' == rs.getString('first')
        assert 'Fatma' == rs.getString('last')

        assert rs.next()
        assert 102 == rs.getInt('id')
        assert 30 == rs.getInt('age')
        assert 'Zaid' == rs.getString('first')
        assert 'Khan' == rs.getString('last')

        assert rs.next()
        assert 103 == rs.getInt('id')
        assert 28 == rs.getInt('age')
        assert 'Sumit' == rs.getString('first')
        assert 'Mittal' == rs.getString('last')

        assert !rs.next()

        rs.close()
        statement.close()
        connection.close()
        future.complete()
    }

}