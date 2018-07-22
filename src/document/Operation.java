package document;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/**
 * This is the abstract class for the operation. A single operation is anything
 * that inserts or delete text from the datatype GapBuffer. This operation data
 * type can allow for creating from various different calls, as well as
 * transforming due to concurrent operations. This is the major part where we
 * will write the transformation algorithm in the subclasses of this class.
 */
public abstract class Operation implements Serializable {

    private static final long serialVersionUID = -4459256618218580312L;

    // Client ID who created operation
    int siteId;
    // ID number of operation processed at this client
    int seqId;
    // Type of operation: insert or delete
    String type;
    // State machine of client who created operation
    private ClientState clientState = null;

    // Denotes if operation was performed locally
    private boolean local = false;
    // Document where operation is made
    String key = null;

    // String being inserted
    String value = null;
    // Offset of the operation
    int offset;

    // Global order of the operation (does not include local operations)
    private int order;
    // Indicates if operation is immutable
    boolean immutable;

    // Store referenceable operations
    private Vector<Operation> xCache = null;

    /**
     * Create a new operation specified by the type (insert or delete)
     *
     * @param type operation type (insert/delete)
     * @param properties defined in Operation function
     * @returns a new Operation
     * @throws OperationEngineException
     */
    public static Operation createOperationFromType(String type, Map<String, Object> properties) throws OperationEngineException {
        Operation op = null;
        if (type.equals("insert")) {
            op = new InsertOperation(properties);
        } else if (type.equals("delete")) {
            op = new DeleteOperation(properties);
        }

        return op;
    }

    /**
     * Create a unique history key so that its history of operations can be referenced easily
     *
     * @param site client ID
     * @param seq integer reprsenting the sequence
     * @return a new string that will serve as a key.
     */
    public static String createHistoryKey(int site, int seq) {
        return Integer.toString(site) + ", " + Integer.toString(seq);
    }

    @Override
    public String toString() {
        return ("{siteId : " + this.siteId) +
                ",seqId : " + this.seqId +
                ",type :" + type +
                ",contextVector : " + this.clientState +
                ",key : " + this.key +
                ",position : " + this.offset +
                ",value : " + this.value +
                ",order : " + this.getOrder() + "}";
    }

    /**
     * Contains information about a local or remote event for transformation.
     *
     * Initializes the operation from serialized state or individual props if
     * state is not defined in the args parameter.
     *
     * Properties contains the following information:
     *  siteID: client ID who created the operation
     *  contextVector: context in which the operation occurred
     *  key: document where operation occurred
     *  value: value (string) of operation
     *  offset: offset of hte operation
     *  order: the operation's order in the global ordering of all operations
     *  seqID: sequence number of the operation at its originating site
     *  immutable: indicates if operation cannot be changed
     *
     * @param properties
     * @throws OperationEngineException
     */
    @SuppressWarnings("unchecked")
    protected Operation(Map<String, Object> properties)
            throws OperationEngineException {
        if (properties == null) {
            this.type = null;
            return;
        }

        if (properties.containsKey("state")) {
            this.setState((Object[]) properties.get("state"));
            this.local = false;
        } else {
            this.siteId = (Integer) properties.get("siteId");
            this.clientState = (ClientState) properties.get("contextVector");
            this.key = (String) properties.get("key");
            this.value = (String) properties.get("value");
            this.offset = (Integer) properties.get("position");

            Integer ord = (Integer) properties.get("order");
            if (ord == null) {
                this.setOrder(Integer.MAX_VALUE);
            } else {
                this.setOrder(ord);
            }

            if (properties.containsKey("seqId")) {
                this.seqId = (Integer) properties.get("seqId");
            } else if (this.clientState != null) {
                this.seqId = this.clientState.getSeqForClient(this.siteId) + 1;
            } else {
                throw new OperationEngineException("Missing sequence ID for new operation.");
            }

            if (properties.containsKey("xCache")) {
                this.xCache = (Vector<Operation>) properties.get("xCache");
            } else {
                this.xCache = null;
            }

            this.local = (Boolean) properties.get("local");
        }

        this.immutable = false;

        if (this.xCache == null) {
            this.xCache = new Vector<Operation>();
        }
    }

    /**
     * Returns a new operation after transforming the current operation with the specified data type
     *
     * @param op operation
     * @return a new operation that is transformed after specified operation is applied
     */
    protected abstract Operation transformWithDelete(Operation op);

    protected abstract Operation transformWithInsert(Operation op);


    /**
     * Can use this function to copy another operation, and set all of the
     * parameters, given a correct state object array.
     *
     * @param properties
     *            Array in the format returned by getState
     * @throws OperationEngineException
     */
    private void setState(Object[] properties) throws OperationEngineException {
        if (!properties[0].equals(this.type)) {
            throw new OperationEngineException("setState invoked with state from wrong operation type.");
        } else if (this.immutable) {
            throw new OperationEngineException("Operation is immutable.");
        }

        this.key = (String) properties[1];
        this.value = (String) properties[2];
        this.offset = (Integer) properties[3];

        HashMap<String, Object> args = new HashMap<String, Object>();
        args.put("state", properties[4]);

        this.clientState = new ClientState(args);

        this.seqId = (Integer) properties[5];
        this.siteId = (Integer) properties[6];

        if (properties.length >= 8) {
            this.setOrder((Integer) properties[7]);
        } else {
            this.setOrder(Integer.MAX_VALUE);
        }
    }

    /**
     * Makes a copy of this operation object.
     *
     * @throws OperationEngineException
     * @return copy of this Operation object
     */
    public Operation copy() throws OperationEngineException {
        HashMap<String, Object> properties = new HashMap<String, Object>();
        properties.put("siteId", this.siteId);
        properties.put("seqId", this.seqId);
        properties.put("contextVector", this.clientState.copy());
        properties.put("key", this.key);
        properties.put("value", this.value);
        properties.put("position", this.offset);
        properties.put("order", this.getOrder());
        properties.put("local", this.local);
        properties.put("xCache", this.xCache);

        Operation op;
        try {
            op = Operation.createOperationFromType(this.type, properties);
        } catch (OperationEngineException e) {
            e.printStackTrace();
            op = null;
        }
        return op;
    }

    /**
     * Gets a version of the given operation previously transformed into the
     * given context if available.
     *
     * @param cv context of the transformed op to seek
     * @throws OperationEngineException
     * @return Copy of the transformed operation from the cache or null if not in cache
     */
    public Operation getFromCache(ClientState cv) throws OperationEngineException {
        Vector<Operation> cache = this.xCache;
        int l = cache.size();
        Operation xop;

        for (int i = 0; i < l; i++) {
            xop = cache.elementAt(i);
            if (xop.clientState.equals(cv)) {
                return xop.copy();
            }
        }

        return null;
    }

    /**
     * Caches a transformed copy of this original operation for faster future
     * transformations.
     *
     * @param siteCount  number of active sites, including the local one
     * @throws OperationEngineException
     */
    public void addToCache(int siteCount) throws OperationEngineException {
        Vector<Operation> cache = this.xCache;
        Operation cop = this.copy();

        cop.immutable = true;

        cache.addElement(cop);

        int diff = cache.size() - (siteCount - 1);
        if (diff > 0) {
            Operation[] operationArray = new Operation[cache.size()];
            operationArray = cache.toArray(operationArray);
            Operation[] newArr = Arrays.copyOf(operationArray, diff);

            cache.removeAllElements();
            for (int i = 0; i < newArr.length; i++) {
                cache.addElement(newArr[i]);
            }
        }
    }

    /**
     * Computes an ordered comparison of this op and another based on their
     * position in the total op order. If the order is the same, then we have to
     * take into account which one was local, and assign that priority. If they
     * are both local or both remote, then we take into account the SeqID.
     *
     * @param op
     *            Other operation
     * @return -1 if this op is ordered before the other, 0 if they are in the
     *         same context, and 1 if this op is ordered after the other
     */
    public int compareByOrder(Operation op) {
        if (this.getOrder() == op.getOrder()) {

            if (this.local == op.local) {
                if (this.seqId < op.seqId) return 1;
                else return -1;
            }
            else if (this.local) {
                return 1;
            }
            else {
                return -1;
            }
        }
        else if (this.getOrder() < op.getOrder()) {
            return 1;
        }
        else {
            return -1;
        }
    }

    /**
     * Transforms this operation to include the effects of the operation
     * provided as a parameter IT(this, op). Upgrade the context of this operation to
     * reflect the inclusion of the other.
     *
     * @return This operation, transformed in-place
     * @throws OperationEngineException If this op to be transformed is immutable
     */
    public Operation transformWith(Operation op)
            throws OperationEngineException {
        if (this.immutable) {
            throw new OperationEngineException("Attempt to transform immutable operation.");
        }

        Operation rv = null;
        if (op.type.equals("delete")) {
            rv = this.transformWithDelete(op);
        }
        else if (op.type.equals("insert")) {
            rv = this.transformWithInsert(op);
        }

        if (rv != null) {
            this.upgradeContextTo(op);
        }

        return rv;
    }

    /**
     * Upgrades the context of this operation to reflect the inclusion of a
     * another operation from the some site.
     *
     * @param op operation to include in the context of this operation
     * @throws OperationEngineException If this op to be upgraded is immutable
     */
    public void upgradeContextTo(Operation op) throws OperationEngineException {
        if (this.immutable) {
            throw new OperationEngineException(
                    "attempt to upgrade context of immutable op");
        }

        this.clientState.setSeqForClient(op.siteId, op.seqId);
    }

    /**
     * Return operation's siteID
     */
    public int getSiteId() {
        return this.siteId;
    }

    /**
     * Return operation's sequence ID
     */
    public int getSeqId() {
        return this.seqId;
    }

    /**
     * Return operation's string value
     */
    public String getValue() {
        return this.value;
    }

    /**
     * Return operation's offset
     */
    public int getOffset() {
        return this.offset;
    }

    /**
     * Return operation's context vector
     */
    public ClientState getClientState() {
        return this.clientState;
    }

    /**
     * Sets operation's immutability
     */
    public void setImmutable(boolean immutable) {
        this.immutable = immutable;
    }

    /**
     * Return operation's ordering
     */
    public int getOrder() {
        return order;
    }

    /**
     * Sets the operation's ordering
     */
    public void setOrder(int order) {
        this.order = order;
    }

    /**
     * Return document operation is made it
     */
    public String getKey() {
        return this.key;
    }

    /**
     * Sets the document key value
     */
    public void setKey(String key) {
        this.key = key;
    }

}
