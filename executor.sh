#!/bin/bash

# Define source and logs directories
SRC_DIR="com/server"
LOGS_DIR="logs"

# Ensure logs directory exists
mkdir -p "$LOGS_DIR"

# Compile the Java files, storing class files in the same directory as source
echo "Compiling Java files..."
javac "$SRC_DIR/Client.java" "$SRC_DIR/ServerLogic.java" "$SRC_DIR/ServerThreader.java"

# Check if compilation was successful
if [ $? -eq 0 ]; then
    echo "Compilation successful."
else
    echo "Compilation failed. Exiting."
    exit 1
fi

# Start the ServerThreader class in the background, logging output to server_log.txt
echo "Starting ServerThreader..."
java -cp "com" server.ServerThreader > "$LOGS_DIR/server_logs.txt" 2>&1 &

# Allow time for the server to start
sleep 2

# Run the Client class, passing in the configuration file, and log output to client_log.txt
echo "Starting Client..."
java -cp "com" server.Client "com/resources/ClientConfig.properties" > "$LOGS_DIR/client_log.txt" 2>&1

echo "Execution started. Logs are being written to $LOGS_DIR."
