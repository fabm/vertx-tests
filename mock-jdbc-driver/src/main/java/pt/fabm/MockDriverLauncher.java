package pt.fabm;

import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.Map;

public class MockDriverLauncher implements InvocationHandler {
    public static final String GET_CONNECTION = "getConnection";
    public static final String CREATE_STATEMENT = "createStatement";
    private Script script;
    private static final boolean defaultAcceptsUrl = true;
    private DriverPropertyInfo[] defaultDriverPropertyInfos = new DriverPropertyInfo[0];
    private static final ClassLoader classLoader = MockDriverLauncher.class.getClassLoader();
    private static final Logger LOGGER = LoggerFactory.getLogger(MockDriver.class);


    private static void load(){
        MockDriverLauncher launcher = new MockDriverLauncher();
        Driver driver = (Driver) Proxy.newProxyInstance(classLoader, new Class[]{Driver.class}, launcher);

        try {
            DriverManager.registerDriver(driver);
        } catch (SQLException e) {
            LOGGER.error("sql exception", e);
        }
    }

    static {
        load();
    }


    private Statement createStatement(Map<String, ?> connectionClosuresMap) {
        if (connectionClosuresMap.containsKey(CREATE_STATEMENT)) {

            final Object objStatement = connectionClosuresMap.get(CREATE_STATEMENT);
            if (objStatement instanceof Map) {
                final Class[] interfaces = {Connection.class};
                InvocationHandler handler = ((Object proxy, Method method, Object[] args) ->
                        ((Map<String, Closure<?>>) objStatement).get(method.getName()).call(args));

                return (Statement) Proxy.newProxyInstance(classLoader, interfaces, handler);
            } else if (objStatement instanceof Closure<?>) {
                return ((Closure<Statement>) objStatement).call();
            }
            return null;
        }
        return null;
    }

    private Connection createConnection() {
        if (script.getBinding().hasVariable(GET_CONNECTION)) {
            //connection map of closures
            Object objGetConnection = script.getBinding().getVariables().get(GET_CONNECTION);
            if (objGetConnection instanceof Map) {
                Map<String, Closure<?>> connectionMap = (Map<String, Closure<?>>) objGetConnection;
                final Class[] interfaces = {Connection.class};

                InvocationHandler handler = ((Object proxy, Method method, Object[] args) ->
                        connectionMap.get(method.getName()).call(args));
                if (connectionMap.containsKey(CREATE_STATEMENT)) {
                    createStatement(connectionMap);
                }

                return (Connection) Proxy.newProxyInstance(classLoader, interfaces, handler);
            }
        }
        return null;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        if (method.getName().equals("toString")) {
            return "mock driver instance";
        }

        if (method.getName().equals(GET_CONNECTION)) {
            return createConnection();
        }

        if (script.getBinding().hasVariable(method.getName())) {
            Closure<?> closure = (Closure<?>) script.getBinding().getVariable(method.getName());
            return closure.call(args);
        }
        return null;
    }
}
