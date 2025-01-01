package com.server;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;

public class ServerLogic implements Runnable {

    private Socket clientSocket;
    private Properties config;
    private final String BACKUP_PREFIX = "_backup_";

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

        if (propFileName == null || propFileName.isBlank()) {
            logToFile("Error: Received message missing file name.");
            return;
        }


        // Write the properties to a file
        try {
            // Create path to store the File
            Path messagePath = Paths.get(this.config.getProperty("store.directory"), propFileName);
            // Directory creation if not exists
            if (!Files.exists(messagePath.getParent())) {
                Files.createDirectories(messagePath.getParent());
            }
            Properties existingProps = new Properties();
            if (Files.exists(messagePath)) {
                existingProps.load(Files.newBufferedReader(messagePath, StandardCharsets.UTF_8));
            }
             // If backup.file is true then save this file/create to backup folder inside the store.directory
            if (Boolean.parseBoolean(this.config.getProperty("backup.file", "false"))) {
                logToFile("backing up the file: " + messagePath);
                Path backupPath = Paths.get(this.config.getProperty("store.directory"), "backup", propFileName);
                // Add _backup to the filename
                backupPath = backupPath.resolveSibling(this.BACKUP_PREFIX + backupPath.getFileName().toString());
                logToFile("Backup path: " + backupPath);
                // Directory creation if not exists
                if (!Files.exists(backupPath.getParent())) {
                    Files.createDirectories(backupPath.getParent());
                }
                // Copy the file to backup location
                logToFile(propFileName + " exists. Moving to backup location: " + backupPath);
                Files.move(messagePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            }
            // Store the properties with propFileName as filename

            // Check if append.to.file prop is set to true
            if (Boolean.parseBoolean(this.config.getProperty("append.to.file", "false"))) {
                logToFile("Appending to file: " + messagePath);
                logToFile("Before merge: ");
                logToFile(existingProps.toString());
                logToFile("After merge: ");
                existingProps.putAll(messageProps);
                logToFile(existingProps.toString());
                // Write the merged properties back to the file
                existingProps.store(Files.newBufferedWriter(messagePath, StandardCharsets.UTF_8), "Appended properties");
            } else{
                // Overwrite the file
                logToFile("Writing or overwriting to file: " + messagePath);
                messageProps.store(Files.newBufferedWriter(messagePath, StandardCharsets.UTF_8), "New Properties");
            }

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
            writer.write("[" + Thread.currentThread().getName() + "] " + message);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }

}
