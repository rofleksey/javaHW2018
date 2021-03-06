package ru.ifmo.rain.borisov.webcrawler;

import info.kgeorgiy.java.advanced.crawler.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

public class WebCrawler implements Crawler {
    private final ExecutorService downloadService, extractorService;
    private final Downloader downloader;
    private int perHost;
    private final List<CountLatch> counters = new LinkedList<>();
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
            Result r = slark.download(url, 1);
            System.out.println(r.getErrors());
            System.out.println(r.getDownloaded());
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
        this.downloader = downloader;
        this.perHost = perHost;
        if(downloaders < Integer.MAX_VALUE) {
            downloadService = Executors.newFixedThreadPool(downloaders);
        } else {
            downloadService = Executors.newCachedThreadPool();
        }
        if(extractors < Integer.MAX_VALUE) {
            extractorService = Executors.newFixedThreadPool(extractors);
        } else {
            extractorService = Executors.newCachedThreadPool();
        }
    }

    private void downloadCommand(String what, int depth, CountLatch counter, List<String> success,
                                 ConcurrentHashMap<String, IOException> errors, ConcurrentHashMap<String, Boolean> visited) {
        try {
            if (visited.putIfAbsent(what, Boolean.TRUE) == null) {
                Document d = downloader.download(what);
                success.add(what);
                if (depth > 1) {
                    counter.up();
                    extractorService.submit(() -> extractCommand(d, what, depth, counter, success, errors, visited));
                }
            }
        } catch (IOException e) {
            errors.putIfAbsent(what, e);
        }catch (Exception e) {
            e.printStackTrace();
        }finally {
            counter.down();
        }
    }

    private void extractCommand(Document doc, String url, int depth, CountLatch counter, List<String> success,
                                ConcurrentHashMap<String, IOException> errors, ConcurrentHashMap<String, Boolean> visited) {
        try {
            List<String> list = doc.extractLinks();
            for (String s : list) {
                try {
                    String host = URLUtils.getHost(s);
                    counter.up();
                    downloadService.submit(() -> downloadCommand(s, depth - 1, counter, success, errors, visited));
                } catch (MalformedURLException ee) {
                    errors.putIfAbsent(s, ee);
                }
            }
        } catch (IOException e) {
            errors.putIfAbsent(url, e);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            counter.down();
        }
    }

    @Override
    public Result download(String what, int depth) {
        //System.out.println("*START*");
        List<String> success = Collections.synchronizedList(new LinkedList<String>());
        ConcurrentHashMap<String, IOException> errors = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, Boolean> visited = new ConcurrentHashMap<>();
        try {
            CountLatch counter;
            synchronized (lock) {
                if (isClosed) {
                    throw new InterruptedException();
                }
                counter = new CountLatch();
                counters.add(counter);
                counter.up();
                downloadService.submit(() -> downloadCommand(what, depth, counter, success, errors, visited));
            }
            counter.waitUntilZero();
            synchronized (counters) {///вроде не нужно synchronized
                counters.remove(counter);
            }
        } catch (InterruptedException ignored) {

        }
        //System.out.println(success);
        //System.out.println(errors);
        //System.out.println("*END*");
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