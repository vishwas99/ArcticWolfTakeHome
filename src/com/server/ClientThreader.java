package com.server;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ClientThreader {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Client <config-file-path>");
            return;
        }

        String configFilePath = args[0];
        Properties config = new Properties();
        try (InputStream input = new FileInputStream(configFilePath)) {
            config.load(input);
        } catch (IOException e) {
            System.err.println("Error loading configuration from: " + configFilePath + " - " + e.getMessage());
            return;
        }

        int serverPort;
        int ackPort;
        try {
            serverPort = Integer.parseInt(config.getProperty("server.port", "8080"));
            ackPort = Integer.parseInt(config.getProperty("ack.port", "9090"));
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number in configuration. Using default ports.");
            serverPort = 8080;
            ackPort = 9090;
        }

        try {
            // Create two separate socket connections
            // Creating the watcher and listener threads
            Thread watcherThread = new Thread(new WatcherRunnable(config, 1000));  // Watcher using ackSocket
            Thread listenerThread = new Thread(new ClientListener(config)); // Listener using ackSocket
            // Starting the threads
            watcherThread.start();
            listenerThread.start();
            System.out.println("Watcher/Listener thread started");
            watcherThread.join();
            listenerThread.join();
            System.out.println("Watcher/Listener thread Joined");
        } catch (InterruptedException e) {
            System.err.println("Error connecting to server or thread error: " + e.getMessage());
        }
    }
}
