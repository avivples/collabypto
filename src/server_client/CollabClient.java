package server_client;

import document.*;
import server_client.CollabModel;
import gui.ClientGui;
import gui.DocumentSelectionPage;
import gui.ErrorDialog;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

/**
 * This is the client that will connect to a server. A unique instance of the
 * client will be created each time a user opens up a client. It will receive a
 * copy of the central document from the server. Changes to the document on this
 * particular client will be sent to the server. Changes to the document by
 * other clients will be relayed by the server, and this client will apply an OT
 * algorithm to correctly update its own document. All of this will be reflected
 * on the GUI.
 *
 *
 * Passing these tests ensure that our client is able to display the latest
 * version of the document while a user is adding new text.
 *
 * @author youyanggu
 *
 */
public class CollabClient implements CollabInterface {
    /** Timeout on socket connection attempts */
    private static final int TIMEOUT = 2000;
    /** unique to each client. Used to differentiate operations */
	private int siteID;
	/** document the client is editing */
	private String document = "";

	/** port number of client */
	private int port;
	/** ip number of client */
	private String ip;
	/** username of client */
	private String name;

	/** label to display at the top of the document GUI */
	protected String label = "Client";
	/** Intializes socket and streams to null */
	private Socket s = null;

	/** outputstream to send objects to server */
	protected ObjectOutputStream out = null;
	/** inputstream to receive objects from server */
	protected ObjectInputStream in = null;
	/** client GUI used to display the document */
	protected ClientGui gui;

	/**
	 * Constructor to start the client. Simply sets the identifiers.
	 * The connection to the server come later in the connect() call.
	 *
	 * @param IP - the ip address of the host to connect to
	 * @param port - the port number of the host to connect to
	 * @param name - the name alias given to the client
	 */
	public CollabClient(String IP, int port, String name) {
		this.ip = IP;
		this.port = port;
		this.name = name;
	}

	/**
	 * start up a client.
	 * by calling the connect() method.
	 * @throws OperationEngineException if the operation finds an incosistency
	 */
	public void start() {
        try {
            this.connect();
        } catch (IOException e) {
            new ErrorDialog(e.toString());
        }

	}


	/**
	 * Starts the client and try to connec to the server with the paramters
	 * given in the constructor. It success, it will continue a connection with
	 * the server until an exception is thrown. A GUI is created that will
	 * hopefully reflect the current state of the document. Various message
	 * passing will be used to send insert/delete updates. The document will
	 * hopefully be updated with edits made by both the client itself and other
	 * clients, relayed by the server.
	 *
	 * @throws IOException if the socket is broken or corrupted
	 * @throws OperationEngineException if the operation caused an exception when being processed by
     * the operation engine
	 */
	public void connect() throws IOException {

	    // Establishes a socket connection
		System.out.println("Connecting to port: " + this.port + " at: " + this.ip);
		InetSocketAddress address = new InetSocketAddress(this.ip, this.port);
		try {
		    s = new Socket();
		    s.connect(address, TIMEOUT);
		} catch (UnknownHostException e) {
            System.err.println("Don't know about host: " + this.name);
            return;
        } catch (IOException e) {
		    System.err.println("Timeout: cannot connect to IP: " + this.ip + " , port: " + this.port + ". Please try again.");
		    return;
		} catch (Exception e) {
		    System.err.println("Your port number (" + this.port + ") or IP (" + this.ip + ") is incorrect. Please try again");
		    return;
		}

		// Connection established. Communicates with server
		try {
			out = new ObjectOutputStream(s.getOutputStream());
			in = new ObjectInputStream(s.getInputStream());

			Object o= in.readObject();

			if(!(o instanceof ArrayList<?>)) {
                throw new RuntimeException("Expected ArrayList of documents");
            }

			// Popup for user to select document. We already checked above for object type
            @SuppressWarnings("unchecked")
            JFrame f = new DocumentSelectionPage((ArrayList<String>) o,this);

            // Waits until user selects document to edit. Calls setDocument() on return
            // to set the document to edit
		    while (f.isVisible()) {
		        try {
		            Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
		    }

		    // Sends to server the document this client wants to edit
			out.writeObject(document);
			out.flush();

			// Reads in operations from the server
			o = in.readObject();
			while (o != null) {
			    parseInput(o);
				o = in.readObject();

			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (OperationEngineException e) {
			e.printStackTrace();
		} finally {
		    // Close connection
		    s.close();
			out.close();
			in.close();
			System.exit(0);

		}
	}

	/**
	 * Takes an object that was received from the server and parses it according
	 * to its type. Performs the appropriate calls and updates based on the object received
	 * @param o - object sent from the server to parse
	 * @throws IOException - caused if the operation object is corrupt, or if the socket connection breaks
	 * @throws OperationEngineException - if the operation caused an exception when being processed by
	 * the operation engine
	 */
	@SuppressWarnings("unchecked")
    public void parseInput(Object o) throws IOException, OperationEngineException {
	    if (o instanceof Pair) {
	    	Pair p = (Pair) o;
	    	Object ciphertext = p.first;
			// TODO: Decrypt "first"
			// We'll change the temp in the if to the decrypted variable
			Object plaintext = decrypt(ciphertext);
			if (plaintext instanceof Operation) {
				Operation op = (Operation) plaintext;
				if (getID() == op.getSiteId()) return;
				// We received an operation from the server. Update the local document
				op.setOrder((Integer) p.second);
				updateDoc(op);
			}
			if (plaintext instanceof ArrayList) {
				// Updates list of current users and documents
				ArrayList<String> users = (ArrayList<String>) plaintext;
				ArrayList<String> documents = (ArrayList<String>) p.second;
				this.gui.updateUsers(users.toArray());
				this.gui.updateDocumentsList(documents.toArray());
			}
		// if a new user, get the doc in server and the history of operations

        // TODO: Decrypt o
		} else if (o instanceof DocumentInstance) {
			// The server just sent the initial string of the document
			// Start up the GUI with this string
			String text = ((DocumentInstance) o).document;
			try {
				this.gui = new ClientGui(text, this, label);
			} catch (OperationEngineException e) {
				e.printStackTrace();
			}
			this.gui.setModelKey(document);

			JFrame frame = new JFrame("Collab Edit Demo");
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			// Add content to the JFrame window.
			frame.add(this.gui);
			// Display the window.
			frame.pack();
			frame.setVisible(true);

			// Updates the ContextVector of the GUI with the one sent by the server
			ClientState cV = ((DocumentInstance) o).contextVector;
			gui.getCollabModel().setCV(cV);
		} else if (o instanceof ArrayList) {
			ArrayList history = (ArrayList) o;
			for (int i = 0; i < history.size(); i++) {
				// TODO: Decrypt op
				Operation op = (Operation) decrypt(history.get(i));
				op.setOrder(i);
				updateDoc(op);
			}
		}
        else if (o instanceof Integer) {
            // The server is sending the unique client identifiers
            this.siteID = ((Integer) o).intValue();
            if (this.name.equals("Anonymous"))
                this.name += "" + this.siteID;
            out.writeObject(this.name);
            out.flush();
            label = this.name + " is editing document: " + this.document;

        } else if (o instanceof String) {
		 	if (o.equals("give")) {
				// TODO: encrypt and change this so we send the object we always speak of
				transmit(encrypt(new DocumentInstance(gui.getText(), gui.getCollabModel().copyOfCV())));
			}
            // The server just sent the initial string of the document
            // Start up the GUI with this string
			else {
				try {
					this.gui = new ClientGui((String) o, this, label);
				} catch (OperationEngineException e) {
					e.printStackTrace();
				}
				this.gui.setModelKey(document);

				JFrame frame = new JFrame("Collab Edit Demo");
				frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				// Add content to the JFrame window.
				frame.add(this.gui);
				// Display the window.
				frame.pack();
				frame.setVisible(true);
			}
        } else if (o instanceof ClientState) {
			// Updates the ContextVector of the GUI with the one sent by the server
			CollabModel collab = this.gui.getCollabModel();
			collab.setCV((ClientState) o);
//        } else if (o instanceof Pair<?, ?>) {
//            // Updates list of current users and documents
//            ArrayList<String> users = ((Pair<ArrayList<String>, ArrayList<String>>) o).first;
//            ArrayList<String> documents = ((Pair<ArrayList<String>, ArrayList<String>>) o).second;
//            this.gui.updateUsers(users.toArray());
//            this.gui.updateDocumentsList(documents.toArray());
		} else {
            throw new RuntimeException("Unrecognized object type received by client");
        }
	}

	private Object decrypt(Object cipherText) {
		// TODO
		return cipherText;
	}

	private Object encrypt(Object plaintext) {
		// TODO
		return plaintext;
	}

	/**
	 * Updates the client's copy of the document using operational transform
	 * through a call to the CollabModel's remoteInsert/remoteDelete
	 * @param o - the operation that was received from the server to
	 * apply to the model
	 */
	@Override
	public void updateDoc(Operation o) {
		try {
			if (o instanceof InsertOperation) {
				this.gui.getCollabModel().remoteOp((Operation) o, true);
			} else if (o instanceof DeleteOperation) {
				this.gui.getCollabModel().remoteOp((Operation) o, false);
			} else if (o instanceof UpdateOperation) {
				throw new UnsupportedOperationException();
			} else {
				throw new RuntimeException("Shouldn't reach here");
			}
		} catch (OperationEngineException e) {
			new ErrorDialog(e.toString());
		} catch (BadLocationException e) {
			new ErrorDialog(e.toString());
		}
	}

	/**
	 * @return the siteID of the document
	 */
	@Override
	public int getID() {
		return siteID;
	}

	/**
	 * Transmits local changes to the server via an operatoin
	 * @param o the operation to transmit to server
	 * @throws IOException if the OutputStream is corrupted or broken
	 */
	@Override
	public void transmit(Object o) throws IOException {
		if (out == null)
			throw new RuntimeException("Socket not initialized.");
		out.writeObject(o);
		out.flush();
	}

	/**
	 * Used by the document selector popup to set the
	 * @param text new name of the document
	 */
	public void setDocument(String text) {
		document = text;
	}

	/** @return ip address of client */
	public String getIP() {
	    return this.ip;
	}

	/** @return port number of client */
	public int getPort() {
	    return this.port;
	}

	/** @return username of client */
	public String getUsername() {
	    return this.name;
	}

}
