package ru.ifmo.rain.borisov.helloudp;

import java.io.IOException;
import java.net.*;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HelloUDPClient {
    static final int UDP_PACKET_SIZE = 8192;
    static final int UDP_TIMEOUT = 1000;
    ExecutorService[] threads;

    static void printUsageAndExit() {
        System.out.println("Usage: HelloUDPClient address port request_prefix threads_count number_of_requests");
        System.exit(1);
    }

    public static void main(String[] args) {
        if (args.length < 5) {
            printUsageAndExit();
        }
        String address, requestPrefix;
        int port, threadsCount, numberOfRequests;
        try {
            address = args[0];
            port = Integer.valueOf(args[1]);
            requestPrefix = args[2];
            threadsCount = Integer.valueOf(args[3]);
            numberOfRequests = Integer.valueOf(args[4]);
        } catch (NumberFormatException e) {
            System.err.println("Can't parse number: " + e.getMessage());
            return;
        }
        try {
            HelloUDPClient udp = new HelloUDPClient(address, port, requestPrefix, threadsCount, numberOfRequests);
            udp.waitFor();
        } catch (UnknownHostException e) {
            System.err.println("Unknown host: " + e.getMessage());
        } catch (SocketException e) {
            System.err.println("Can't create socket: " + e.getMessage());
        }
    }

    HelloUDPClient(final String addressString, final int port, final String requestPrefix, final int threadsCount, final int numberOfRequests) throws UnknownHostException, SocketException {
        threads = new ExecutorService[threadsCount];
        for (int i = 0; i < threadsCount; i++) {
            threads[i] = Executors.newSingleThreadExecutor();
        }
        InetAddress address = InetAddress.getByName(addressString);
        for (int j = 0; j < threadsCount; j++) {
            ExecutorService threadpool = threads[j];
            for (int i = 0; i < numberOfRequests; i++) {
                final int ii = i, jj = j;
                final DatagramSocket clientSocket = new DatagramSocket();
                clientSocket.setSoTimeout(UDP_TIMEOUT);
                threadpool.submit(() -> {
                    String requestString = requestPrefix + (jj + 1) + "_" + (ii + 1);
                    byte[] request = requestString.getBytes(Charset.forName("UTF-8"));
                    byte[] response = new byte[UDP_PACKET_SIZE];//TODO: сервер может отправить ответ больше ожидаемого, тогда прийдет не все
                    DatagramPacket sendPacket = new DatagramPacket(request, request.length, address, port);
                    DatagramPacket receivePacket = new DatagramPacket(response, response.length);
                    System.out.println(requestString);
                    while (true) {//TODO: на вход может быть передана слишком большая для UDP строка, и может в итоге отправиться не все
                        try {
                            clientSocket.send(sendPacket);
                            clientSocket.receive(receivePacket);
                            System.out.println(new String(response, 0, receivePacket.getLength(), "UTF-8"));
                        } catch (IOException e) {
                            continue;
                        }
                        break;
                    }
                });
            }
        }
    }

    void waitFor() {
        for (int i = 0; i < threads.length; i++) {
            threads[i].shutdown();
        }
        for (int i = 0; i < threads.length; i++) {
            try {
                threads[i].awaitTermination(1, TimeUnit.DAYS);
            } catch (InterruptedException e) {
                //
            }
        }
    }
}
