package server_client;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Array;
import java.util.*;

import javax.crypto.SealedObject;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.JFrame;
import javax.swing.text.BadLocationException;


import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import document.*;
import gui.ErrorDialog;
import gui.ServerGui;
import signal.EncryptedMessage;
import signal.RegistrationInfo;
import signal.SessionInfo;

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

public class CollabServer implements CollabInterface {

    /**
     * maximum number of clients allowed at a time
     */
    private static final int MAX_CLIENTS = 30;
    /**
     * lock object
     */
    Object lock = new Object();
    /**
     * server socket that accepts client connections
     */
    private ServerSocket serverSocket;
    /**
     * number of clients actively connected
     */
    private int users = 0;
    /**
     * port number of the server
     */
    private int port = 0;
    /**
     * IP address of the server
     */
    private String ip = "";
    /**
     * name of the server
     */
    private String serverName;
    /**
     * JFrame used by the server GUI for display
     */
    private JFrame frame;
    /**
     * GUI object for the server
     */
    private ServerGui displayGui;
    /**
     * order of the operations
     */
    private int order;
    /**
     * Associates a user with a socket. Boolean for if its active.
     */
    private HashMap<String, Pair<Socket, Boolean>> clientSockets = new HashMap<>();

    int clientID = 0; //TODO: look for other alternatives for this

    /**
     * A hash of socket to its associated input/output streams. We want to use these
     * two streams each time we need to communicate between a particular client and the server
     */
    private HashMap<Socket, Pair<ObjectInputStream, ObjectOutputStream>> socketStreams = new HashMap<>();

    //stores the registration info for each users
    private HashMap<String, RegistrationInfo> clientRegistrationInfo =  new HashMap<>();

    //stores sessioninfos that are yet to be sent per user.
    private HashMap<String, List<SessionInfo>> clientSessions = new HashMap<>();

    /**
     * List of all users
     */
    private ArrayList<String> usernames = new ArrayList<>();


    // TODO: Change documents to be a map from ID to string + history. REMOVE THIS
    /**
     * List of all documents
     */
    private HashMap<String, ServerGui> documents = new HashMap<>();

    // TODO: Put history and documentInstance for every doc. For only one for testing
    private HashMap<String, String[]> clientLists = new HashMap<>();
    //private HashMap<String, ArrayList<Object>> histories = new HashMap<>();
    private HashMap<String, DocumentInstance> documentInstances = new HashMap<>();
    private HashMap<String, String> clientCurrentDocument = new HashMap<>();
    private HashMap<Pair<String, String>, ArrayList<EncryptedMessage>> histories = new HashMap<>();
//    private int accept = 0;

    /**
     * Constructor for making a server. It will set the port number, create a
     * server socket, and generate a central GUI.
     * List of documents and socket names are also initialized
     *
     * @param port - targeted port number
     * @param IP   - targeted IP address
     */
    public CollabServer(String IP, int port, String name) {
        // Sets server info
        this.port = port;
//        this.serverName = DEFAULT_DOC_NAME;
        this.ip = IP;
        // Create a server socket for clients to connect to
        try {
            this.serverSocket = new ServerSocket(this.port);
        } catch (IOException e) {
            System.err.println("Cannot create server socket at IP: " + this.ip
                    + ", port: " + this.port);
            return;
        }

        // The server is viewed as the zeroth socket
        clientSockets.put(null, null);
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
        } catch (IOException e) {
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
    public void serve() throws IOException {
//        frame = new JFrame("Collab Edit Demo");
//        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // Add content to the window.
//        frame.add(this.displayGui);

        // Display the window.
//        frame.pack();
//        frame.setVisible(true);

        while (true) {
            if (this.users > MAX_CLIENTS) {
                throw new RuntimeException("Too many clients!");
            }
            // block until a client connects
            try {
                final Socket socket = serverSocket.accept();

                // New client detected; create new thread to handle each connection
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
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
    public void handleConnection(Socket socket) throws IOException {
        String documentID = "";
        String clientName = "";
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

        // Add stream to HashMap
        socketStreams.put(socket, new Pair<>(in, out));

        try {
            Object input;
            //waits for client to write his name and saves it along with registration info of the user
            input = in.readObject();
            if (input instanceof Pair) {
                Pair p = (Pair) input;
                clientName = (String) p.first;

                clientRegistrationInfo.put(clientName, (RegistrationInfo) p.second);
            }
            else {
                throw new IOException("Expected client name");
            }

            //Stays here waiting for the user to choose a document. (his other option is pressing F5)
            while(true) {
                clientCurrentDocument.put(clientName, "");

                // Sends list of documents to client
                ArrayList<String> clientDocuments = filteredList(clientName);
                out.writeObject(clientDocuments);
                out.flush();

                // Receives which document to edit or F5 request
                input = in.readObject();
                if (!(input instanceof String)) {
                    throw new RuntimeException("Expected document name or documents request");
                }
                else if(!input.equals("give documents please thanks")) { //Shouldn't be a string because someone can name his document this but whatever
                    break;
                }
            }

            synchronized (lock) {
                // If document does not exist, create it
                // TODO: client crashes if you use an existing document name. Maybe fix
                documentID = (String) input;
                clientCurrentDocument.put(clientName, documentID);
                //if new document
                if (!documents.containsKey(documentID)) {
                    documentInstances.put(documentID, new DocumentInstance(""));
                    input = in.readObject();
                    if (input instanceof String[]) {
                        String[] clientList = (String[]) input;
                        clientLists.put(documentID, (String[]) input);

                        //build sessioninfo for document creator with the rest of the users. Assumes everyone is registered.
                        for(String client : clientList) {
                            clientSessions.put(client, new ArrayList<>());
                            histories.put(new Pair<>(client, documentID), new ArrayList<>());
                            if(clientSockets.get(client) == null) clientSockets.put(client, new Pair(null, false)); //don't know the client's socket yet
                        }

                        for(String client : clientList) {
                            if(clientRegistrationInfo.get(client) == null) {
                                throw new IllegalArgumentException("Client in list does not exist. Add error checking to handle this instead of an exception later.");
                            }
                            RegistrationInfo registrationInfo = clientRegistrationInfo.get(client);
                            for(String otherClient : clientList) {
                                if(otherClient.equals(client)) continue;
                                SessionInfo session = registrationInfo.createSessionInfo(client, documentID); //TODO: CHECK
                                clientSessions.get(otherClient).add(session);
                                //TODO: functionality if user is not online when session is created
                            }
                            //if this is the creator of the document, send him the sessions now. Else, store them.
                        }
                        //TODO: remove sessions when user joins and builds them so he doesnt build multiple times
                    }
                    else {
                        throw new IOException("Expected client list");
                    }

                    // TODO: At some point remove this. WE DON'T WANT SERVER GUI
                    ServerGui newGui = new ServerGui(this, documentID);
                    newGui.setModelKey(documentID);
                    documents.put(documentID, newGui);
                }
                this.users++;
                //set user as joined and associate with his socket
                clientSockets.put(clientName, new Pair(socket, true));
                System.out.println("clientsockets " + clientName + " activesocket is now " + clientSockets.get(clientName).second);

                // sets client ID
                clientID++;
            }

//            if (!documents.containsKey(documentID)) {
//                throw new RuntimeException("Missing document ID in map");
//            }
            out.writeObject(clientID);
            out.flush();  //TODO: CHECK THAT THIS CAN BE OUTSIDE THE LOCK
            out.writeObject(clientSessions.get(clientName));
            out.flush();
            // Sends to client the client ID


            Pair<String, String> pair = new Pair<>(clientName, documentID);

            //send the client the history of the document.
            out.writeObject(new Pair(documentInstances.get(documentID), histories.get(pair)));
            out.flush();

            histories.get(pair).clear();

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
//        } catch (OperationEngineException e) {
//            e.printStackTrace();
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
                    clientSockets.get(clientID).second = false;
                    clientCurrentDocument.put(clientName, ""); //TODO: fix it so it updates
                    this.users--;
                    usernames.remove(clientName);
                    // need to update the view of who still in the edit room
                    documents.get(documentID).updateUsers(this.usernames.toArray());
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

    //Returns a list of all documents given client has authorization to see
    public ArrayList<String> filteredList (String clientName) {
        ArrayList<String> clientDocuments = new ArrayList<>();
        for (String document : documents.keySet()) {
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
    public void parseInput(Object input, String documentID) throws IOException {
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
    public void transmit(Object o, String documentID) throws IOException {
        ObjectOutputStream out;
        // Increment the order so the Operation Engine can determine
        // the relative position of all the operations
        EncryptedMessage[] messages = (EncryptedMessage[]) o;
        synchronized (lock) {
            //histories.get(documentID).add(o); //TODO: change when history changes
            //delivery = new Pair<>(o, order);
            for(int i = 0; i < messages.length; i++) {
                messages[i].setOrder(order);
            }
            order++;
        }

        for(int i = 0; i < messages.length; i++) {
            EncryptedMessage message = messages[i];
            Pair<Socket, Boolean> p = clientSockets.get(message.recipientID);
            Socket currentSocket = p.first;
            Boolean activeSocket = p.second;
            if (clientCurrentDocument.get(message.recipientID).equals(documentID)) {
                System.out.println(documentID + " " + clientCurrentDocument.get(message.recipientID));
                System.out.println("Sent message from " + message.senderID + " to " + message.recipientID);
                out = socketStreams.get(currentSocket).second;
                out.writeObject(message);
                out.flush();
            }
            else {
                System.out.println("message from " + message.senderID + " to " + message.recipientID + " added to history");
                System.out.println(!activeSocket);
                //TODO: fix clientsockets so it updates activesocket correctly
                System.out.println(!clientCurrentDocument.get(message.recipientID).equals(documentID));
                Pair<String, String> pair = new Pair<>(message.recipientID, documentID);
                histories.get(pair).add(message);
            }
        }

/*        // send operation to each one of the clients
        for (String clientName : clientSockets.keySet()) {
            if(clientName == null) continue; //do not send to server
            Pair<Socket, Boolean> p = clientSockets.get(clientName);
            Socket currentSocket = p.first;
            Boolean activeSocket = p.second;

            // Connection is already closed, or this is the operation that was
            // originally sent by that client. So we don't send.
            if (!activeSocket) {
                continue;
            }

            // Otherwise we send the operation
            out = socketStreams.get(currentSocket).second;
            out.writeObject(delivery);
            out.flush();
        }*/
    }

    /**
     * Sends a Pair of users and documents to all active clients to update the
     * right pane. It is called each time a new client joins or leaves
     *
     * @throws IOException - if the socket connection is corrupted
     */
    @SuppressWarnings("unchecked")
    public void updateUsers() throws IOException {
        ObjectOutputStream out = null;
//        this.displayGui.updateUsers(((ArrayList<String>) usernames.clone()).toArray());
        ArrayList<String> docs = new ArrayList<String>();
        docs.addAll(documents.keySet());
        // Sorts the document list in alphabetical order
        Collections.sort(docs);
//        this.displayGui.updateDocumentsList(docs.toArray());

        // For each client
        for (String clientName : clientSockets.keySet()) {
            if(clientName == null) continue;
            // retrieve the sockets for the client
            Pair<Socket, Boolean> p = clientSockets.get(clientName);
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
                     filteredList(clientName));

            // Sends it to the client
            out.writeObject(pair);
            out.flush();
        }
    }

    /**
     * Switches between documents to be displayed on the server. This happens
     * when a user clicks on a document name on the server GUI.
     *
     * @param document - the document name to be switched
     */
    public synchronized void switchScreen(String document) {
        // TODO: remove
        if (!documents.containsKey(document))
            throw new RuntimeException("Document not found!");
        this.displayGui = documents.get(document);
        this.displayGui.updateUsers(this.usernames.toArray());
        ArrayList<String> temp = new ArrayList<String>();
        temp.addAll(this.getDocumentsName());
        // Sorts the document list in alphabetical order
        Collections.sort(temp);
        this.displayGui.updateDocumentsList(temp.toArray());
        this.displayGui.repaint();
        this.displayGui.revalidate();

        frame.setContentPane(displayGui);
        frame.setVisible(true);

    }


    /**
     * @return the ID of the server, which is always 0
     */
    @Override
    public int getID() {
        return 0;
    }

    @Override
    public void transmit(Object op) throws IOException {
        //We have no use for this because I changed how transmit works (and we will need to change it again later for pairwise encryption) but the interface demands this
    }

    @Override
    public void transmit(Object o, CollabClient.ENCRYPTION_METHOD encryption) throws IOException {
        //used only for client, bad structure
    }

    /**
     * Get the list of documents currently store in the model
     *
     * @return the list of documents
     */
    public Set<String> getDocumentsName() {
        return documents.keySet();
    }

    /**
     * @return ip address of server
     */
    public String getIP() {
        return this.ip;
    }

    /**
     * @return port number of server
     */
    public int getPort() {
        return this.port;
    }

    /**
     * @return username of server
     */
    public String getUsername() {
        return this.serverName;
    }

    /**
     * @return HashMap of documents to ServerGuis
     */
    public HashMap<String, ServerGui> getDocuments() {
        return this.documents;
    }

    /**
     * @return serverSockets
     */
    public ServerSocket getServerSocket() {
        return this.serverSocket;
    }

    /**
     * @return order of server
     */
    public int getOrder() {
        return this.order;
    }

    /**
     * @return number of users
     */
    public int getNumOfUsers() {
        return this.users;
    }

    /**
     * set number of users
     *
     * @param users number of users
     */
    public void setNumOfUsers(int users) {
        this.users = users;
    }
}





