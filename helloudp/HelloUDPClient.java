package ru.ifmo.rain.borisov.helloudp;

import info.kgeorgiy.java.advanced.hello.HelloClient;
import info.kgeorgiy.java.advanced.hello.HelloClientTest;

import java.io.IOException;
import java.net.*;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HelloUDPClient implements HelloClient {
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
        HelloUDPClient udp = new HelloUDPClient();
        udp.run(address, port, requestPrefix, threadsCount, numberOfRequests);
    }


    @Override
    public void run(String addressString, int port, final String requestPrefix, int threadsCount, int numberOfRequests) {
        threads = new ExecutorService[threadsCount];
        for (int i = 0; i < threadsCount; i++) {
            threads[i] = Executors.newSingleThreadExecutor();
        }
        InetAddress addressTemp = null;
        try {
            addressTemp = InetAddress.getByName(addressString);
        } catch (UnknownHostException e) {
            return;
        }
        final InetAddress address = addressTemp;
        for (int j = 0; j < threadsCount; j++) {
            ExecutorService threadpool = threads[j];
            final int jj = j;
            threadpool.submit(() -> {
                for (int i = 0; i < numberOfRequests; i++) {
                    final DatagramSocket clientSocket;
                    try {
                        clientSocket = new DatagramSocket();
                        clientSocket.setSoTimeout(UDP_TIMEOUT);
                    } catch (SocketException e) {
                        return;
                    }
                    //System.out.println("!"+requestPrefix+"!");
                    String requestString = requestPrefix+jj + "_" + i;
                    byte[] request = requestString.getBytes(Charset.forName("UTF-8"));
                    byte[] response = new byte[UDP_PACKET_SIZE];//TODO: сервер может отправить ответ больше ожидаемого, тогда прийдет не все
                    //System.out.println(requestString);
                    for(int attempts = 0; attempts < 10; attempts++) {//TODO: на вход может быть передана слишком большая для UDP строка, и может в итоге отправиться не все
                        try {
                            DatagramPacket sendPacket = new DatagramPacket(request, request.length, address, port);
                            DatagramPacket receivePacket = new DatagramPacket(response, response.length);
                            clientSocket.send(sendPacket);
                            clientSocket.receive(receivePacket);
                            String input = new String(response, 0, receivePacket.getLength(), "UTF-8");
                            if(!input.contains(requestString)) {
                                //System.out.println(input+"!!!!!!!!!!!");
                                continue;
                            }
                            System.out.println(input);
                        } catch (IOException e) {
                            continue;
                        }
                        break;
                    }
                }
            });
        }
        Arrays.stream(threads).forEach(ExecutorService::shutdownNow);
        Arrays.stream(threads).forEach(it -> {
            try {
                it.awaitTermination(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                //
            }
        });
    }
}
