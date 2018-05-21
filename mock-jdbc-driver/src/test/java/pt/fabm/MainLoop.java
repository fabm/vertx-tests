package pt.fabm;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MainLoop {
    private static List<Future> futures = new ArrayList<>();

    private static class AbstractFuture<T> implements Future<T> {
        Predicate<Boolean> donePredicate;
        Callable<T> callable;
        boolean called = false;

        private Predicate<Boolean> createDefaultDoneSupplier() {
            return called -> called;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            throw new IllegalStateException("not cancellable");
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return donePredicate.test(called);
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new ExecutionException(e);
            } finally {
                called = true;
            }
        }

        @Override
        public T get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return get();
        }
    }


    public static void doLoop() {
        while (!futures.isEmpty()) {
            futures = futures
                    .stream()
                    .filter(future -> !future.isDone())
                    .collect(Collectors.toList());
        }
    }

    public static void start() {
        doLoop();
    }

    public static <T> Future<T> submit(Callable callable) {
        AbstractFuture abstractFuture = submitAbstractFuture(callable);
        abstractFuture.donePredicate = abstractFuture.createDefaultDoneSupplier();
        return abstractFuture;
    }

    public static <T> Future<T> submit(Callable callable, Predicate<Boolean> isDone) {
        AbstractFuture abstractFuture = submitAbstractFuture(callable);
        abstractFuture.donePredicate = isDone;
        return abstractFuture;
    }

    private static <T> AbstractFuture<T> submitAbstractFuture(Callable callable) {
        AbstractFuture abstractFuture = new AbstractFuture<T>();
        abstractFuture.callable = callable;
        futures.add(abstractFuture);
        return abstractFuture;
    }

    public static BooleanSupplier isMillisecondsFromNow(long ms) {
        long now = System.currentTimeMillis();
        return () -> now + ms > System.currentTimeMillis();
    }


}
