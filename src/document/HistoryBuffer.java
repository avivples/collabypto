package document;

import java.util.HashMap;
import java.util.Vector;
import java.util.Stack;
import java.util.Arrays;
import java.util.Comparator;

/**
 * This history buffer keeps track of what operations have been performed. This
 * is useful for the OT algorithm.
 * All operations in the history are immutable, since they are only to be used as a record.
 * Since each client has his/her own history buffer, and there is no shared memory,
 * this code should be thread safe.
 */
public class HistoryBuffer {


    // Stores all processed operations of the client
    private HashMap<String, Operation> ops;
    // Number of processed operations
    private int size = 0;

    public HistoryBuffer() {
        this.ops = new HashMap<String, Operation>();
        this.size = 0;
    }

    /**
     * Adds a local operation to the history buffer
     * @param op local operation performed
     */
    public void addLocalOperation(Operation op) {
        String key = Operation.createHistoryKey(op.siteId, op.seqId);
        this.ops.put(key, op);
        op.immutable = true;
        ++this.size;
    }

    /**
     * Adds a remote operation to the history. If the operation already exists
     * in the history, simply updates its order attribute. If not, adds it.
     *
     * If the operation does not have a order or if there is an operation with the
     * same key in the total order, an error is thrown.
     *
     * @param op remote operation performed
     * @throws OperationEngineException
     */
    public void addRemoteOperation(Operation op) throws OperationEngineException {
        String key = Operation.createHistoryKey(op.siteId, op.seqId);
        Operation eop = this.ops.get(key);

        if (op.getOrder() == Integer.MAX_VALUE) {
            throw new OperationEngineException("Remote op missing total order.");
        }
        else if (eop != null) {
            if (eop.getOrder() != Integer.MAX_VALUE) {
                throw new OperationEngineException("Duplicate op in total order: " +
                        "old=" + eop.getOrder() + " new=" + op.getOrder());
            }
            eop.setOrder(op.getOrder());
        }
        else {
            this.ops.put(key, op);
            op.immutable = true;
            ++this.size;
        }
    }

    /**
     * Retrieves all of the operations represented by the given context
     * differences from the history buffer. Sorts them by total order, placing
     * any ops with an unknown place in the order (i.e., local ops) at the end
     * sorted by their sequence IDs. Throws an exception when a requested
     * operation is missing from the history.
     *
     * @param cd Context difference object
     * @throws OperationEngineException
     * @return Sorted operations
     */
    public Stack<Operation> getOpsForDifference(StateDifference cd) throws OperationEngineException {

        String[] keys = cd.getHistoryBufferKeys();
        Vector<Operation> opsStack = new Vector<Operation>();
        int l = keys.length;
        String key;
        Operation op;

        for (int i = 0; i < l; i++) {
            key = keys[i];
            op = this.ops.get(key);
            if (op == null) {
                throw new OperationEngineException(
                        "HistoryBuffer error -- We are missing ops for context: " +
                                "i = " + i + ", key = " + key +
                                ", keys = " + keys.toString());
            }
            opsStack.addElement(op);
        }
        Operation[] arr = new Operation[opsStack.size()];
        arr = opsStack.toArray(arr);
        Arrays.sort(arr, (Comparator) (a, b) -> ((Operation) a).compareByOrder((Operation) b));

        Stack<Operation> stack = new Stack<>();
        stack.addAll(Arrays.asList(arr));
        return stack;
    }

    /**
     * Return the number of operations in the history.
     */
    public int getSize() {
        return this.size;
    }

    @Override
    public String toString() {
        return "Ops: " + this.ops + ", Size: " + this.size;
    }
}
