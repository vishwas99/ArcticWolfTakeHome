package com.server.multiClient;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientListener implements Runnable {

    private final BlockingQueue<String> acknowledgmentQueue; // Queue to store acknowledgments
    // private final Socket socket;
    private final Properties config;

    public ClientListener(Properties config) {
        // this.socket = socket;
        this.config = config;
        this.acknowledgmentQueue = new LinkedBlockingQueue<>(); // Initialize the BlockingQueue
    }

    @Override
    public void run() {
        logToFile("Started CLIENTLISTENER THREAD", config);

        try (ServerSocket ackSocket = new ServerSocket(Integer.parseInt(config.getProperty("ack.port", "9090")))) {
            while (true) {
                // This will block, waiting for the server to connect and send an ack
                Socket clientSocket = ackSocket.accept();
                logToFile("Received acknowledgment connection from server on ack.port", config);

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                    String ackMessage;
                    while ((ackMessage = reader.readLine()) != null) {
                        processAcknowledgment(ackMessage);
                    }
                } catch (IOException e) {
                    logToFile("Error reading acknowledgment from server: " + e.getMessage(), config);
                } catch (NoSuchAlgorithmException ex) {
                    logToFile("Error calculating checksum: " + ex.getMessage(), config);
                } finally {
                    clientSocket.close();
                }
            }
        } catch (IOException e) {
            System.err.println("Error setting up acknowledgment socket or reading from server: " + e.getMessage());
        }
    }

    private void processAcknowledgment(String ackMessage) throws IOException, NoSuchAlgorithmException {
        logToFile("Received from server: " + ackMessage, config);

        // Split ack to get the filename and status
        String[] serverMessage = ackMessage.split("=");
        if (serverMessage.length != 2) {
            logToFile("Invalid acknowledgment message: " + ackMessage, config);
        }

        // Delete file from Store.dir from config
        String fileNameForServer = serverMessage[0];
        String serverChecksum = serverMessage[1];
        Path filePath = Paths.get(config.getProperty("monitored.directory", "store")).resolve(fileNameForServer);
        Map<String, String> propertiesMap = propsToMap(config.getProperty("filter.regex", ".*"), filePath);
        String clientChecksum = calculateMapHash(propertiesMap);
        logToFile(clientChecksum, config);
        logToFile(serverChecksum, config);
        // Check if the message matches success or failure pattern
        // if (ackMessage.contains("Failure")) {

        //     logToFile("File processing failed. Moving file to failed folder: " + filePath, config);
        //     moveFileToFailedFolder(filePath, config); // Move file to failed folder
        // } else if (clientChecksum.equals(serverChecksum)) {
        //     logToFile("File processed successfully. Deleting the file: " + serverMessage[0], config);
        //     Files.delete(filePath); // Delete the file if acknowledgment is Success
        // } else {
        //     // Invalid Message, Loggin it
        //     logToFile("Invalid acknowledgment message: " + ackMessage, config);
        // }

        // Revert to old logic to check success and failure
        if(ackMessage.contains("Success")) {
            logToFile("File processed successfully. Deleting the file: " + serverMessage[0], config);
            Files.delete(filePath); // Delete the file if acknowledgment is Success
        } else {
            logToFile("File processing failed. Moving file to failed folder: " + filePath, config);
            moveFileToFailedFolder(filePath, config); // Move file to failed folder
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

    private static void moveFileToFailedFolder(Path filePath, Properties config) {
        Path failedDir = Paths.get(config.getProperty("failed.directory", "failed"));
        logToFile("Failed directory path: " + failedDir.toString(), config);

        // Ensure the failed directory exists
        try {
            if (!Files.exists(failedDir)) {
                logToFile("Failed directory does not exist. Creating it...", config);
                Files.createDirectories(failedDir);
            }
        } catch (IOException e) {
            logToFile("Failed to create failed directory: " + e.getMessage(), config);
            return; // Exit method since we cannot proceed without the directory
        }

        Path failedFilePath = failedDir.resolve(filePath.getFileName());
        try {
            // Move and replace the file in the failed directory
            Files.move(filePath, failedFilePath, StandardCopyOption.REPLACE_EXISTING);
            logToFile("File moved to failed directory: " + failedFilePath, config);
        } catch (IOException e) {
            logToFile("Failed to move file to failed directory: " + e.getMessage(), config);
        }
    }

    // Method to calculate the hash of the Map
    private static String calculateMapHash(Map<String, String> propertiesMap) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        StringBuilder sb = new StringBuilder();

        // Sort the Map by key to ensure consistency
        propertiesMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n"));

        md.update(sb.toString().getBytes());
        byte[] hashBytes = md.digest();
        return bytesToHex(hashBytes);
    }

    // Helper method to convert byte array to hex string
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }

    private Map<String, String> propsToMap(String filterRegex, Path filePath) {
        Map<String, String> propsMap = new HashMap<>();

        // Load the properties file
        Pattern pattern = Pattern.compile(filterRegex);
        Properties properties = new Properties();
        try (FileInputStream fileInputStream = new FileInputStream(filePath.toFile())) {
            // Load properties from the file
            properties.load(fileInputStream);
        } catch (IOException e) {
            System.err.println(filePath + " - Error loading properties file: " + e.getMessage());
            System.err.println("Error loading properties file: " + e.getMessage());
            return null;
        }

        // Iterate over the properties and filter based on regex
        for (String key : properties.stringPropertyNames()) {
            // Match the key with the provided regex pattern
            logToFile("Checking key: " + key, config);
            key = key.trim();
            Matcher matcher = pattern.matcher(key);
            if (matcher.matches()) {
                // If it matches, print the key and value (this is just an example)
                // logToFile(key + "=" + properties.getProperty(key));
                propsMap.put(key, properties.getProperty(key));
            }
        }

        return propsMap;
    }
}
