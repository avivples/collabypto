package signal;

import org.whispersystems.libsignal.SessionCipher;

public class ClientSessionCipher {
    //stores a session cipher along with information on who is in the session and what document the session is for.

    public SessionCipher sessionCipher;
    public String senderID;
    public String documentID;

    public ClientSessionCipher(SessionCipher sessionCipher, String senderID, String documentID) {
        this.sessionCipher = sessionCipher;
        this.senderID = senderID;
        this.documentID = documentID;
    }
}
