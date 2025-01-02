package com.server;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Properties;

public class ServerLogic implements Runnable {

    private Socket clientSocket;
    private Properties config;
    private final String BACKUP_PREFIX = "_backup_";
    private String checkSum;

    public ServerLogic(Socket clientSocket, Properties config) {
        this.clientSocket = clientSocket;
        this.config = config;
    }

    @Override
    public void run() {
        try {
            handleClientRequest();

        } catch (ClassNotFoundException | IOException e) {
            System.err.println("Error handling client request: " + e.getMessage());
        } catch (NoSuchAlgorithmException ex) {
            System.err.println("Error calculating checksum: " + ex.getMessage());
        } finally {
            logToFile("Client disconnected. Active thread count: " + Thread.activeCount());

        }
    }

    private void handleClientRequest() throws IOException, ClassNotFoundException, NoSuchAlgorithmException {
        // Create an ObjectInputStream to read the serialized map sent by the client
        try (ObjectInputStream ois = new ObjectInputStream(clientSocket.getInputStream())) {

            logToFile("Client connected!");

            // Deserialize the object (Map)
            @SuppressWarnings("unchecked")
            Map<String, String> map = (Map<String, String>) ois.readObject();
            logToFile("Received map: " + map);

            // Calculate the hash of the map
            this.checkSum = calculateMapHash(map);
            // Process the Map
            boolean processSuccess = processClientMessage(map);
            // Acknowledgement mode where the server sends an acknowledgment to the client on ack.port
            boolean ackMode = Boolean.parseBoolean(config.getProperty("ack.mode", "false"));
            if(ackMode) {
                sendAcknowledgmentToAckPort(processSuccess, map.get("##FILENAME##"));
            }else{
                sendAcknowledgment(processSuccess, map.get("##FILENAME##"));
            }
           

        }
    }

    private boolean processClientMessage(Map<String, String> message) {
        // Process the message received from the client
        logToFile("Processing message: " + message);

        Properties messageProps = new Properties();
        messageProps.putAll(message);

        String propFileName = message.get("##FILENAME##");
        messageProps.remove("##FILENAME##"); // Remove FILENAME from properties

        if (propFileName == null || propFileName.isBlank()) {
            logToFile("Error: Received message missing file name.");
            return false;
        }

        // Sanitize the file name
        propFileName = propFileName.replaceAll("[\\\\/:*?\"<>|]", "_");

        try {
            // Construct the file path
            Path messagePath = Paths.get(this.config.getProperty("store.directory"), propFileName);

            // Ensure the parent directory exists
            if (!Files.exists(messagePath.getParent())) {
                Files.createDirectories(messagePath.getParent());
            }

            try (FileChannel fileChannel = FileChannel.open(messagePath, StandardOpenOption.WRITE, StandardOpenOption.CREATE)){
                FileLock lock = fileChannel.lock(); 
                Properties existingProps = new Properties();
                if (Files.exists(messagePath)) {
                    existingProps.load(Files.newBufferedReader(messagePath, StandardCharsets.UTF_8));
                }

                // Handle backup if the file exists and backup.file is true
                if (Files.exists(messagePath) && Boolean.parseBoolean(this.config.getProperty("backup.file", "false"))) {
                    logToFile("Backing up the file: " + messagePath);
                    Path backupDir = Paths.get(this.config.getProperty("store.directory"), "backup");
                    if (!Files.exists(backupDir)) {
                        Files.createDirectories(backupDir);
                    }
                    String backupFileName = String.format("%d"+BACKUP_PREFIX+"%s", System.currentTimeMillis(), propFileName);
                    Path backupPath = backupDir.resolve(backupFileName);
                    Files.move(messagePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                    logToFile("File moved to backup location: " + backupPath);
                }

                // Append or overwrite based on config
                if (Boolean.parseBoolean(this.config.getProperty("append.to.file", "true"))) {
                    logToFile("Appending to file: " + messagePath);
                    logToFile("Existing properties before merge: " + existingProps);
                    existingProps.putAll(messageProps);
                    logToFile("Merged properties: " + existingProps);
                    existingProps.store(Files.newBufferedWriter(messagePath, StandardCharsets.UTF_8),
                            "Appended properties");
                } else {
                    logToFile("Overwriting file: " + messagePath);
                    messageProps.store(Files.newBufferedWriter(messagePath, StandardCharsets.UTF_8), "New Properties");
                }
                logToFile("Message successfully processed and written to: " + messagePath);
                lock.release();
                return true;
            }

        } catch (IOException e) {
            logToFile("Error writing message to file: " + e.getMessage());
            return false;
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

    private void sendAcknowledgmentToAckPort(boolean success, String propFileName) throws NoSuchAlgorithmException {

        int ackPort = Integer.parseInt(config.getProperty("ack.port", "9090"));

        try (Socket ackSocket = new Socket(config.getProperty("server.host"), ackPort);
                PrintWriter ackOut = new PrintWriter(ackSocket.getOutputStream(), true)) {
            // Build acknowledgment message
            // String acknowledgment = propFileName + "=" + (success ? this.checkSum : "Failure");
            String acknowledgment = propFileName + "=" + (success ? "Success" : "Failure");
            logToFile("Sending acknowledgment: " + acknowledgment);
            ackOut.println(acknowledgment); // Send acknowledgment to the new socket

        } catch (IOException e) {
            logToFile("Error sending acknowledgment: " + e.getMessage());
        }
    }

    private void sendAcknowledgment(boolean success, String propFileName) {
        try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            // Build acknowledgment message
            String acknowledgment = propFileName + "=" + (success ? "Success" : "Failure");
            logToFile("Sending acknowledgment: " + acknowledgment);
            out.println(acknowledgment);  // Send acknowledgment to client
        } catch (IOException e) {
            logToFile("Error sending acknowledgment: " + e.getMessage());
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

}
