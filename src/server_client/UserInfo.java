package server_client;

import document.Pair;
import signal.EncryptedMessage;
import signal.RegistrationInfo;
import signal.SessionInfo;

import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

//database for all the information the server knows about a client.

class UserInfo {
    //client socket and whether it's active or not
    public Pair<Socket, Boolean> socket;
    public RegistrationInfo registrationInfo;
    public final ArrayList<SessionInfo> sessionInfos;
    public String currentDocument;

    //hashmap between document name and its history
    public final HashMap<String, ArrayList<EncryptedMessage>> histories;

    //constructor initializes some structures. The server sets the information as it gets information about the client.
    public UserInfo() {
        histories = new HashMap<>();
        sessionInfos = new ArrayList<>();
    }
}
