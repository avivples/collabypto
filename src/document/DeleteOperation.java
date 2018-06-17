package document;

import java.util.Map;


/**
 * A subclass of the Operation class which represents a delete operation.
 */
public class DeleteOperation extends Operation {

    private static final long serialVersionUID = 3085129896663389738L;

    /**
     * Creates a delete operation object with the given properties (see Operation.java for more info)
     * @param properties
     * @throws OperationEngineException
     */
    public DeleteOperation(Map<String, Object> properties) throws OperationEngineException {
        super(properties);
        this.type = "delete";
    }
    
    
    /**
     * Transforms this delete to include the effect of another delete. Basically,
     * we will check the position of the other operation.  If the position
     * is such that it will change the position of our operation, we have
     * to modify our operation's position.
     *
     * @param op delete operation to include in this operation
     * @return This instance or null if this op has no further effect on other operations
     */
    public Operation transformWithDelete(Operation op) {
        // Editing different documents - no change
        if(!this.key.equals(op.key)) {
            return this;
        }
        // Shift left
        if(this.offset > op.offset) {
            this.offset -= op.value.length();
        }
        // no further effect - return null
        else if(this.offset == op.offset) {
            return null;
        }
        return this;
    }

    /**
     * Transforms this delete to include the effect of another insert. Basically,
     * we will check the position of the other operation.  If the position
     * is such that it will change the position of our operation, we have
     * to modify our operation's position.
     *
     * @param op insert operation to include in this operation
     * @return This instance
     */
    public Operation transformWithInsert(Operation op) {
        // Editing different documents - no change
        if(!this.key.equals(op.key)) {
            return this;
        }
        // Shift right
        if(this.offset >= op.offset) {
            this.offset += op.value.length();
        }
        return this;
    }

}
