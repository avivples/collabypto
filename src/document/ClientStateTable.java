package document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/**
 * This class will serve as a storage mechanism for all of the ClientStates.
 * This will make it easier to identify causality, and pull the historical
 * operations from the history buffer.
 * This is used in the OperationEngine in the OT algorithm to see what operations have
 * been done since the remote operation was received.
 * 
 */
public class ClientStateTable {

    // Stores all client states
    private ArrayList<ClientState> cvt;

    /**
     * Create a ClientStateTable to store a table of states
     * associated with each client in the network
     * 
     * @param cv initial client state
     * @param client first client to be added to the table
     * @throws OperationEngineException
     */
    public ClientStateTable(ClientState cv, int client) throws OperationEngineException {
        this.cvt = new ArrayList<ClientState>();
        this.growTo(client + 1);
        this.cvt.set(client, cv);
    }

    /**
     * Returns a String representation of all client states in the table.
     */
    @Override
    public String toString() {
        String[] values = new String[this.cvt.size()];
        int l = this.cvt.size();
        for (int i = 0; i < l; i++) {
            ClientState cv = this.cvt.get(i);
            values[i] = cv.toString();
        }
        return Arrays.toString(values);
    }

    /**
     * Increases the size of all  ClientStates in the table to the given size,
     * since they must have a position for all the clients, and increases the
     * current ClientStateTable size All initial sequences are set to 0.
     * 
     * @param finalSize
     *            Table size
     * @throws OperationEngineException
     */
    public void growTo(int finalSize) throws OperationEngineException {
        int l = cvt.size();

        for (ClientState state : cvt) {
            state.growTo(finalSize);
        }

        for (int j = l; j < finalSize; j++) {
            HashMap<String, Object> properties = new HashMap<String, Object>();
            properties.put("count", finalSize);
            ClientState cv = new ClientState(properties);
            this.cvt.add(cv);
        }
    }

    /**
     * Sets the  ClientState for the given client. Grows the table if it does
     * not include the client yet.
     * 
     * @param client client ID
     * @param cv client state instance
     * @throws OperationEngineException
     */
    public void setClientState(int client, ClientState cv)
            throws OperationEngineException {
        if (client >= 0) {
            if (this.cvt.size() <= client) {
                this.growTo(client + 1);
            }
            if (cv.getSize() <= client) {
                cv.growTo(client + 1);
            }
            this.cvt.set(client, cv);
        }
    }

    /**
     * Updates the table with a new operation
     * 
     * @param op operation with the client ID and state
     * @throws OperationEngineException
     */
    public void operationUpdate(Operation op) throws OperationEngineException {
        ClientState cv = op.getClientState().copy();
        cv.setSeqForClient(op.siteId, op.seqId);
        this.setClientState(op.siteId, cv);
    }
}
