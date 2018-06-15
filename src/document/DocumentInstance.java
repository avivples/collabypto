package document;


import java.io.Serializable;

/**
 * Represents the document as a string
 */
public class DocumentInstance implements Serializable {

    private static final long serialVersionUID = 1L;

    public String document;

    public DocumentInstance(String document) {
        this.document = document;
    }
}