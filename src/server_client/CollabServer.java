package server_client;

import document.DocumentInstance;
import document.OperationEngineException;
import document.Pair;
import gui.ErrorDialog;
import org.apache.commons.lang3.RandomStringUtils;
import signal.EncryptedMessage;
import signal.RegistrationInfo;
import signal.SessionInfo;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/**
 * Creates a server that relay information between clients. It will block
 * until a client connects, and then send a copy of the requested document to the client.
 * It will start a new thread to handle each new client connection.
 * Each time a client makes a change to the document, it will send it to the server, which will relay it to the rest of the clients.
 * Note: a server must be present before a client can connect.
 */

@SuppressWarnings("ConstantConditions")
public class CollabServer {

    /**
     * maximum number of clients allowed at a time
     */
    private static final int MAX_CLIENTS = 30;
    /**
     * lock object
     */
    private final Object lock = new Object();
    /**
     * server socket that accepts client connections
     */
    private ServerSocket serverSocket;
    /**
     * number of clients actively connected
     */
    private int users = 0;

    /**
     * order of the operations
     */
    private int order;

    private int clientID = 0;

    /**
     * A hash of socket to its associated input/output streams. We want to use these
     * two streams each time we need to communicate between a particular client and the server.
     */
    private final HashMap<Socket, Pair<ObjectInputStream, ObjectOutputStream>> socketStreams = new HashMap<>();



    //List of all online users
    private final ArrayList<String> usernames = new ArrayList<>();

     //List of all documents
    private final ArrayList<String> documents = new ArrayList<>();

    //List of clients that are in each documents.
    private final HashMap<String, String[]> clientLists = new HashMap<>();

     //Neccessary for the initial implementation, but should be changed
    private final HashMap<String, DocumentInstance> documentInstances = new HashMap<>();

    //map of username to info the server has on the user
    private final HashMap<String, UserInfo> clientInfos = new HashMap<>();

    //list of valid unused tokens
    private final ArrayList<String> tokens = new ArrayList<>();

    /**
     * Constructor for making a server. It will set the port number and create a server socket.
     * List of documents and socket names are also initialized
     *
     * @param port - targeted port number
     */

    public CollabServer(int port) {
        // Sets server info
        // Create a server socket for clients to connect to
        try {
            this.serverSocket = new ServerSocket(port);
        }
        catch (IOException e) {
            System.err.println("Cannot create server socket at port: " + port);
            return;
        }

        System.out.println("Server created.");
    }

    /**
     * start up a server by calling the connect() method.
     *
     * @throws OperationEngineException if the operation finds an inconsistency
     */
    public void start() {
        try {
            this.serve();
        } catch (Exception e) {
            new ErrorDialog(e.toString());
        }

    }

    /**
     * Run the server, listening for client connections and handling them.
     * Never returns unless an exception is thrown. Creates a new thread for every new connection.
     * @throws IOException if the main server socket is broken (IOExceptions from
     *                     individual clients do *not* terminate serve()).
     */
    private void serve() {

        while (true) {
            if (this.users > MAX_CLIENTS) {
                throw new RuntimeException("Too many clients!");
            }
            // block until a client connects
            try {
                final Socket socket = serverSocket.accept();

                // New client detected; create new thread to handle each connection
                Thread thread = new Thread(() -> {
                    try {
                        handleConnection(socket);
                    } catch (IOException e) {

                    } finally {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            new ErrorDialog(e.toString());
                        }
                    }
                });
                thread.start();
            } catch (Exception e) {
                System.err.println("An exception occured. See below.");
                e.printStackTrace();
            }
        }
    }

    /**
     * Handle a single client connection. Returns when client disconnects, after
     * closing all the appropriate sockets and streams. This is where
     * information exchange will be done between the client and server.
     *
     * @param socket socket via which the client is connected
     * @throws IOException if connection has an error or terminates unexpectedly
     */
    private void handleConnection(Socket socket) throws IOException {
        String documentID;
        String clientName = null;
        Boolean returningUser;
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

        // Add stream to HashMap
        socketStreams.put(socket, new Pair<>(in, out));
        UserInfo clientInfo = null;
        try {
            Object input;
            //waits for client to write their name and saves it along with registration info of the user.
            input = in.readObject();

            if (input instanceof Pair) {
                Pair p = (Pair) input;
                clientName = (String) p.first;
                clientInfo = clientInfos.get(clientName);

                //client is new
                for(String user : clientInfos.keySet()) {
                    if(user.equals(clientName)) {
                        if(!(p.second instanceof Boolean)) {
                            System.err.println(clientName + " already taken.");
                            out.writeObject(clientName + " is already taken. Please enter a new username");
                            return;
                        }
                        else {
                            out.writeObject(true);
                        }
                    }
                }
                if(clientInfo == null) {

                    //client sent indication that they are returning even though they are new
                    if(p.second instanceof Boolean) {
                        System.err.println(clientName + " attempted to log in as returning user without info");
                        out.writeObject("Missing user information in server.");
                        return;
                    }

                    out.writeObject(true);

                    Object token = in.readObject();

                    if(!(token instanceof String) || token == null) {
                        System.err.println(clientName + " attempted to register without token");
                        out.writeObject("Missing token.");
                        return;
                    }
                    else {
                        //check token
                        if(tokens.contains(token)) {
                            clientInfo = new UserInfo();
                            clientInfos.put(clientName, clientInfo);
                            clientInfo.registrationInfo = (RegistrationInfo) p.second;
                            tokens.remove(token);
                            out.writeObject(true);
                        }
                        else {
                            System.err.println(clientName + " attempted to register with invalid token");
                            out.writeObject("Invalid token.");
                            return;
                        }
                    }
                }
            }
            else {
                throw new IOException("Expected client name");
            }

            clientInfo.currentDocument = "";
            ArrayList<String> clientDocuments = filteredDocumentList(clientName);
            out.writeObject(clientDocuments);
            out.flush();

            //Stays here waiting for the user to choose a document.
            while(true) {
                //Receives which document to edit, a document/user list request, or a token generated by the user.
                input = in.readObject();
                if(input instanceof String) {
                    if(input.equals("refresh")) {
                        // Sends list of documents to client
                        clientDocuments = filteredDocumentList(clientName);
                        out.writeObject(clientDocuments);
                        out.flush();
                    }
                    //else, client generated a token
                    else tokens.add((String) input);
                }

                //client is requesting list of registered clients
                else if(input instanceof  ArrayList) {
                    out.writeObject(new ArrayList(clientInfos.keySet()));
                }

                //got document name + returning user boolean pair
                else if (input instanceof Pair) {
                    returningUser = (Boolean) ((Pair)input).second;
                    break;
                }
            }

            synchronized (lock) {
                // TODO: client crashes if you use an existing document name. Maybe fix
                documentID = (String) ((Pair) input).first;
                clientInfo.currentDocument = documentID;

                //If document does not exist, create it
                if (!documents.contains(documentID)) {
                    documents.add(documentID);
                    documentInstances.put(documentID, new DocumentInstance(""));
                    input = in.readObject();

                    //expecting a list of clients the user invited to the document
                    if (input instanceof ArrayList) {
                        String[] clientList = ((ArrayList<String>)input).toArray(new String[0]);
                        clientLists.put(documentID, clientList);

                        //build sessioninfo for document creator with the rest of the users. Assumes everyone is registered.
                        for(String client : clientList) {
                            UserInfo curUser = clientInfos.get(client);
                            if(clientInfos.get(client).registrationInfo == null) {
                                //shouldn't happen under normal circumstances (client using the GUI)
                                throw new IllegalArgumentException("Client in list does not exist.");
                            }
                            //create an empty history list for each user in the document
                            curUser.histories.put(documentID, new ArrayList<>());
                            if(curUser.socket == null) curUser.socket = new Pair(null, false); //don't know the client's socket yet
                        }

                        //create session info between every 2 clients
                        for(String client : clientList) {
                            UserInfo curUser = clientInfos.get(client);
                            RegistrationInfo registrationInfo = curUser.registrationInfo;

                            for(String otherClient : clientList) {
                                if(otherClient.equals(client)) continue;
                                SessionInfo session = registrationInfo.createSessionInfo(client, documentID);
                                clientInfos.get(otherClient).sessionInfos.add(session);
                            }
                        }
                    }
                    else {
                        throw new IOException("Expected client list");
                    }
                }
                this.users++;
                //set user as joined and associate with their socket
                // TODO: move this to the correct place. (active socket is only used by their code and works here, but logic is incorrect)
                clientInfo.socket = new Pair<>(socket, true);

                //increment client ID
                clientID++;
            }

            // Sends client the client ID
            out.writeObject(clientID);
            out.flush();

            //if this is the first time the user entered this document, give them the session information.
            if(!returningUser) {
                //TODO: remove unneccessary session infos?
                out.writeObject(clientInfos.get(clientName).sessionInfos);
                out.flush();
            }

            //send the client the history of the document.
            out.writeObject(new Pair(documentInstances.get(documentID), clientInfo.histories.get(documentID)));
            out.flush();

            //now that client received the history, we empty it again so it only contains messages they didn't receive.
            clientInfo.histories.get(documentID).clear();

            //Receives username of client. Updates users.
            input = in.readObject();
            if (!(input instanceof String)) {
                throw new RuntimeException("Expected client username");
            }
            synchronized (lock) {
                usernames.add(clientName);
            }

            updateUsers();

            //Receives operations from client. That's all the server is expecting from the client from now on.
            input = in.readObject();
            while (input != null) {
                parseInput(input, documentID);
                input = in.readObject();
            }
        }
        catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        //Clean up, close connections
        finally {
            System.out.println("Connection to client #" + clientID + " lost.");

            //set connection as closed
            synchronized (lock) {
                try {
                    //set client as inactive
                    clientInfo.socket.second = false;
                    clientInfo.currentDocument = "";
                    this.users--;
                    usernames.remove(clientName);
                    //need to update the view of who still in the edit room
                } catch (Exception e) {
                    System.err.println("Client not found");
                }
            }
            //update user lists
            updateUsers();
            out.close();
            in.close();
        }
    }

    public String generateToken() {
        String generatedString = RandomStringUtils.randomAlphanumeric(10);

        //We do not need to check if the token already exists.
        //By the birthday paradox, there is a 99.9% of all the tokens being different given 10 million unused tokens.
        tokens.add(generatedString);
        return generatedString;
    }

    //Returns a list of all documents given client has authorization to see
    private ArrayList<String> filteredDocumentList (String clientName) {
        ArrayList<String> clientDocuments = new ArrayList<>();
        for (String document : documents) {
            String[] clientList = clientLists.get(document);
            for (int i = 0; i < clientList.length; i++) {

                //add every document that contains the client in its client list
                if (clientList[i].equals(clientName)) {
                    clientDocuments.add(document);
                    break;
                }
            }
        }
        return clientDocuments;
    }

    /**
     * Takes an object that was received from the client and parses it. It
     * should be an encrypted message the client created.
     *
     * @param input      - object sent from the client to parse
     * @param documentID - the document that the client is editing
     * @throws IOException - caused if the operation object is corrupt, or if the socket
     *                     connection breaks
     */
    private void parseInput(Object input, String documentID) throws IOException {
        transmit(input, documentID);
    }

    /**
     * After a change by a client is received, this method will be called to
     * send the changes to all the other clients in the documents, who will then apply their
     * own OT algorithm to generate the most recent copy of the document.
     *
     * @throws IOException if the input/output stream is corrupt
     */
    private void transmit(Object o, String documentID) throws IOException {
        ObjectOutputStream out;
        // Increment the order so the Operation Engine can determine
        // the relative position of all the operations

        //we receive an array of encrypted messages, each to be sent to a specific client in the document.
        EncryptedMessage[] messages = (EncryptedMessage[]) o;
        synchronized (lock) {
            for(int i = 0; i < messages.length; i++) {
                messages[i].setOrder(order);
            }
            order++;
        }

        for(int i = 0; i < messages.length; i++) {
            EncryptedMessage message = messages[i];

            //find the socket of the recipient of the message
            UserInfo recipientInfo = clientInfos.get(message.recipientID);
            Pair<Socket, Boolean> p = recipientInfo.socket;
            Socket currentSocket = p.first;
            Boolean activeSocket = p.second;
            if (recipientInfo.currentDocument.equals(documentID)) {
                out = socketStreams.get(currentSocket).second;
                out.writeObject(message);
                out.flush();
            }
            else {
                //client is not in the document, add message to their history so they can update when they next join.
                recipientInfo.histories.get(documentID).add(message);
            }
        }
    }

    /**
     * Sends a Pair of users and documents to all active clients to update the
     * right pane. It is called each time a new client joins or leaves.
     *
     * @throws IOException - if the socket connection is corrupted
     */
    @SuppressWarnings("unchecked")
    private void updateUsers() throws IOException {
        ObjectOutputStream out;
        ArrayList<String> docs = new ArrayList<>(documents);
        //Sorts the document list in alphabetical order
        Collections.sort(docs);

        //For each client
        for (String clientName : clientInfos.keySet()) {
            if (clientName == null) continue;
            //retrieve the sockets for the client
            Pair<Socket, Boolean> p = clientInfos.get(clientName).socket;
            if (p == null) continue;
            Socket currentSocket = p.first;
            Boolean activeSocket = p.second;

            //Connection is already closed, So we don't send.
            if (!activeSocket) continue;

            //Makes sure socket exists in map
            if (!socketStreams.containsKey(currentSocket)) {
                throw new RuntimeException("Socket not found in HashMap");
            }

            out = socketStreams.get(currentSocket).second;
            //Creates a cloned pair of usernames and documents
            Pair<ArrayList<String>, ArrayList<String>> pair = new Pair<>(
                    (ArrayList<String>) usernames.clone(),
                    filteredDocumentList(clientName));

            //Sends it to the client
            out.writeObject(pair);
            out.flush();
        }
    }
}