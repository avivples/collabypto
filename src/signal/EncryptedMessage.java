package signal;

import org.whispersystems.libsignal.protocol.CiphertextMessage;

import javax.crypto.Cipher;
import java.io.Serializable;

//represents an encrypted signal message to be sent to the server
public class EncryptedMessage implements Serializable {
    private static final long serialVersionUID = 1339;

    public String recipientID;
    public String senderID;
    public String documentID;
    public byte[] message;
    public int order;

    public EncryptedMessage(String recipientID, String senderID, String documentID, byte[] message) {
        this.recipientID = recipientID;
        this.senderID = senderID;
        this.documentID = documentID;
        this.message = message;
    }

    public void setOrder(int order) {
        this.order = order;
    }
}
