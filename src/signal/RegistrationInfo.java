package signal;

import org.whispersystems.libsignal.*;
import org.whispersystems.curve25519.*;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.state.impl.InMemorySignalProtocolStore;
import org.whispersystems.libsignal.util.KeyHelper;
import org.whispersystems.libsignal.util.Medium;

public class RegistrationInfo {

    int registrationId;
    int startId;
    ECPublicKey[] preKeyPublic;
    ECPublicKey signedPreKeyPublic;
    byte[] signedPreKeySignature;
    ECPublicKey identityKeyPublic;

    public RegistrationInfo(int registrationId, int startId, ECPublicKey[] preKeyPublic,
                       ECPublicKey signedPreKeyPublic, byte[] signedPreKeySignature,
                       ECPublicKey identityKeyPublic) {

        this.registrationId = registrationId;
        this.startId = startId;
        this.preKeyPublic = preKeyPublic;
        this.signedPreKeyPublic = signedPreKeyPublic;
        this.signedPreKeySignature = signedPreKeySignature;
        this.identityKeyPublic = identityKeyPublic;

    }

}
