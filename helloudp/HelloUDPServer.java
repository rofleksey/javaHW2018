package ru.ifmo.rain.borisov.helloudp;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class HelloUDPServer {
    static final int UDP_PACKET_SIZE = 8192;
    static final String SERVER_HEADER = "Hello, ";
    ExecutorService threadpool;
    Semaphore bchch;

    static void printUsageAndExit() {
        System.out.println("Usage: HelloUDPServer port thread_count");
        System.exit(1);
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            printUsageAndExit();
        }
        int port, threadCount;
        try {

            port = Integer.valueOf(args[0]);
            threadCount = Integer.valueOf(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Can't parse number: " + e.getMessage());
            return;
        }
        try {
            HelloUDPServer udp = new HelloUDPServer(port, threadCount);
        } catch (SocketException e) {
            System.err.println("Can't create socket: " + e.getMessage());
        }
    }

    HelloUDPServer(int port, int threadCount) throws SocketException {
        threadpool = Executors.newFixedThreadPool(threadCount);
        bchch = new Semaphore(threadCount);
        DatagramSocket serverSocket = new DatagramSocket(port);
        System.out.println("Server started!");
        while (true) {
            final byte[] request = new byte[UDP_PACKET_SIZE];
            final DatagramPacket receivePacket = new DatagramPacket(request, request.length);
            try {
                serverSocket.receive(receivePacket);
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                continue;
            }
            final InetAddress clientAddress = receivePacket.getAddress();
            final int clientPort = receivePacket.getPort();
            final String clientString = clientAddress.toString().replace("/", "") + ":" + clientPort;
            System.out.println("Got client " + clientString);
            String requestString;
            try {
                requestString = new String(request, 0, receivePacket.getLength(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                requestString = "";
            }
            final String requestStringFinal = requestString;
            System.out.println("Request: " + requestStringFinal);
            try {
                bchch.acquire();
            } catch (InterruptedException e) {
                //
            }
            threadpool.submit(() -> {
                String responseString = SERVER_HEADER + requestStringFinal;
                byte[] response = responseString.getBytes(Charset.forName("UTF-8"));
                DatagramPacket sendPacket = new DatagramPacket(response, response.length, clientAddress, clientPort);
                try {
                    serverSocket.send(sendPacket);
                    System.out.println("Response '" + responseString + "' to client " + clientString + " has been sent");
                } catch (IOException e) {
                    //
                }
                bchch.release();
            });
        }
    }

}
