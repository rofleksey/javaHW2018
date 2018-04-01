package ru.ifmo.rain.borisov.iterativeparallelism;

import java.util.function.Function;

public class ParallelMapperTask<R, T> {
    private final Function<? super T, ? extends R> f;
    private final T item;
    private boolean isDone = false, isError;
    private R result;

    ParallelMapperTask(Function<? super T, ? extends R> f, T item) {
        this.f = f;
        this.item = item;
    }

    private synchronized void waitFor() throws InterruptedException {
        while (!isDone) {
            try {
                wait();
            } catch (InterruptedException e) {
                if (isError) {
                    throw e;
                }
            }
        }
    }

    synchronized void complete() {
        isDone = true;
        notifyAll();
    }

    synchronized void error() {
        isError = true;
        notifyAll();
    }

    void execute() {
        result = f.apply(item);
    }

    R getResult() throws InterruptedException {
        waitFor();
        return result;
    }
}
