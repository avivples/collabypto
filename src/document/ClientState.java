package document;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

/**
 * This vector will be the representation of our state machine:
 * A data representation of our state machine will be an integer array. e.g:
 * 
 * State machine1 --> [0, 1, 2, 3, 4, 5]
 * State machine2 --> [1, 2, 3, 4, 5]
 * 
 * The index corresponds to the ClientID. The integer value at that position
 * represents the number of operations processed in this state that are sent
 * from clientID denoted by index position.
 *
 */
public class ClientState implements Serializable {


    private static final long serialVersionUID = 5715462582805781166L;
    
    // holds all the information of the state of the location originating
    private int[] clients;

    public ClientState(Map<String, Object> properties) throws OperationEngineException {
        if (properties.containsKey("count")) {
            this.clients = new int[(Integer) properties.get("count")];
        }
        else if (properties.containsKey("contextVector")) {
            this.clients = ((ClientState) properties.get("contextVector")).copyClients();
        }
        else if (properties.containsKey("sites")) {
            this.clients = (int[]) properties.get("sites");
        }
        else if (properties.containsKey("state")) {
            this.clients = (int[]) properties.get("state");
        }
        else {
            throw new OperationEngineException("Uninitialized context vector");
        }
    }

    /**
     * Provides a public copy of the current state.
     * 
     * @return Array of integer sequence numbers
     */
    public int[] getState() {
        return this.clients;
    }

    /**
     * Makes a copy of this ClientState.
     * 
     * @return Copy of this ClientState
     * @throws OperationEngineException
     */
    public ClientState copy() throws OperationEngineException {
        HashMap<String, Object> properties = new HashMap<String, Object>();
        properties.put("contextVector", this);
        return new ClientState(properties);
    }

    /**
     * Makes a copy of the array in this ClientState.
     */
    public int[] copyClients() {
        return Arrays.copyOf(this.clients, this.clients.length);
    }

    /**
     * Increases the size of the ClientState to the given size. Initializes
     * new entries with zeros. This is useful for when a new client joins, we
     * can include that client in our state by growing the array.
     * 
     * @param count size of new state
     */
    public void growTo(int count) {
        int l = this.clients.length;
        if (l < count) {
            int[] newClients = new int[count];
            for (int i = 0; i < count; i++) {
                if (i < l) {
                    newClients[i] = this.clients[i];
                } else {
                    newClients[i] = 0;
                }
            }
            this.clients = newClients;
        }
    }

    /**
     * Gets the sequence number for the given site in this ClientState. Grows
     * the ClientState if it does not include the site yet.
     * 
     * @param client index to grow to
     * @return Integer sequence number for the site
     */
    public int getSeqForClient(int client) {
        if (client < 0) {
            throw new ArrayIndexOutOfBoundsException("Improper client input.");
        }
        if (this.clients.length <= client) {
            this.growTo(client + 1);
        }
        return this.clients[client];
    }

    /**
     * Sets the sequence number for the given client id (site) in this ClientState.
     * Grows the ClientState if it does not include the site yet.
     * 
     * @param client client ID (site)
     * @param seq sequence number
     */
    public void setSeqForClient(int client, int seq) {
        if (client < 0) {
            throw new ArrayIndexOutOfBoundsException("Improper client input.");
        }
        if (this.clients.length <= client) {
            this.growTo(client + 1);
        }
        this.clients[client] = seq;
    }

    /**
     * Gets the size of this ClientState.
     * @return size
     */
    public int getSize() {
        return this.clients.length;
    }

    /**
     * Computes the difference in sequence numbers at each site between this
     * ClientState and the one provided. This will be used in the
     * transform algorithm to determine the offset.
     *
     * @param cv the other context vector
     * @return A StateDifference object representing what operations are
     *         different between these two states
     */
    public StateDifference subtract(ClientState cv) {
        StateDifference sd = new StateDifference();
        for (int i = 0; i < this.clients.length; i++) {
            int a = this.getSeqForClient(i);
            int b = cv.getSeqForClient(i);
            if (a - b > 0) {
                sd.addRange(i, b + 1, a + 1);
            }
        }
        return sd;
    }

    /**
     * Checks if the ClientState values contain the same sequence IDs.
     * 
     * @param cv the other context vector
     * @return True if equal, false otherwise
     */
    public boolean equals(ClientState cv) {
        int[] a = this.clients;
        int[] b = cv.clients;

        // account for different size ClientStates
        int max = Math.max(a.length, b.length);
        for (int i = 0; i < max; i++) {
            int va = 0;
            int vb = 0;
            if (i < a.length) va = a[i];
            if (i < b.length) vb = b[i];
            if (va != vb)  return false;
        }
        return true;
    }

    /**
     * This will be a comparator function to see if the ClientState values contain
     * the same sequence IDs.
     * If a single sequence is less than the other, then this state occurs
     * before the other state
     *
     * @param cv  other client state (context vector)
     * @return -1 if this ClientState is ordered before the other, 0 if they
     *         are equal, or 1 if this ClientState is ordered after the other
     */
    public int compare(ClientState cv) {
        int[] a = this.clients;
        int[] b = cv.clients;
        // acount for different size ClientStates
        int max = Math.max(a.length, b.length);
        for (int i = 0; i < max; i++) {
            int va = 0;
            int vb = 0;
            if (i < a.length) va = a[i];
            if (i < b.length) vb = b[i];

            if (va < vb) return -1;
            else if (va > vb) return 1;
        }
        return 0;
    }

    /**
     * Converts the contents of this ClientState sites array to a string.
     */
    @Override
    public String toString() {
        return Arrays.toString(this.clients);
    }

}
