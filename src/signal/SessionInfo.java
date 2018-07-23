package signal;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import org.whispersystems.libsignal.ecc.DjbECPublicKey;
import org.whispersystems.libsignal.state.PreKeyBundle;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

//A session info is to registration info what a list of prekeybundles is to a prekeybundle.
//It is the same as registration info but containing only one bundle.

public class SessionInfo implements Serializable {
    private static final long serialVersionUID = 1338;

    public PreKeyBundle preKey;
    //The user and document this session was created for.
    public String senderID;
    public String documentID;

    public SessionInfo(PreKeyBundle preKey, String senderID, String documentID) {
        this.preKey = preKey;
        this.senderID = senderID;
        this.documentID = documentID;
    }

    //serialization methods

    private void writeObject(ObjectOutputStream out) throws IOException {
        Class<?>[] classes = new Class[] { PreKeyBundle.class, DjbECPublicKey.class};
        XStream xs = new XStream(new DomDriver());
        XStream.setupDefaultSecurity(xs);
        //xs.allowTypesByWildcard(new String[] { "org.whispersystems.libsignal.*"});
        xs.allowTypes(classes);
        String xml = xs.toXML(preKey);
        out.writeObject(xml);
        out.flush();
        out.writeObject(senderID);
        out.flush();
        out.writeObject(documentID);
        out.flush();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        Class<?>[] classes = new Class[] {PreKeyBundle.class, DjbECPublicKey.class};

        XStream xs = new XStream(new DomDriver());
        XStream.setupDefaultSecurity(xs);
        xs.allowTypes(classes);
      //  xs.allowTypesByWildcard(new String[] { "org.whispersystems.libsignal.*"});
        String xml = (String) in.readObject();
        preKey = (PreKeyBundle) xs.fromXML(xml);
        senderID = (String) in.readObject();
        documentID = (String) in.readObject();
    }
}
