# ArcticWolfTakeHome
A Java (client) program that monitors a directory

## Server

Server will take the path of configuration properties file as a argument to the main method

### Use following command to start the Server
`java com.server.ServerThreader ./com/resources/ServerConfig.properties`

Server Contains 2 main Classes

### ServerThreader
 - Responsible for creating and Handling port
 - For every client request new Server Thread will be created to handle the message/Event processing

### ServerLogic
 - Contains Logic for Processing file and Storing the File in the store.directory property from ServerConfig.properties file
 - Simple Server Mode will send back acknowledgement to the Client once File is processed Successfully
 - Server is also capable of sending acknowledgements to a different port if needed, This port can be set by ack.port in Config File
 (Currently Compiled Server Class is Running on Simple Server Mode)


## Client

Client program works similar to Server Program, Config File should be passed as command line argument to main Method like shown below

### Use following command to start the Server
`java com.server.Client com/resources/ClientConfig2.properties`

- Client Program is responsible for watching a directory and detecting Create Events(Only Create EVENTS are handled in this problem scope)
- monitored.directory should be provided in the Config file, Client will be enable watcher service on this directory
- On addition of a properties file to the directory, A new map will be created to read the Key-Value Pairs.
- Only those Keys which satisfy the filter.regex property in config file will be processed. If not provided all keys are parsed.
- Thus the map created will be Sent to the Server as Object.
- After sending the key the program will wait for Acknowledgement, Wait timeout can be adjusted by using server.ack.timeout property in the config file (Defaults to 10000 ms if not provided)
- If Failed Acknowledgement / Time out happens then the File will be moved to failed Directory which can be set by failed.directory property in config, If not set defaults to "failed" folder in the src folder of the Server/Client Program
- Client can handle late acknowledgements, In that case any falsely flagged file from failed.directory will be deleted on successfull acknowledgement from server.