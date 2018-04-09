package ru.ifmo.rain.borisov.webcrawler;

import info.kgeorgiy.java.advanced.crawler.CachingDownloader;

import java.io.IOException;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        WebCrawler web = new WebCrawler(new CachingDownloader(Paths.get("/home/junkie/404/")),5, 5, 5);
        web.download("http://example.com", 1 );
        web.close();
    }
}
