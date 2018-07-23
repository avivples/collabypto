package signal;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import org.whispersystems.libsignal.ecc.DjbECPublicKey;
import org.whispersystems.libsignal.state.PreKeyBundle;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;

public class RegistrationInfo implements Serializable {
    private static final long serialVersionUID = 1337;

    private PreKeyBundle[] preKeyBundles;
    //for now we assume that prekeys dont run out. In the future it is possible to implement creation of new prekeys when the supply is running low.

    //stores index of earliest unused prekey
    private int index;


    public RegistrationInfo( List<PreKeyBundle> preKeyBundles) {
        this.preKeyBundles = preKeyBundles.toArray(new PreKeyBundle[preKeyBundles.size()]);
        index = 0;
    }

    //makes a session info using a prekey and the rest of the information. Adds the key to the used list and increments index
    public SessionInfo createSessionInfo(String senderID, String documentID) {
        PreKeyBundle preKey = preKeyBundles[index];
        index++;
        return new SessionInfo(preKey, senderID, documentID);
    }

    //serialization methods

    private void writeObject(ObjectOutputStream out) throws IOException {
        Class<?>[] classes = new Class[] { PreKeyBundle.class, DjbECPublicKey.class};
        XStream xs = new XStream(new DomDriver());
        XStream.setupDefaultSecurity(xs);
        xs.allowTypes(classes);
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
        String xml = (String) in.readObject();
        preKeyBundles = (PreKeyBundle[]) xs.fromXML(xml);
        index = in.readInt();
    }

}
