package pt.fabm;

import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.servicediscovery.ServiceDiscovery;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

public class MockDriver implements Driver {

    static {
        MockDriver mockDriver = new MockDriver();
        try {
            DriverManager.registerDriver(mockDriver);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final String URL_PREFIX = "jdbc:mock-driver:";
    private Vertx vertx = Vertx.vertx();



    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        Script script;
        try {
            URI uri = new URI(url.substring(URL_PREFIX.length()));
            GroovyShell shell = new GroovyShell();
            script = shell.parse(uri);



            script.getBinding().getVariables().put("vertx", vertx);
            script.run();

        } catch (URISyntaxException | IOException e) {
            throw new IllegalStateException(e);
        }

        final ClassLoader classLoader = MockDriver.class.getClassLoader();
        final Class[] interfaces = {Connection.class};

        InvocationHandler proxyCall = (Object proxy, Method method, Object[] args) -> {
            final Map variables = script.getBinding().getVariables();
            if (variables.containsKey(method.getName())) {
                final Object variable = variables.get(method.getName());
                if (variable instanceof Closure) {
                    return ((Closure) variable).call(args);
                }
                return null;
            }
            return null;
        };


        return (Connection) Proxy.newProxyInstance(classLoader, interfaces, proxyCall);
    }


    @Override
    public boolean acceptsURL(String url) throws SQLException {
        if (!url.startsWith(URL_PREFIX)) {
            return false;
        }
        try {
            URI uri = new URI(url.substring(URL_PREFIX.length()));
            File file = new File(uri);
            return file.exists();
        } catch (URISyntaxException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return 0;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return true;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return null;
    }
}
