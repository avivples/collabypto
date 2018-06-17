package document;

/**
 * This exception is thrown whenever an error is encountered in the Operation engine
 */
public class OperationEngineException extends Exception {

    private static final long serialVersionUID = 1L;

    public OperationEngineException(String string) {
        super(string);
    }
}
