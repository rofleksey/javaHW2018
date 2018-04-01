package ru.ifmo.rain.borisov.iterativeparallelism;

import java.util.ArrayList;
import java.util.LinkedList;

public class ParallelMapperWorker {
    private final LinkedList<ParallelMapperTask> queue;
    private final ArrayList<Thread> workers;
    private boolean stopped = false;//TODO: а нужно ли volatile?

    ParallelMapperWorker(final int threads) {
        queue = new LinkedList<>();
        workers = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            workers.add(new Thread(() -> {
                while (!stopped) {
                    ParallelMapperTask task;
                    synchronized (queue) {
                        while (!stopped && queue.isEmpty()) {
                            try {
                                queue.wait();
                            } catch (InterruptedException ignored) {
                                //
                            }
                        }
                        if (stopped) {
                            return;
                        }
                        task = queue.removeFirst();
                    }
                    task.execute();
                    task.complete();
                }
            }));
        }
        for (Thread t : workers) {
            t.start();
        }
    }

    void addTask(ParallelMapperTask t) {
        synchronized (queue) {
            if (stopped) {
                return;
            }
            queue.addLast(t);
            queue.notify();
        }
    }

    void stop() {
        synchronized (queue) {
            stopped = true;
            queue.notifyAll();
        }
        for (ParallelMapperTask t : queue) {
            t.error();
        }
        for (Thread t : workers) {
            t.interrupt();
            try {
                t.join();
            } catch (InterruptedException ignored) {

            }
        }
    }
}
