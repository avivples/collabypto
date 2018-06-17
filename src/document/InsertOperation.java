package document;

import java.util.Map;

/**
 * A subclass of the Operation class which represents an insert operation.
 */
public class InsertOperation extends Operation {

    private static final long serialVersionUID = -7860774059727089325L;

    /**
     * Creates an insert operation object with the given properties (see Operation.java for more info)
     * @param properties
     * @throws OperationEngineException
     */
    public InsertOperation(Map<String, Object> properties) throws OperationEngineException {
        super(properties);
        this.type = "insert";
    }
    
    /**
     * Transforms this insert to include the effect of another insert. Basically,
     * we will check the position of the other operation.  If the position
     * is such that it will change the position of our operation, we have
     * to modify our operation's position.
     *
     * @param op insert operation to include in this operation
     * @return this operation
     */
    public Operation transformWithInsert(Operation op) {
        // Editing different documents - no change
        if(!this.key.equals(op.key)) {
            return this;
        }

        // Shift right
        if(this.offset > op.offset || (this.offset == op.offset && this.siteId <= op.siteId)) {
            this.offset += op.value.length();
        }
        return this;
    }
    
    /**
     * Transforms this insert to include the effect of another delete. Basically,
     * we will check the position of the other operation.  If the position
     * is such that it will change the position of our operation, we have
     * to modify our operation's position.
     *
     * @param op delete operation to include in this op
     * @return this operation
     */
    public Operation transformWithDelete(Operation op) {
        // Editing different documents - no change
        if (!this.key.equals(op.key)) {
            return this;
        }
        // Shift right
        if (this.offset > op.offset) {
            this.offset -= op.value.length();
        }
        return this;
    }
}
