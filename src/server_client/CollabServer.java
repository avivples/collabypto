package server_client;

import document.DocumentInstance;
import document.Operation;
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
 *
 * Creates a server that will store the main copy of each document. It will block
 * until a client connects, and then send a copy of the requested document to the client.
 * It will start a new thread to handle each new client connection. Each time a
 * client makes a change to the document, it will send it to the server, which
 * will process the change using the operational transform algorithm, and then
 * relay it to the rest of the clients.
 *
 * Note: a server must be present before a client can connect. A server's
 * document is not editable by the user
 *
 *
 */

@SuppressWarnings("ConstantConditions")
public class CollabServer implements CollabInterface {

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
     * IP address of the server
     */
    private final String ip = "";

    /**
     * order of the operations
     */
    private int order;
    /**
     * Associates a user with a socket. Boolean for if its active.
     */

    private int clientID = 0; //TODO: look for other alternatives for this

    /**
     * A hash of socket to its associated input/output streams. We want to use these
     * two streams each time we need to communicate between a particular client and the server
     */
    private final HashMap<Socket, Pair<ObjectInputStream, ObjectOutputStream>> socketStreams = new HashMap<>();



    //List of all online users

    private final ArrayList<String> usernames = new ArrayList<>();



     //List of all documents

    private final ArrayList<String> documents = new ArrayList<>();

    private final HashMap<String, String[]> clientLists = new HashMap<>();
    private final HashMap<String, DocumentInstance> documentInstances = new HashMap<>();

    private final HashMap<String, UserInfo> clientInfos = new HashMap<>();
    private final ArrayList<String> tokens = new ArrayList<>();

    /**
     * Constructor for making a server. It will set the port number, create a
     * server socket, and generate a central GUI.
     * List of documents and socket names are also initialized
     *
     * @param port - targeted port number
     * @param IP   - targeted IP address
     */
    public CollabServer(String IP, int port) {
        // Sets server info
        // Create a server socket for clients to connect to
        try {
            this.serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            System.err.println("Cannot create server socket at IP: " + ip + ", port: " + port);
            return;
        }

        System.out.println("Server created.");
    }

    /**
     * start up a server.
     * by calling the connect() method.
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
     * Starts up a non-editable GUI dedicated to handling client connections.
     * Never returns unless an exception is thrown. Creates a new thread for
     * every new connection.
     *
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
     * closing all the appropriate sockets and streams. This is where various
     * information passing will be done between the client and server.
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
            //waits for client to write his name and saves it along with registration info of the user
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

                    //client sent indication that he is returning even though he is new
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

                // Receives which document to edit
                input = in.readObject();
                if(input instanceof String) {
                    if(input.equals("refresh")) {
                        // Sends list of documents to client
                        clientDocuments = filteredDocumentList(clientName);
                        out.writeObject(clientDocuments);
                        out.flush();
                    }
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
                // If document does not exist, create it
                if (!documents.contains(documentID)) {
                    documents.add(documentID);
                    documentInstances.put(documentID, new DocumentInstance(""));
                    input = in.readObject();
                    if (input instanceof ArrayList) {
                        String[] clientList = ((ArrayList<String>)input).toArray(new String[0]);
                        clientLists.put(documentID, clientList);

                        //build sessioninfo for document creator with the rest of the users. Assumes everyone is registered.
                        for(String client : clientList) {
                            UserInfo curUser = clientInfos.get(client);
                            if(clientInfos.get(client).registrationInfo == null) {
                                throw new IllegalArgumentException("Client in list does not exist. Add error checking to handle this instead of an exception later.");
                            }
                            curUser.histories.put(documentID, new ArrayList<>());
                            if(curUser.socket == null) curUser.socket = new Pair(null, false); //don't know the client's socket yet
                        }

                        for(String client : clientList) {
                            UserInfo curUser = clientInfos.get(client);
                            RegistrationInfo registrationInfo = curUser.registrationInfo;

                            for(String otherClient : clientList) {
                                if(otherClient.equals(client)) continue;
                                SessionInfo session = registrationInfo.createSessionInfo(client, documentID);
                                clientInfos.get(otherClient).sessionInfos.add(session);
                            }
                            //if this is the creator of the document, send him the sessions now. Else, store them.
                        }
                    }
                    else {
                        throw new IOException("Expected client list");
                    }
                }
                this.users++;
                //set user as joined and associate with his socket
                // TODO: move this to the correct place without breaking everything
                clientInfo.socket = new Pair<>(socket, true);

                // sets client ID
                clientID++;
            }

//            if (!documents.containsKey(documentID)) {
//                throw new RuntimeException("Missing document ID in map");
//            }
            out.writeObject(clientID);
            out.flush();
            if(!returningUser) {
                //TODO: remove unneccessary session infos?
                out.writeObject(clientInfos.get(clientName).sessionInfos);
                out.flush();
            }
            // Sends to client the client ID

            //send the client the history of the document.
            out.writeObject(new Pair(documentInstances.get(documentID), clientInfo.histories.get(documentID)));
            out.flush();

            clientInfo.histories.get(documentID).clear();

            // Receives username of client. Updates users.
            input = in.readObject();
            if (!(input instanceof String)) {
                throw new RuntimeException("Expected client username");
            }
//            clientName = (String) input;
            synchronized (lock) {
                usernames.add(clientName);
            }
            updateUsers();
            // Receives operations from client. That's all the server is
            // expecting from
            // the client from now on.
            input = in.readObject();
            while (input != null) {
                parseInput(input, documentID);
                input = in.readObject();
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (Exception e) {
            // TODO: Remove this when we don't care about errors in collabserver
            e.printStackTrace();
            return;
        } finally {
            // Clean up, close connections
            if (clientID == -1) {
                return;
            }
            // set connection as closed
            System.out.println("Connection to client #" + clientID + " lost.");
            synchronized (lock) {
                try {
                    // set client as inactive
                    clientInfo.socket.second = false;
                    clientInfo.currentDocument = "";
                    this.users--;
                    usernames.remove(clientName);
                    // need to update the view of who still in the edit room
                    //documents.get(documents.indexOf(documentID)).updateUsers(this.usernames.toArray()); //TODO
                } catch (Exception e) {
                    System.err.println("Client not found");
                }
            }
            // update user lists
            updateUsers();
            out.close();
            in.close();
        }
    }

    public String generateToken() {
        String generatedString = RandomStringUtils.randomAlphanumeric(10);
        tokens.add(generatedString);
        return generatedString;
    }

    //Returns a list of all documents given client has authorization to see
    private ArrayList<String> filteredDocumentList (String clientName) {
        ArrayList<String> clientDocuments = new ArrayList<>();
        for (String document : documents) {
            String[] clientList = clientLists.get(document);
            for (int i = 0; i < clientList.length; i++) {
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
     * should be an operation that was performed on the client. The server
     * should then update its corresponding document and notify all clients
     *
     * @param input      - object sent from the client to parse
     * @param documentID - the document that the client is editing
     * @throws IOException - caused if the operation object is corrupt, or if the socket
     *                     connection breaks
     */
    private void parseInput(Object input, String documentID) throws IOException {
        transmit(input, documentID); // also mutates the input
    }


    /**
     * Updates the copy of the document using operational transform through a
     * call to the CollabModel's remoteInsert/remoteDelete
     *
     * @param o - the operation that was received from the client to apply to
     *          the model
     */
    @Override
    public void updateDoc(Operation o) {

    }


    /**
     * After a change by a client is received, this method will be called to
     * send the changes to all the other clients, who will then apply their
     * own OT algorithm to generate the most recent copy of the document.
     *
     * @throws IOException if the input/output stream is corrupt
     */
    private void transmit(Object o, String documentID) throws IOException {
        ObjectOutputStream out;
        // Increment the order so the Operation Engine can determine
        // the relative position of all the operations
        EncryptedMessage[] messages = (EncryptedMessage[]) o;
        synchronized (lock) {
            for(int i = 0; i < messages.length; i++) {
                messages[i].setOrder(order);
            }
            order++;
        }

        for(int i = 0; i < messages.length; i++) {
            EncryptedMessage message = messages[i];
            UserInfo recipientInfo = clientInfos.get(message.recipientID);
            Pair<Socket, Boolean> p = recipientInfo.socket;
            Socket currentSocket = p.first;
            Boolean activeSocket = p.second;
            if (recipientInfo.currentDocument.equals(documentID)) {
                System.out.println("Sent message from " + message.senderID + " to " + message.recipientID);
                out = socketStreams.get(currentSocket).second;
                out.writeObject(message);
                out.flush();
            }
            else {
                System.out.println("message from " + message.senderID + " to " + message.recipientID + " added to history");
                //TODO: fix clientsockets so it updates activesocket correctly
                recipientInfo.histories.get(documentID).add(message);
            }
        }
    }

    /**
     * Sends a Pair of users and documents to all active clients to update the
     * right pane. It is called each time a new client joins or leaves
     *
     * @throws IOException - if the socket connection is corrupted
     */
    @SuppressWarnings("unchecked")
    private void updateUsers() throws IOException {
        ObjectOutputStream out;
//        this.displayGui.updateUsers(((ArrayList<String>) usernames.clone()).toArray());
        ArrayList<String> docs = new ArrayList<>(documents);
        // Sorts the document list in alphabetical order
        Collections.sort(docs);
//        this.displayGui.updateDocumentsList(docs.toArray());

        // For each client
        for (String clientName : clientInfos.keySet()) {
            if (clientName == null) continue;
            // retrieve the sockets for the client
            Pair<Socket, Boolean> p = clientInfos.get(clientName).socket;
            if (p == null) continue;
            Socket currentSocket = p.first;
            Boolean activeSocket = p.second;

            // Connection is already closed, So we don't send.
            if (!activeSocket) continue;

            // Makes sure socket exists in map
            if (!socketStreams.containsKey(currentSocket)) {
                throw new RuntimeException("Socket not found in HashMap");
            }
            out = socketStreams.get(currentSocket).second;
            // Creates a cloned pair of usernames and documents
            Pair<ArrayList<String>, ArrayList<String>> pair = new Pair<>(
                    (ArrayList<String>) usernames.clone(),
                    filteredDocumentList(clientName));

            // Sends it to the client
            out.writeObject(pair);
            out.flush();
        }
    }

    /**
     * @return the ID of the server, which is always 0
     */
    @Override
    public int getID() {
        return 0;
    }

    @Override
    public void transmit(Object op) {
        //We have no use for this because I changed how transmit works (and we will need to change it again later for pairwise encryption) but the interface demands this
    }

    @Override
    public void transmit(Object o, CollabClient.ENCRYPTION_METHOD encryption) {
        //used only for client, bad structure
    }

}