package server_client;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import document.*;
import gui.ClientGui;
import gui.DocumentSelectionPage;
import gui.ErrorDialog;
import org.apache.commons.lang3.StringUtils;
import org.whispersystems.libsignal.*;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.protocol.PreKeySignalMessage;
import org.whispersystems.libsignal.protocol.SignalMessage;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.state.impl.InMemorySignalProtocolStore;
import org.whispersystems.libsignal.util.KeyHelper;
import org.whispersystems.libsignal.util.Medium;
import signal.ClientSessionCipher;
import signal.EncryptedMessage;
import signal.RegistrationInfo;
import signal.SessionInfo;

import javax.swing.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/**
 * This is the client that will connect to a server. A unique instance of the
 * client will be created each time a user opens up a client. Changes to the document on this
 * particular client will be sent to the server. Changes to the document by
 * other clients will be relayed by the server, and this client will apply an OT
 * algorithm to correctly update its own document. All of this will be reflected
 * on the GUI.
 *
 * Passing these tests ensure that our client is able to display the latest
 * version of the document while a user is adding new text.
 */

@SuppressWarnings("ResultOfMethodCallIgnored")
public class CollabClient implements CollabInterface {
    /** Timeout on socket connection attempts */
    private static final int TIMEOUT = 2000;
    /** unique to each client. Used to differentiate operations */
	private int siteID = -1;
	/** document the client is editing */
	private String document = "";
	/** list of clients current doc is shared with */

	//storing the session ciphers documentname -> array of ciphers
	private HashMap<String, ArrayList<ClientSessionCipher>> sessionCiphers = new HashMap<>();

	/** port number of client */
	private final int port;
	/** ip number of client */
	private final String ip;

    //the name of the directory the client will read/write information from/to.
	private final String dir;

	/** username of client */
	private String name;

	//the text of the document and its context vector
	private DocumentState documentState = new DocumentState("", null);

	/** label to display at the top of the document GUI */
    private String label = "Client";

    /** Error message **/
	private String errorMessage = "Server Disconnected.";

	/** outputstream to send objects to server */
    private ObjectOutputStream out = null;
	/** inputstream to receive objects from server */
    private ObjectInputStream in = null;
	/** client GUI used to display the document */
    private ClientGui gui;

    //The signal protocol information is stored here
	private InMemorySignalProtocolStore clientStore;

	//the type of encryption used (AES was included before but was deprecated)
	public enum ENCRYPTION_METHOD {
		NONE, SIGNAL
	}

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
        String serverName = ip + "-" + port;
		this.dir =  "users/" + name + "/" + serverName;
	}

	/**
	 * start up a client.
	 * by calling the connect() method.
	 * @throws OperationEngineException if the operation finds an inconsistency
	 */
	public void start() {
        try {
            this.connect();
        } catch (IOException e) {
            new ErrorDialog(e.toString());
        }
	}


    //Creates a list of bundles to pass to the server, so the server can create sessions with other users.
	private RegistrationInfo register () throws InvalidKeyException {
		//initialize information as per the signal library instructions
		IdentityKeyPair	identityKeyPair = KeyHelper.generateIdentityKeyPair();
		int registrationId  = KeyHelper.generateRegistrationId(true);
		int startId = new Random().nextInt(Medium.MAX_VALUE);
		List<PreKeyRecord> preKeys = KeyHelper.generatePreKeys(startId, 100);
		SignedPreKeyRecord signedPreKey = KeyHelper.generateSignedPreKey(identityKeyPair, new Random().nextInt(Medium.MAX_VALUE));

		//Create a list of PreKeyBundles
		List<PreKeyBundle> preKeyBundles = createPreKeyBundles(identityKeyPair, registrationId, preKeys, signedPreKey);

		//Store in the data structure
		clientStore = new InMemorySignalProtocolStore(identityKeyPair, registrationId);
		for (PreKeyRecord pK : preKeys) {
			clientStore.storePreKey(pK.getId(), pK);
			pK.serialize();
		}
		clientStore.storeSignedPreKey(signedPreKey.getId(), signedPreKey);
		return new RegistrationInfo(preKeyBundles);
	}

	//write information on exit to load when client rejoins
	private void writeToFile() throws IOException, OperationEngineException {
		Class<?>[] classes = new Class[] {HashMap.class, ClientSessionCipher.class};
		XStream xs = new XStream(new DomDriver());
		XStream.setupDefaultSecurity(xs);
		xs.allowTypesByWildcard(new String[] {"org.whispersystems.libsignal.**"});
		xs.allowTypes(classes);

		//write registration information
		String fileName = dir + "/clientStore.txt";
		File clientStoreFile = new File(fileName);
		clientStoreFile.getParentFile().mkdirs();
		clientStoreFile.createNewFile();
		String xml = xs.toXML(clientStore);
		writeXMLToFile(clientStoreFile, xml);

		//if the user is in a document, then also write document information
		if(!document.equals("")) {
			//write document text and context vector
			DocumentState documentState = new DocumentState(gui.getCollabModel().getDocumentText(), gui.getCollabModel().copyOfCV());
			fileName = dir + "/doc-" + document + ".txt";
			File documentStateFile = new File(fileName);
            documentStateFile.createNewFile();
            xml = xs.toXML(documentState);
			writeXMLToFile(documentStateFile, xml);

			//write session ciphers
			fileName = dir + "/sessions-" + document + ".txt";
			File sessionCiphersFile = new File(fileName);
			sessionCiphersFile.createNewFile();
			xml = xs.toXML(sessionCiphers);
			writeXMLToFile(sessionCiphersFile, xml);
		}

	}

	//helper function for writing
	private void writeXMLToFile(File file, String xml) throws IOException {
		FileWriter writer = new FileWriter(file, false);
		writer.write(xml);
		writer.close();
	}

	//reads and sets clientstore and sessionciphers. Returns true if the client has this information, false otherwise.
	private boolean readFromFile() throws IOException {
		XStream xs = new XStream(new DomDriver());
		XStream.setupDefaultSecurity(xs);
		xs.allowTypesByWildcard(new String[] {"org.whispersystems.libsignal.**"});

		//if client has no directory for this server, we don't read.
		if(!new File(dir).exists()) {
			return false;
		}

		//read registration information.
		String fileName = dir + "/clientStore.txt";
		File clientStoreFile = new File(fileName);
		String xml = readAllFile(clientStoreFile);
		clientStore = (InMemorySignalProtocolStore) xs.fromXML(xml);

		return true;
	}

	//reads and sets document information upon client requesting a document.
	public boolean readDocument() throws IOException {
		Class<?>[] classes = new Class[] {DocumentState.class, HashMap.class, ClientSessionCipher.class};
		XStream xs = new XStream(new DomDriver());
		XStream.setupDefaultSecurity(xs);
		xs.allowTypesByWildcard(new String[] {"org.whispersystems.libsignal.**"});
		xs.allowTypes(classes);
		File documentFile = new File(dir + "/doc-" + document + ".txt");
		if(!documentFile.exists()) return false;
		String xml = readAllFile(documentFile);
		this.documentState = (DocumentState) xs.fromXML(xml);

		//java translates newline to \r\n - change to just \n
		documentState.documentText = StringUtils.remove(documentState.documentText, (char) 13);

		File sessionCiphersFile = new File(dir + "/sessions-" + document + ".txt");
		xml = readAllFile(sessionCiphersFile);
		sessionCiphers = (HashMap<String, ArrayList<ClientSessionCipher>>) xs.fromXML(xml);

		return true;
	}

	//helper function for reading
	private String readAllFile(File file) throws IOException {
		FileInputStream fis = new FileInputStream(file);
		byte[] data = new byte[(int) file.length()];
		fis.read(data);
		fis.close();
		return new String(data);
	}

	//creates a prekeybundle holding 1 prekey for each of the prekeys given.
	private List<PreKeyBundle> createPreKeyBundles(IdentityKeyPair	identityKeyPair,
												   int registrationId, List<PreKeyRecord> preKeys,
												   SignedPreKeyRecord signedPreKey) {

		List<PreKeyBundle> preKeyBundles = new ArrayList<>();
		for (PreKeyRecord pK : preKeys) {
			PreKeyBundle preKeyBundle = new PreKeyBundle(registrationId, 1, pK.getId(),
					pK.getKeyPair().getPublicKey(), signedPreKey.getId(), signedPreKey.getKeyPair().getPublicKey(),
					signedPreKey.getSignature(), identityKeyPair.getPublicKey());
			preKeyBundles.add(preKeyBundle);
		}
		return preKeyBundles;
	}


	/**
	 * Starts the client and try to connect to the server with the parameters
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
    private void connect() throws IOException {
	    // Establishes a socket connection
		System.out.println("Connecting to port: " + this.port + " at: " + this.ip);
		InetSocketAddress address = new InetSocketAddress(this.ip, this.port);
        /* Intializes socket and streams to null */
        Socket s;
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

			//determine if we have information for this server. Register if we don't, tell the server if we do.
			boolean returning = readFromFile();

			try {
                if(!returning) {
                    transmit(new Pair(this.name, register()));
                }
                else {
                    transmit(new Pair(this.name, true)); //send that we already registered
                }
			} catch (Exception e) {
				e.printStackTrace();
			}

            Object o = in.readObject();
			// boolean --> username not in use
            if(o instanceof Boolean) {
                if (!returning) {
                    String tokenValue = JOptionPane.showInputDialog(null, "Please enter your token:", null);
                    transmit(tokenValue);
                    o = in.readObject();

                    //if server sent an error message
                    if(o instanceof String) {
                        errorMessage = (String) o;
                    }
                }
            }
            // string --> username in use or returning user with missing info in server
            else {
                errorMessage = (String) o;
            }

			o = in.readObject();


			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				//doesn't work if process shuts down unexpectedly
				try {
					writeToFile();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (OperationEngineException e) {
					e.printStackTrace();
				}
			}));

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

			// Reads in operations from the server
			o = in.readObject();
			while (o != null) {
			    parseInput(o);
				o = in.readObject();

			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}  finally {
		    // Close connection
			JOptionPane.showMessageDialog(null, errorMessage, "Error", JOptionPane.ERROR_MESSAGE);

			s.close();
			out.close();
			in.close();
			System.exit(1);
		}
	}

	/**
	 * Takes an object that was received from the server and parses it according
	 * to its type. Performs the appropriate calls and updates based on the object received
	 * @param o - object sent from the server to parse
	 * @throws IOException - caused if the operation object is corrupt, or if the socket connection breaks
     */
	@SuppressWarnings("unchecked")
    private void parseInput(Object o) throws IOException {
	    //the server mostly sends pairs to the client - with an operation and its order, or a pair of the list of documents and list of users, or with a history of operations and document instance.
	    if (o instanceof Pair) {
	    	Pair p = (Pair) o;
	    	Object plaintext = p.first;
			// We'll change the temp in the if to the decrypted variable
			if (plaintext instanceof Operation) {
				Operation op = (Operation) plaintext;
				if (getID() == op.getSiteId()) return;
				// We received an operation from the server. Update the local document
				op.setOrder((Integer) p.second);
				updateDoc(op);
			} else if (plaintext instanceof ArrayList) {
				// Updates list of current users and documents
				ArrayList<String> users = (ArrayList<String>) plaintext;
				ArrayList<String> documents = (ArrayList<String>) p.second;
				this.gui.updateUsers(users.toArray());
				this.gui.updateDocumentsList(documents.toArray());
			} else if (plaintext instanceof DocumentInstance) {
			    //we got history and documentinstance from the server, so update this user to the current state using the history.
				ArrayList<EncryptedMessage> history = (ArrayList) p.second;
				if (history.size() > 0) {
					documentState.documentText = updateFromHistory(history, documentState.documentText);
				}
				try {
					this.gui = new ClientGui(documentState.documentText, this, label);
				}
				catch (OperationEngineException e) {
					e.printStackTrace();
				}
				this.gui.setModelKey(document);

				JFrame frame = new JFrame("Collabypto - Demo");
				frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				// Add content to the JFrame window.
				frame.add(this.gui);
				// Display the window.
				frame.pack();
				frame.setVisible(true);

				// Updates the ContextVector of the GUI with the one sent by the server. We use the context vector of the last operation.
				if (history.size() > 0) {
					Operation lastOp = null;
					try {
						lastOp = decrypt(history.get(history.size() - 1));
					}
					catch (Exception e) {
						e.printStackTrace();
					}
					if(documentState.contextVector != null) {
						gui.getCollabModel().setCV(documentState.contextVector);
					}
					ClientState cV = lastOp.getClientState();
					gui.getCollabModel().setCV(cV);
					lastOp.setOrder(history.size() - 1);
					updateDoc(lastOp);
				}
			}
		}
		else if (o instanceof EncryptedMessage) {
			try {
				EncryptedMessage message = (EncryptedMessage) o;
				Operation op = decrypt(message);
				if (getID() == op.getSiteId()) {
					System.err.println("site ID is the same " + getID());
					return; //shouldn't happen when server sends to specific users
				}
				op.setOrder(message.order);
				updateDoc(op);
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
        else if (o instanceof Integer) {
            // The server is sending the unique client identifiers
			if(siteID != -1) return; //siteID is already set
            this.siteID = ((Integer) o).intValue();
            if (this.name.equals("Anonymous")) {
				this.name += "" + this.siteID;
			}
			//TODO: we shouldn't be writing/reading the username twice but removing it seems to make the client/document list disappear.
            out.writeObject(this.name);
            out.flush();
            label = this.name + " is editing document: " + this.document;
		}
		//The server is sending a list of sessioninfos.
		else if (o instanceof ArrayList) {
	        ArrayList lst = (ArrayList) o;
	        try {
	            buildSessions((ArrayList<SessionInfo>) lst);
	        }
	        catch (Exception e) {
	            e.printStackTrace(); }
	    }
		else {
            throw new RuntimeException("Unrecognized object type received by client");
        }
	}

	//builds sessions for the first time from a list given by the server
	private void buildSessions(ArrayList<SessionInfo> sessions) throws UntrustedIdentityException, InvalidKeyException {
		for(SessionInfo session : sessions) {
			//build sessions only for this document
			if(!session.documentID.equals(document)) continue;

			//build session as per library instructions
			SignalProtocolAddress address = new SignalProtocolAddress(session.senderID, 1);
			SessionBuilder sessionBuilder = new SessionBuilder(clientStore, address);
			sessionBuilder.process(session.preKey);  //note: sessionbuilder automatically stores the session in clientstore
			SessionCipher sessionCipher = new SessionCipher(clientStore, address);

			//update our data structures to store the ciphers
			if(sessionCiphers.get(document) == null)  {
				sessionCiphers.put(document, new ArrayList<>());
			}
			sessionCiphers.get(document).add(new ClientSessionCipher(sessionCipher, session.senderID, document));
		}
	}

    //encrypt a message pairwise and send to the server
	private void encrypt(Object plaintext) throws IOException, UntrustedIdentityException {
		Class<?>[] classes = new Class[] { Operation.class};
		XStream xs = new XStream(new DomDriver());
		XStream.setupDefaultSecurity(xs);
		xs.allowTypes(classes);
		int i = 0;
		//find the appropriate sessioncipher to encrypt with
		ClientSessionCipher clientSessionCipher;
		if(sessionCiphers.get(document) == null) return;
		EncryptedMessage[] messages = new EncryptedMessage[sessionCiphers.get(document).size()];
		for(ClientSessionCipher sessionCipher : sessionCiphers.get(document)) {
			clientSessionCipher = sessionCipher;
			String xml = xs.toXML(plaintext);

			//encrypt the string with the sessioncipher
			CiphertextMessage message = clientSessionCipher.sessionCipher.encrypt(xml.getBytes("UTF-8"));

			//create an encrypted message out of the data
			messages[i] = new EncryptedMessage(clientSessionCipher.senderID, getUsername(), clientSessionCipher.documentID, message.serialize());
			i++;
		}
		//turn it into a string so we can send it easily

		out.writeObject(messages);
		out.flush();
	}

	//decrypt a received message
	private Operation decrypt(EncryptedMessage signalMessage) throws DuplicateMessageException, InvalidMessageException, UntrustedIdentityException, LegacyMessageException, InvalidVersionException, InvalidKeyException, InvalidKeyIdException {
		Class<?>[] classes = new Class[] {InsertOperation.class, DeleteOperation.class,  Operation.class};
		XStream xs = new XStream(new DomDriver());
		XStream.setupDefaultSecurity(xs);
		xs.allowTypes(classes);

		//get the encrypted message

		//get the appropriate sessioncipher to decrypt this message
		ClientSessionCipher clientSessionCipher = null;
		for(ClientSessionCipher sessionCipher : sessionCiphers.get(document)) {
			if(sessionCipher.senderID.equals(signalMessage.senderID)) {
				clientSessionCipher = sessionCipher;
			}
		}

		//deserialize the byte array to get the signalmessage
		//decrypt the signal message to get the byte array of the message

		byte[] plaintext;
		try {
			plaintext = clientSessionCipher.sessionCipher.decrypt(new SignalMessage(signalMessage.message));
		}
		catch(Exception e) {
			plaintext = clientSessionCipher.sessionCipher.decrypt(new PreKeySignalMessage(signalMessage.message));
		}

		//convert the bytes to the xml message that the sent operation was converted to
		String xml = new String(plaintext);

		//convert the xml message to the operation that was sent
		return (Operation) xs.fromXML(xml);
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
			if (getID() == o.getSiteId()) return;
			if (o instanceof InsertOperation) {
				this.gui.getCollabModel().remoteOp(o, true);
			} else if (o instanceof DeleteOperation) {
				this.gui.getCollabModel().remoteOp(o, false);
			} else {
				throw new RuntimeException("Shouldn't reach here");
			}
		} catch (OperationEngineException e) {
			new ErrorDialog(e.toString());
		}
	}

	//simulates the history of operations on a stringbuilder to quickly get to the current document state.
    private String updateFromHistory(ArrayList<EncryptedMessage> history, String text) {
		StringBuilder doc = new StringBuilder();
		Operation[] operations = new Operation[history.size()];
		int i = 0;
		for(EncryptedMessage message : history) {
			try {
				//do not use the last operation - we need it to update the context vector
				if(i != operations.length - 1) operations[i] = decrypt(message);
				i++;
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		try {
			doc.append(text);
			for (i = 0; i < operations.length - 1; i++) {
					Operation op = operations[i];
					op.setOrder(i);
				if (op.getKey().equals(document)) {
					if (op instanceof InsertOperation) {
						doc.insert(op.getOffset(), op.getValue());
					}
					else if (op instanceof DeleteOperation) {
						doc.delete(op.getOffset(), op.getOffset() + op.getValue().length());
					}
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return doc.toString();
	}

	/**
	 * @return the siteID of the document
	 */
	@Override
	public int getID() {
		return siteID;
	}

	@Override
	public void transmit(Object o) throws IOException {
		transmit(o, ENCRYPTION_METHOD.NONE);
	}

	/**
	 * Transmits local changes to the server via an operatoin
	 * @param o the operation to transmit to server
	 * @throws IOException if the OutputStream is corrupted or broken
	 */
	public void transmit(Object o, ENCRYPTION_METHOD encryption) throws IOException {
		if (out == null)
			throw new RuntimeException("Socket not initialized.");
		switch (encryption) {
			case NONE:
				out.writeObject(o);
				out.flush();
				break;
			case SIGNAL:
				try {
					encrypt(o);
				} catch (UntrustedIdentityException e) {
					e.printStackTrace();
				}
				break;
			default:
				break;
		}
	}

	//update list of available documents
	public Object[] readNewDocumentsList() throws IOException, ClassNotFoundException {
		Object o = in.readObject();
		if(o == null) {
			return null;
		}
		if(!(o instanceof ArrayList)) {
			throw new SocketException("Expected documents list, got " + o.getClass());
		}
		ArrayList<String> documentsList = (ArrayList<String>) o;
		return documentsList.toArray();
	}

	/**
	 * Used by the document selector popup to set the
	 * @param text new name of the document
	 */
	public void setDocument(String text) {
		this.document = text;
	}

	//get list of registered users
	public ArrayList<String> getRegisteredUserList() throws IOException, ClassNotFoundException {
		Object input = in.readObject();
		if(!(input instanceof  ArrayList)) {
			throw new IOException("Expected arraylist of registered users");
		}
		return (ArrayList<String>) input;
	}

	/** @return username of client */
	public String getUsername() {
	    return this.name;
	}
}
