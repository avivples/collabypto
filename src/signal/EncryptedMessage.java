package signal;

import java.io.Serializable;

//represents an encrypted signal message to be sent to the server
public class EncryptedMessage implements Serializable {
    private static final long serialVersionUID = 1339;

    public final String recipientID;
    public final String senderID;
    public final byte[] message;
    public int order;

    public EncryptedMessage(String recipientID, String senderID,  byte[] message) {
        this.recipientID = recipientID;
        this.senderID = senderID;
        this.message = message;
    }

    public void setOrder(int order) {
        this.order = order;
    }
}
