package com.server;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class ServerThreader extends Thread {
    public static void main(String[] args) {
         if (args.length < 1) {
            // logToFile("Please provide the path to the properties file as a command-line argument.", );
            return;
        }

        System.out.println("properties file as a command-line argument.");
        // Read the configuration file from the command line argument
        Path configPath = Paths.get(args[0]);

        if (!Files.exists(configPath) || !Files.isReadable(configPath)) {
            System.err.println("Configuration file not found or not readable: " + configPath);
            return;
        }

        // Load into Properties Object
        Properties config = new Properties();
        try (InputStream input = new FileInputStream(configPath.toFile())) {
            config.load(input);
        } catch (IOException e) {
            System.err.println("Error loading configuration: " + e.getMessage());
            return;
        }

        startServerThread(config);
    }

    public static void startServerThread(Properties config) {

        // If port is not provided in Config it will be defaulted to 8080
        int port = config.getProperty("server.port") != null ? Integer.parseInt(config.getProperty("server.port")) : 8080;
        

        // Create a server Socket
        try (ServerSocket serverSocket = new ServerSocket(port)){

            logToFile("Server started on port " + port, config);
            logToFile("Thread Number: " + Thread.activeCount(), config);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logToFile("Shutting down server...", config);
            }));

            while (true) {
                // Wait for a client connection
                Socket clientSocket = serverSocket.accept();
                logToFile("Client connected: " + clientSocket.getInetAddress(), config);

                // Create a new thread to handle the client
                Thread clientThread = new Thread(new ServerLogic(clientSocket, config));
                clientThread.start(); // Start the client handler thread
                // timeout for 5 secs testing
                Thread.sleep(10000);
            }

        }catch (IOException | InterruptedException e) {
            System.err.println("Error creating server socket: " + e.getMessage());
            return;
        }
    }

     private static void logToFile(String message, Properties config) {
        System.out.println(message);
        String logFilePath = config.getProperty("log.file.path", "./logs/client_log.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFilePath, true))) {
            writer.write(message);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }
}
