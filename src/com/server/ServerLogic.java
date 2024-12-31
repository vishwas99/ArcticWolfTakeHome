package com.server;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;

public class ServerLogic implements Runnable {

    private Socket clientSocket;
    private Properties config;

    public ServerLogic(Socket clientSocket, Properties config){
        this.clientSocket = clientSocket;
        this.config = config;
    }

    @Override
    public void run() {
        try {
            handleClientRequest();
            
        } catch (ClassNotFoundException | IOException e) {
            System.err.println("Error handling client request: " + e.getMessage());
        } finally {
            logToFile("Client disconnected. Active thread count: " + Thread.activeCount());

        }
    }

    private void handleClientRequest() throws IOException, ClassNotFoundException {
        // Create an ObjectInputStream to read the serialized map sent by the client
        try (ObjectInputStream ois = new ObjectInputStream(clientSocket.getInputStream())) {

            logToFile("Client connected!");

            // Deserialize the object (Map)
            @SuppressWarnings("unchecked")
            Map<String, String> map = (Map<String, String>) ois.readObject();
            logToFile("Received map: " + map);

            // Process the Map
            processClientMessage(map);
            
        } finally {
            // Close the client socket after the operation
            clientSocket.close();
        }
    }

    private void processClientMessage(Map<String, String> message) {
        // Process the message received from the client
        logToFile("Processing message: " + message);

        // Convert map to propterties
        Properties messageProps = new Properties();
        messageProps.putAll(message);
        // Get file name from message
        String propFileName = message.get("##FILENAME##");
        messageProps.remove("##FILENAME##"); // Remove FILENAME from Props

        propFileName = propFileName.replaceAll("[\\\\/:*?\"<>|]", "_"); // Replace invalid characters


        // Write the properties to a file
        try {
            // Create path to store the File
            Path messagePath = Paths.get(this.config.getProperty("store.directory"), propFileName);
            // Directory creation if not exists
            if (!Files.exists(messagePath.getParent())) {
                Files.createDirectories(messagePath.getParent());
            }
            // Store the properties with propFileName as filename
            messageProps.store(Files.newBufferedWriter(messagePath), "Message properties");
            logToFile("Message written to: " + messagePath);
        } catch (IOException e) {
            System.err.println("Error writing message: " + e.getMessage());
        }

    }

     private void logToFile(String message) {
        System.out.println(message);
        System.out.println(this.config.getProperty("log.file.path"));
        String logFilePath = this.config.getProperty("log.file.path", "./logs/client_log.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFilePath, true))) {
            writer.write(message);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }

}
