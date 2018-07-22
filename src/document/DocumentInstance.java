package document;


import java.io.Serializable;

//Is just equivalent to a string, but the class is kept because the structure relies on it.
public class DocumentInstance implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String document;
    public DocumentInstance(String document) {
        this.document = document;
    }
}