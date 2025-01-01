package com.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Client {
    public static void main(String[] args) {
        // Load the configuration file
        if (args.length < 1) {
            System.err.println("Usage: java Client <config-file-path>");
            return;
        }

        // Load the configuration file from the argument
        String configFilePath = args[0];
        Properties config = new Properties();
        try (InputStream input = new FileInputStream(configFilePath)) {
            config.load(input);
        } catch (IOException e) {
            System.err.println("Error loading configuration from: " + configFilePath + " - " + e.getMessage());
            return;
        }

        int serverPort;

        try {
            serverPort = Integer.parseInt(config.getProperty("server.port", "8080"));
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number in configuration. Using default port 8080.");
            serverPort = 8080;
        }

        // Read configuration values
        String monitoredDirectory = config.getProperty("monitored.directory");

        if (monitoredDirectory == null || monitoredDirectory.isBlank()) {
            System.err.println("Missing 'monitored.directory' in configuration. Exiting.");
            return;
        }

        String filterRegex = config.getProperty("filter.regex", ".*");
        if (!isValidRegex(filterRegex)) {
            System.err.println("Invalid 'filter.regex' in configuration. Exiting.");
            return;
        }

        String serverHost = config.getProperty("server.host");
        int fileProcessInterval = Integer.parseInt(config.getProperty("file.process.interval", "0"));

        logToFile("Client configuration loaded:", config);
        logToFile("Monitored Directory: " + monitoredDirectory, config);
        logToFile("Key Filter Regex: " + filterRegex, config);
        logToFile("Server Address: " + serverHost + ":" + serverPort, config);

        // Create the directory path
        Path monitoredPath = Paths.get(monitoredDirectory);

        // Watch Service
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {

            monitoredPath.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
            logToFile("Monitoring directory: " + monitoredPath, config);

            while (true) {
                WatchKey key;
                try {
                    // Wait for the next watch key
                    key = watchService.take();
                } catch (InterruptedException e) {
                    System.err.println("Watcher interrupted: " + e.getMessage());
                    return;
                }

                // // Process the events for the key
                // for (WatchEvent<?> event : key.pollEvents()) {
                // // Get event type (only ENTRY_CREATE for now)
                // WatchEvent.Kind<?> kind = event.kind();

                // // If a file is created
                // if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                // // Get the file name from the event
                // Path filePath = (Path) event.context();
                // Path fullFilePath = monitoredPath.resolve(filePath);
                // System.out.println("fullFilePath: " + fullFilePath);
                // logToFile("New file detected: " + fullFilePath, config);

                // // Only process if it’s a .properties file
                // if (filePath.toString().endsWith(".properties")) {
                // logToFile("Processing file: " + filePath, config);

                // // Add your file processing logic here, e.g., loading the file, filtering
                // keys, etc.
                // propertiesMapMaker(fullFilePath, filterRegex, config, filePath.toString());
                // try {
                // Thread.sleep(fileProcessInterval);
                // } catch (InterruptedException e) {
                // e.printStackTrace();
                // }
                // }
                // }
                // }

                processWatchKey(key, monitoredPath, filterRegex, config, fileProcessInterval);

                // Reset the key to continue watching for events
                boolean valid = key.reset();
                if (!valid) {
                    System.err.println("WatchKey no longer valid. Exiting.");
                    break;
                }
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            System.err.println("Error setting up directory watcher: " + e.getMessage());
        }
    }

    private static void processWatchKey(WatchKey key, Path monitoredPath, String filterRegex, Properties config,
            int interval) throws InterruptedException {
        for (WatchEvent<?> event : key.pollEvents()) {
            if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                // Path filePath = monitoredPath.resolve((Path) event.context());
                Path filePath = (Path) event.context();
                Path fullFilePath = monitoredPath.resolve(filePath);
                logToFile("New file detected: " + filePath, config);

                if (filePath.toString().endsWith(".properties")) {
                    logToFile("Processing file: " + filePath, config);
                    propertiesMapMaker(fullFilePath, filterRegex, config, filePath.toString());
                    // For Testing multi threading
                    // Thread.sleep(interval);
                }
            }
        }
        key.reset();
    }

    private static void propertiesMapMaker(Path filePath, String filterRegex, Properties config,
            String fileNameForServer) {

        Map<String, String> propertiesMap = new HashMap<>();

        // Load the properties file
        Pattern pattern = Pattern.compile(filterRegex);
        Properties properties = new Properties();
        try (FileInputStream fileInputStream = new FileInputStream(filePath.toFile())) {
            // Load properties from the file
            properties.load(fileInputStream);
        } catch (IOException e) {
            System.err.println(filePath + " - Error loading properties file: " + e.getMessage());
            System.err.println("Error loading properties file: " + e.getMessage());
            return;
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
                propertiesMap.put(key, properties.getProperty(key));
            }
        }

        logToFile("Filtered properties map: " + propertiesMap, config);

        // This Map along with File Name should be sent to Server
        sendMapToServer(propertiesMap, filePath, config, fileNameForServer);

    }

    private static void sendMapToServer(Map<String, String> propertiesMap, Path filePath, Properties config,
            String fileNameForServer) {

        logToFile("Sending properties map to server for file: " + filePath, config);
        logToFile("Properties map: " + propertiesMap, config);

        String serverHost = config.getProperty("server.host");
        int serverPort = Integer.parseInt(config.getProperty("server.port"));
        // Acknowledgment timeout from server -> defualts to 5 secs
        int ackTimeout = Integer.parseInt(config.getProperty("server.ack.timeout", "10000"));
        long timeoutTime = System.currentTimeMillis() + ackTimeout;
        final AtomicBoolean ackForCurrentFile = new AtomicBoolean(false);

        // Map<String, String> mapWithFileName = new HashMap<>();

        propertiesMap.put("##FILENAME##", fileNameForServer);

        try (Socket socket = new Socket(serverHost, serverPort);
                ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {
            logToFile("Connected to the server!", config);

            oos.writeObject(propertiesMap); // Serialize and send the map
            oos.flush();
            // Create a separate thread to wait for the acknowledgment
            final String[] serverMessage = { null };
            Thread responseThread = new Thread(() -> {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                    while (System.currentTimeMillis() < timeoutTime) {
                        if (socket.getInputStream().available() > 0) {
                            serverMessage[0] = in.readLine(); // Read the server response
                            logToFile("Received from server: " + serverMessage[0], config);

                            // Check if the message matches success or failure pattern
                            if (serverMessage[0].contains(fileNameForServer + "=Success")) {
                                logToFile("File processed successfully. Deleting the file: " + filePath, config);
                                Files.delete(filePath); // Delete the file if acknowledgment is Success
                                ackForCurrentFile.set(true);
                                break;
                            } else if (serverMessage[0].contains(fileNameForServer + "=Failure")) {
                                logToFile("File processing failed. Moving file to failed folder: " + filePath, config);
                                moveFileToFailedFolder(filePath, config); // Move file to failed folder
                                ackForCurrentFile.set(true);
                                break;
                            }else{
                                // split the message and get Filename and Status
                                String[] splitMessage = serverMessage[0].split("=");
                                if(splitMessage.length == 2){
                                    if(splitMessage[1].equals("Success")){
                                        logToFile("File processed successfully. Deleting the file: " + filePath, config);
                                        // Delete from Failed Directory if exists
                                        Path failedDir = Paths.get(config.getProperty("failed.directory", "failed"));
                                        Path failedFilePath = failedDir.resolve(filePath.getFileName());
                                        if(Files.exists(failedFilePath)){
                                            Files.delete(failedFilePath);
                                        }
                                    }else if(splitMessage[1].equals("Failure")){
                                        logToFile("File processing failed. Moving file to failed folder: " + filePath, config);
                                        // The file will already be in the Failed Folder so no need to move
                                    }
                                }
                            }
                        }
                        Thread.sleep(10); // Add small delay to avoid high CPU usage
                    }

                    // If no valid acknowledgment for current file was received
                    if (!ackForCurrentFile.get()) {
                        logToFile("Server acknowledgment timeout. Moving file to failed folder: " + filePath, config);
                        // Move file to failed folder if no response was received
                        moveFileToFailedFolder(filePath, config);
                    }
                } catch (IOException | InterruptedException e) {
                    serverMessage[0] = "FAIL"; // Failure if there is an exception in reading the response
                    logToFile("Error reading server response: " + e.getMessage(), config);
                    moveFileToFailedFolder(filePath, config); // Move file to failed folder on error
                }
            });

            responseThread.start();
            responseThread.join(ackTimeout); // Wait for the response or timeout

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
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

    private static boolean isValidRegex(String regex) {
        try {
            Pattern.compile(regex);
            return true;
        } catch (Exception e) {
            return false;
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

}
