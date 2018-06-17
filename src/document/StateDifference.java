package document;

import java.util.Vector;

/**
 * This class is a way to store the differences in state for a collection of
 * vectors. With this, it is easier to determine causality between operations,
 * and allow the recursive algorithm in the engine to determine how far back in
 * the history buffer we need to check to transform a received operation with.
 * 
 */
public class StateDifference {

    // Holds client IDs
    public Vector<Integer> clients;
    // Holds IDs for operations
    public Vector<Integer> sequenceID;

    /**
     * This is the constructor for the context difference. We have two vectors:
     * one for client IDs and one for operation (sequence) IDs
     */
    public StateDifference() {
        this.clients = new Vector<Integer>();
        this.sequenceID = new Vector<Integer>();
    }

    /**
     * This command will be called when we want to add a range of differences
     * for a client. This will be useful in the _transform_ function called in
     * the OperationEngine
     * 
     * @param client id
     * @param start first operation sequence number (inclusive)
     * @param end  last operation sequence number (exclusive)
     */
    public void addRange(int client, int start, int end) {
        for (int i = start; i < end; i++) {
            // This means that the client has a different operation at the specified
            // sequence ID
            this.clients.addElement(client);
            this.sequenceID.addElement(i);
        }
    }

    /**
     * We will get all the operations in the HistoryBuffer that are specified in
     * this contextDifference.
     * Since we know the specific way that keys of the operation are constructed,
     * we can recreate the key for a specific operation, and then get it from the history buffer.
     * This will allow us to access the information about this different operation,
     * such as its value and offset.
     * This will be used in the __transform__ function in OperationEngine
     * 
     * @return Array of keys for HistoryBuffer lookups
     */
    public String[] getHistoryBufferKeys() {
        Vector<String> arr = new Vector<String>();
        for (int i = 0; i < this.clients.size(); i++) {
            String key = Operation.createHistoryKey(this.clients.elementAt(i), this.sequenceID.elementAt(i));
            arr.addElement(key);
        }
        String[] strArr = new String[arr.size()];
        return arr.toArray(strArr);
    }

}
