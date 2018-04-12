package ru.ifmo.rain.borisov.webcrawler;

public class CountLatch {
    private int count = 0;
    private boolean finished = false;

    public synchronized void waitUntilZero() throws InterruptedException {
        while (!finished && count > 0) {
            wait();
        }
    }

    public synchronized void up() {
        count++;
    }

    public synchronized void down() {
        if (--count <= 0) {
            notify();
        }
    }

    public synchronized void finish() {
        finished = true;
        notify();
    }
}
