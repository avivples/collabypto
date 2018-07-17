package server_client;

import document.Pair;
import signal.EncryptedMessage;
import signal.RegistrationInfo;
import signal.SessionInfo;

import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

//database for all the information the server knows about a client

public class UserInfo {
    //client socket and whether it's active or not
    public Pair<Socket, Boolean> socket;
    public RegistrationInfo registrationInfo;
    public ArrayList<SessionInfo> sessionInfos;
    public String currentDocument;

    //hashmap between document name and its history
    public HashMap<String, ArrayList<EncryptedMessage>> histories;

    public UserInfo() {
        histories = new HashMap<>();
        sessionInfos = new ArrayList<>();
    }
}
