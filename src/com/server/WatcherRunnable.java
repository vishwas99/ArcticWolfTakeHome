package com.server;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WatcherRunnable implements Runnable {

    // private final BlockingQueue<String> acknowledgmentQueue;
    private final Properties config;
    private final String monitoredDirectory;
    private final int fileProcessInterval;

    public WatcherRunnable(Properties config, int fileProcessInterval) {
        this.config = config;
        // this.acknowledgmentQueue = acknowledgmentQueue;
        this.monitoredDirectory = config.getProperty("monitored.directory");
        this.fileProcessInterval = fileProcessInterval;
    }

    @Override
    public void run() {
        logToFile("Started WATCHER THREAD", config);
        try {
            String filterRegex = config.getProperty("filter.regex", ".*");
            Path monitoredPath = Paths.get(monitoredDirectory);
            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                monitoredPath.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

                while (true) {
                    WatchKey key = watchService.take();

                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
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
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void sendMapToServer(Map<String, String> propertiesMap, Path filePath, Properties config,
            String fileNameForServer) {

        logToFile("Sending properties map to server for file: " + filePath, config);
        logToFile("Properties map: " + propertiesMap, config);

        String serverHost = config.getProperty("server.host");
        int serverPort = Integer.parseInt(config.getProperty("server.port"));

        propertiesMap.put("##FILENAME##", fileNameForServer);

        try (Socket socket = new Socket(serverHost, serverPort);
                ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {
            logToFile("Connected to the server!", config);
            oos.writeObject(propertiesMap); // Serialize and send the map
            oos.flush();

            // Removed acknowledgment handling logic here

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void propertiesMapMaker(Path filePath, String filterRegex, Properties config,
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

    private void logToFile(String message, Properties config) {
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
