package document;

public class DocumentState {
    public String documentText;
    public ClientState contextVector;

    public DocumentState(String documentText, ClientState contextVector) {
        this.documentText = documentText;
        this.contextVector = contextVector;
    }
}
