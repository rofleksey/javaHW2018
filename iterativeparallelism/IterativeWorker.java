package ru.ifmo.rain.borisov.iterativeparallelism;

public class IterativeWorker<R, T> {
    private final FragmentOperation<R, T> op;
    private final Thread thread;

    IterativeWorker(final FragmentOperation<R, T> operation) {
        op = operation;
        thread = new Thread(op::get);
    }

    void start() {
        thread.start();
    }

    private void join() throws InterruptedException {
        thread.join();
    }

    R getResult() throws InterruptedException {
        join();
        return op.getResult();
    }
}
