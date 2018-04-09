package ru.ifmo.rain.borisov.webcrawler;

import info.kgeorgiy.java.advanced.crawler.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

public class WebCrawler implements Crawler {
    static final int MAX_WAITER_COUNT = Integer.MAX_VALUE - 5;
    ExecutorService downloadService, extractorService;
    Downloader downloader;
    int perHost = 20;
    Semaphore waiter;
    final List<String> success = Collections.synchronizedList(new LinkedList<String>());
    final ConcurrentHashMap<String, IOException> errors = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, Boolean> visited = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, Semaphore> hostSemaphores = new ConcurrentHashMap<>();

    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
        this.downloader = downloader;
        this.perHost = perHost;
        downloadService = Executors.newFixedThreadPool(downloaders);
        extractorService = Executors.newFixedThreadPool(extractors);
        waiter = new Semaphore(MAX_WAITER_COUNT);
    }

    public void downloadCommand(String what, int depth) {
        try {
            if(!visited.containsKey(what)) {
                visited.put(what, Boolean.TRUE);
                Semaphore semki = hostSemaphores.get(URLUtils.getHost(what));
                semki.acquire();
                try {
                    Document d = downloader.download(what);
                    success.add(what);
                    waiter.acquire();
                    extractorService.submit(() -> {
                        extractCommand(d, depth);
                    });
                } finally {
                    semki.release();
                }
            }
        } catch (IOException e) {
            errors.put(what, e);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            waiter.release();
        }
    }

    public void extractCommand(Document doc, int depth) {
        try {
            if(depth > 1) {
                List<String> list = doc.extractLinks();
                for (String s : list) {
                    String host = URLUtils.getHost(s);
                    hostSemaphores.putIfAbsent(host, new Semaphore(perHost));
                    waiter.acquire();
                    downloadService.submit(() -> {
                        downloadCommand(s, depth - 1);
                    });
                }
            }
        } catch (IOException | InterruptedException e) {
            //e.printStackTrace();
        } finally {
            waiter.release();
        }
    }

    @Override
    public Result download(String what, int depth) {
        try {
            hostSemaphores.put(URLUtils.getHost(what), new Semaphore(perHost));
            waiter.acquire();
            downloadService.submit(() -> {
                downloadCommand(what, depth);
            });
            waiter.acquire(MAX_WAITER_COUNT);
        } catch (InterruptedException e) {
            //
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        closeImpl();
        return new Result(success, errors);
    }

    public void closeImpl() {
        downloadService.shutdownNow();
        extractorService.shutdownNow();
    }

    @Override
    public void close() {
        waiter.release();
        closeImpl();
    }
}
