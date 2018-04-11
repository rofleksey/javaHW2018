package ru.ifmo.rain.borisov.webcrawler;

public class CountLatch {
    private final Object lock = new Object();
    int count = 0;
    boolean finished = false;

    public void waitUntilZero() throws InterruptedException {
        synchronized (lock) {
            while (!finished && count > 0) {
                lock.wait();
            }
        }
    }

    public void up() {
        synchronized (lock) {
            count++;
        }
    }

    public void down() {
        synchronized (lock) {
            if(--count <= 0) {
                lock.notify();
            }
        }
    }

    public void finish() {
        synchronized (lock) {
            finished = true;
            lock.notify();
        }
    }
}
