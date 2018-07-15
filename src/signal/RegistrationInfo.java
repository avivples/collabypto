package signal;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import org.whispersystems.libsignal.*;
import org.whispersystems.curve25519.*;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.DjbECPublicKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.state.impl.InMemorySignalProtocolStore;
import org.whispersystems.libsignal.util.KeyHelper;
import org.whispersystems.libsignal.util.Medium;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;

public class RegistrationInfo implements Serializable {
    private static final long serialVersionUID = 1337;

    PreKeyBundle[] preKeyBundles;

    //for now we assume that prekeys dont run out. In the future it is possible to implement creation of new prekeys when the supply is running low.
    //The following variable stores the
    int index;

    //Array that stores the ids of used prekeys, to send to the client when he returns so he knows what prekeys to remove.
    //PreKeyBundle[] usedPreKeys;

    public RegistrationInfo( List<PreKeyBundle> preKeyBundles) {
        this.preKeyBundles = preKeyBundles.toArray(new PreKeyBundle[preKeyBundles.size()]);
        index = 0;
       // usedPreKeys = new PreKeyBundle[100];
    }

    //makes a session info using a prekey and the rest of the information. Adds the key to the used list and increments index
    public SessionInfo createSessionInfo(String senderID, String documentID) {
        PreKeyBundle preKey = preKeyBundles[index];
      //  usedPreKeys[index] = preKey;
        index++;
       // System.out.println("sender: " + senderID + " " + preKey.getPreKeyId());
        return new SessionInfo(preKey, senderID, documentID);
    }

    public void clearUsedPreKeys() {
    //    this.usedPreKeys = new PreKeyBundle[100];
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        Class<?>[] classes = new Class[] { PreKeyBundle.class, DjbECPublicKey.class};
        XStream xs = new XStream(new DomDriver());
        XStream.setupDefaultSecurity(xs);
        xs.allowTypes(classes);
      //  xs.allowTypesByWildcard(new String[] { "org.whispersystems.libsignal.*"});
        String xml = xs.toXML(preKeyBundles);
        out.writeObject(xml);
        out.flush();
        out.writeInt(index);
        out.flush();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        Class<?>[] classes = new Class[] { PreKeyBundle.class, DjbECPublicKey.class};
        XStream xs = new XStream(new DomDriver());
        XStream.setupDefaultSecurity(xs);
        xs.allowTypes(classes);
      //  xs.allowTypesByWildcard(new String[] { "org.whispersystems.libsignal.*"});
        String xml = (String) in.readObject();
        preKeyBundles = (PreKeyBundle[]) xs.fromXML(xml);
        index = in.readInt();
    }

}
