package ru.ifmo.rain.borisov.webcrawler;

import info.kgeorgiy.java.advanced.crawler.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

public class WebCrawler implements Crawler {
    private ExecutorService downloadService, extractorService;
    private Downloader downloader;
    private int perHost;
    private final List<CountLatch> counters = new LinkedList<>();
    private final ConcurrentHashMap<String, Semaphore> hostSemaphores = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> visited = new ConcurrentHashMap<>();
    private final Object lock = new Object();
    private boolean isClosed = false;

    private static void printUsage() {
        System.err.println("USAGE: WebCrawler url [downloads [extractors [perHost]]]");
    }

    public static void main(String[] args) {
        if (args == null || args.length == 0 || args.length > 4 || Arrays.stream(args).anyMatch(Objects::isNull)) {
            printUsage();
            return;
        }
        String url = args[0];
        int downloaders = 5;
        int extractors = 5;
        int perHost = 5;
        try {
            if (args.length > 1) {
                downloaders = Integer.parseInt(args[1]);
            }
            if (args.length > 2) {
                extractors = Integer.parseInt(args[2]);
            }
            if (args.length > 3) {
                perHost = Integer.parseInt(args[3]);
            }
        } catch (NumberFormatException e) {
            System.err.println(e.getMessage());
            printUsage();
            return;
        }
        try (WebCrawler slark = new WebCrawler(new CachingDownloader(Paths.get(".", "WebCrawlerDownloads")), downloaders, extractors, perHost)) {
            slark.download(url, 1);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
        this.downloader = downloader;
        this.perHost = perHost;
        downloadService = Executors.newFixedThreadPool(downloaders);
        extractorService = Executors.newFixedThreadPool(extractors);
    }

    private void downloadCommand(String what, int depth, CountLatch counter, List<String> success,
                                 ConcurrentHashMap<String, IOException> errors) {
        try {
            if (visited.putIfAbsent(what, Boolean.TRUE) == null) {
                Semaphore semki = hostSemaphores.get(URLUtils.getHost(what));
                semki.acquire();
                try {
                    Document d = downloader.download(what);
                    success.add(what);
                    if (depth > 1) {
                        counter.up();
                        extractorService.submit(() -> extractCommand(d, depth, counter, success, errors));
                    }
                } finally {
                    semki.release();
                }
            }
        } catch (IOException e) {
            errors.put(what, e);
        } catch (InterruptedException e) {
            //
        } finally {
            counter.down();
        }
    }

    private void extractCommand(Document doc, int depth, CountLatch counter, List<String> success,
                                ConcurrentHashMap<String, IOException> errors) {
        try {
            List<String> list = doc.extractLinks();
            for (String s : list) {
                String host = URLUtils.getHost(s);
                hostSemaphores.putIfAbsent(host, new Semaphore(perHost));
                counter.up();
                downloadService.submit(() -> downloadCommand(s, depth - 1, counter, success, errors));
            }
        } catch (IOException e) {
            //e.printStackTrace();
        } finally {
            counter.down();
        }
    }

    @Override
    public Result download(String what, int depth) {
        List<String> success = Collections.synchronizedList(new LinkedList<String>());
        ConcurrentHashMap<String, IOException> errors = new ConcurrentHashMap<>();
        try {
            CountLatch counter;
            synchronized (lock) {
                if (isClosed) {
                    throw new InterruptedException();
                }
                counter = new CountLatch();
                counters.add(counter);
                hostSemaphores.putIfAbsent(URLUtils.getHost(what), new Semaphore(perHost));
                counter.up();
                downloadService.submit(() -> downloadCommand(what, depth, counter, success, errors));
            }
            counter.waitUntilZero();
        } catch (InterruptedException ignored) {

        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return new Result(success, errors);
    }


    @Override
    public void close() {
        synchronized (lock) {
            downloadService.shutdownNow();
            extractorService.shutdownNow();
            for (CountLatch c : counters) {
                c.finish();
            }
            isClosed = true;
        }
    }
}
