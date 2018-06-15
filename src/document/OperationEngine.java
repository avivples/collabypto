package document;

import java.util.Map;
import java.util.HashMap;
import java.util.Stack;

/**
 * This will control the operation transform at a single site in the client
 * server architecture.
 * This will then take the operations and transform them by calling
 * the OT functions.
 * Since this engine is only run at one client there should be no concurrency issues.
 */
public class OperationEngine {


    // Represents the client ID of this Operation Engine
    private int siteId;
    // Local state machine (context vector)
    private ClientState cs;
    // use to know what state every other client is in
    private ClientStateTable cst;


    // History buffer to keep track of previously processed operations
    private HistoryBuffer historybuffer;
    // Number of clients currently connected in the network
    private int siteCount = 1;

    /**
     * Creates an OperationEngine object with controls the entire OT algorithm.
     * @param siteId  client ID of this engine instance
     */
    public OperationEngine(int siteId) throws OperationEngineException {
        this.siteId = siteId;
        HashMap<String, Object> args = new HashMap<String, Object>();
        args.put("count", siteId + 1);
        this.cs = new ClientState(args);
        this.cst = new ClientStateTable(this.cs, siteId);
        this.historybuffer = new HistoryBuffer();
    }

    @Override
    public String toString() {
        String s = "{siteId : " + siteId +
                   ",ClientState : " + this.cs +
                   ",ClientStaterTable : " + this.cst +
                   ",HistoryBuffer : " + this.historybuffer +
                   ",ClientCount : " + this.siteCount + "}";
        return s;
    }

    /**
     * Makes a copy of the client state representing the local document's state.
     *
     * @return Copy of the ClientState for the local site
     * @throws OperationEngineException
     */
    public ClientState copyClientState() throws OperationEngineException {
        return this.cs.copy();
    }

    /**
     * Factory method that creates an operation object initialized with the
     * given values.
     * 
     * @param local  True iff the operation was originated locally
     * @param key    the operation's key (i.e., document operation is made in)
     * @param value  the operation's value
     * @param type   the operation's type (insert/delete)
     * @param offset the operation's offset (position)
     * @param site   siteID where remote operation was made (ignored for local ops which take local site's ID)
     * @param cv     the operation's context/state (ignored for local ops, which take local site's state)
     * @param order  global ordering of operation (ignored for local ops which are not yet assigned an order)
     * @return operation instance matching the given type
     * @throws OperationEngineException
     */
    public Operation createOp(boolean local, String key, String value, String type, int offset,
                              int site, int[] cv, int order) throws OperationEngineException {
        Map<String, Object> properties = new HashMap<String, Object>();
        if (local) {
            properties.put("key", key);
            properties.put("position", offset);
            properties.put("value", value);
            properties.put("siteId", this.siteId);
            properties.put("contextVector", this.copyClientState());
            properties.put("local", true);
        }
        else {
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("sites", cv);
            properties.put("key", key);
            properties.put("position", offset);
            properties.put("value", value);
            properties.put("siteId", site);
            ClientState clientState = new ClientState(map);
            properties.put("contextVector", clientState);
            properties.put("order", order);
            properties.put("local", false);
        }
        return Operation.createOperationFromType(type, properties);
    }

    /**
     * Creates an operation object and pushes it into the operation engine
     * algorithm.
     * 
     * @return transformed operation
     * @throws OperationEngineException
     */
    public Operation push(boolean local, String key, String value, String type,
                          int position, int site, int[] cv, int order) throws OperationEngineException {

        Operation op = createOp(local, key, value, type, position, site, cv, order);
        if (local) return this.pushLocalOp(op);
        else return this.pushRemoteOp(op);
    }

    /**
     * Process a _local_ operation and adds it to the history buffer.
     * 
     * @param op local operation
     * @return op
     */
    public Operation pushLocalOp(Operation op) {
        this.cs.setSeqForClient(op.getSiteId(), op.getSeqId());
        this.historybuffer.addLocalOperation(op);
        return op;
    }

    /**
     * Process a _remote_ operation, transforming it if required, and adds the
     * original to the history buffer.
     * 
     * @param op
     *            Remote operation
     * @throws OperationEngineException
     * @return New, transformed operation object or null if the effect of the
     *         passed operation is nothing
     */
    public Operation pushRemoteOp(Operation op) throws OperationEngineException {
        Operation o;
        if (this.hasProcessedOp(op)) {
            this.historybuffer.addRemoteOperation(op);
            System.out.println("Already processed");
            return null;
        } else if (this.cs.equals(op.getClientState())) {
            o = op.copy();
        } else {
            StateDifference cd = this.cs.subtract(op.getClientState());
            op.setImmutable(true);
            o = this.fullTransform(op, cd);
        }

        this.cs.setSeqForClient(op.getSiteId(), op.getSeqId());
        this.historybuffer.addRemoteOperation(op);
        this.cst.operationUpdate(op);
        return o;
    }

    /**
     * Checks if engine already processed the give operation based on its
     * ClientState and the ClientState of this engine instance.
     * 
     * @param op operation to check
     * @return True iff the engine already processed this operation
     */
    public boolean hasProcessedOp(Operation op) {
        int seqId = this.cs.getSeqForClient(op.getSiteId());
        return (seqId >= op.getSeqId());
    }

    /**
     * Executes a recursive step in the OT algorithm.
     * This method assumes it will _not_ be called if no transformation
     * is needed in order to reduce the number of operation copies needed.
     * 
     * @param op operation to transform
     * @param cd ClientState difference between the given op and the document state
     * @return A new operation, including the effects of all of the operations
     *         in the context difference or null if the operation can have no
     *         further effect on the document state
     * @throws OperationEngineException
     */
    private Operation fullTransform(Operation op, StateDifference cd) throws OperationEngineException {
        // we first the get the operations that are different, namely the
        // ones we have done locally, but were not seen yet at the remote client.
        Stack<Operation> ops = this.historybuffer.getOpsForDifference(cd);
        Operation prevOperation;
        StateDifference previousStateDifference;
        Operation prevCachedOperation;
        Operation cachedOperation;

        op = op.copy();

        // transform all the ops in the difference
        for (int i = 0; i < ops.size(); i++) {
            prevOperation = ops.elementAt(i);
            if (!op.getClientState().equals(prevOperation.getClientState())) {
                // see if we've cached a transform of this op in the desired context to avoid duplicate work
                prevCachedOperation = prevOperation.getFromCache(op.getClientState());
                if (prevCachedOperation != null) {
                    prevOperation = prevCachedOperation;
                } else {
                    // transformation is needed to update the state of
                    // previousOperation to current Operation
                    previousStateDifference = op.getClientState().subtract(prevOperation.getClientState());
                    if (previousStateDifference.clients == null ||
                            previousStateDifference.clients.size() == 0) {
                        throw new OperationEngineException("Transformation produced empty StateDifference.");
                    }
                    prevCachedOperation = this.fullTransform(prevOperation, previousStateDifference);
                    if (prevCachedOperation == null) {
                        op.upgradeContextTo(prevOperation);
                        continue;
                    }
                    // now we only need the cachedOperation
                    prevOperation = prevCachedOperation;
                }
            }
            if (!op.getClientState().equals(prevOperation.getClientState())) {
                throw new OperationEngineException("ClientStates not convergent after updating.");
            }
            // make a copy of the op as is before transformation
            cachedOperation = op.copy();
            // transform op to include previousOperation now that ClientStates match
            op = op.transformWith(prevOperation);
            if (op == null) {
                // op was deleted by another earlier op so return now because no further
                // transformations have any impact on this op
                return null;
            }
            // cache the transformed op
            op.addToCache(this.siteCount);

            prevOperation = prevOperation.copy();
            prevOperation = prevOperation.transformWith(cachedOperation);
            if (prevOperation != null) {
                prevOperation.addToCache(this.siteCount);
            }
        }
        return op;
    }

    /**
     * Used for creating a new client, and then syncing with the central
     * server
     * @param cv context vector
     */
    public void setCV(ClientState cv) {
        this.cs = cv;
        cv.growTo(siteId);
    }
}
