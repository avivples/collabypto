package document;


import java.io.Serializable;

//represents the document and its context vector
public class DocumentInstance implements Serializable {
    private static final long serialVersionUID = 1L;

    public String document;
    public ClientState contextVector;
        public DocumentInstance(String document, ClientState contextVector) {
            this.document = document;
            this.contextVector = contextVector;
        }
}
