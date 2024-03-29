package server_client;

import document.Operation;

import java.io.IOException;

import server_client.CollabClient.ENCRYPTION_METHOD;

/**
 * The interface for the CollabClient and CollabServer.
 * @author youyanggu
 *
 */
public interface CollabInterface { 
    
    /**
     * Converts the private document into a string.  The GUI will call this
     * for the client
     * @return the string version of the model
     */
    String toString();
    
    /**
     * This will return the unique integer id of this client or server.  Integer ID
     * must be incrementing integers starting at 0 for the server, 1, 2, 3, ..etc. for the clients
     * @return integer siteID
     */
    int getID();
    
    /**
     * For the client, this will take the operation that was performed locally, and transmit it
     * to the server.
     * 
     * For the server, this will take the operation that was sent from a client, and update its 
     * own model.
     * @param op
     * @throws IOException 
     */
    void transmit(Object op) throws IOException;

    void transmit(Object o, ENCRYPTION_METHOD encryption) throws IOException;

    /**
     * Updates the copy of the document using operational transform
     * through a call to the CollabModel's remoteInsert/remoteDelete
     * @param o - the operation that was received to apply to the model
     */
    void updateDoc(Operation o);


}
