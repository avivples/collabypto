package server_client;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import document.*;
import org.apache.commons.lang3.SerializationUtils;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.protocol.PreKeySignalMessage;
import org.whispersystems.libsignal.protocol.SignalMessage;
import org.whispersystems.libsignal.state.*;
import gui.ClientGui;
import gui.DocumentSelectionPage;
import gui.ErrorDialog;

import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.whispersystems.libsignal.*;
import org.whispersystems.libsignal.state.impl.InMemorySignalProtocolStore;
import org.whispersystems.libsignal.util.KeyHelper;
import org.whispersystems.libsignal.util.Medium;
import signal.ClientSessionCipher;
import signal.RegistrationInfo;
import signal.SessionInfo;
import signal.EncryptedMessage;

import javax.crypto.*;

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
	private int siteID = -1;
	/** document the client is editing */
	private String document = "";
	/** list of clients current doc is shared with */

	// storing the session ciphers
	private HashMap<String, ArrayList<ClientSessionCipher>> sessionCiphers = new HashMap<>();

	private int numClientsInDocument;

	/** port number of client */
	private int port;
	/** ip number of client */
	private String ip;

	private String serverName;

	private String dir;
	/** username of client */
	private String name;

	//the text of the document
	private DocumentState documentState = new DocumentState("", null);

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

	protected InMemorySignalProtocolStore clientStore;

	// for demo purposes each client can create one document
	protected Cipher cipherEnc;

	protected Cipher cipherDec;

	protected SecretKey key;

	// TODO: Remove ALL AES stuff
	byte[] keyBytes;
	byte[] iv;

	public enum ENCRYPTION_METHOD {
		NONE, AES, SIGNAL
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
		this.serverName = ip + "-" + port;
		this.dir =  "users/" + name + "/" + serverName;
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
        } catch (InvalidKeyException e) {
			e.printStackTrace();
		}
	}


    //Creates a list of bundles to pass to the server, so the server can create sessions with other users.
	private RegistrationInfo register () throws InvalidKeyException {
		// TODO: At some point add authentication and also local store instead of only memory store
		IdentityKeyPair	identityKeyPair = KeyHelper.generateIdentityKeyPair();
		int registrationId  = KeyHelper.generateRegistrationId(true);
		int startId = new Random().nextInt(Medium.MAX_VALUE);
		List<PreKeyRecord> preKeys = KeyHelper.generatePreKeys(startId, 100);
		SignedPreKeyRecord signedPreKey = KeyHelper.generateSignedPreKey(identityKeyPair, 5); // why 5?

		// Create a list of PreKeyBundle
		List<PreKeyBundle> preKeyBundles = createPreKeyBundles(identityKeyPair, registrationId, preKeys, signedPreKey);
		// Store (in memory for now, later using local DB)
		clientStore = new InMemorySignalProtocolStore(identityKeyPair, registrationId);
		for (PreKeyRecord pK : preKeys) {
			clientStore.storePreKey(pK.getId(), pK);
			pK.serialize();
		}
		clientStore.storeSignedPreKey(signedPreKey.getId(), signedPreKey);
		RegistrationInfo registrationInfo = new RegistrationInfo(preKeyBundles);
		return registrationInfo;
	}

	// write information on exit to load when client rejoins
	private void writeToFile() throws IOException, OperationEngineException {
		Class<?>[] classes = new Class[] {HashMap.class, ClientSessionCipher.class};
		XStream xs = new XStream(new DomDriver());
		XStream.setupDefaultSecurity(xs);
		xs.allowTypesByWildcard(new String[] {"org.whispersystems.libsignal.**"});
		xs.allowTypes(classes);

		String fileName = dir + "/clientStore.txt";
		File clientStoreFile = new File(fileName);
		clientStoreFile.getParentFile().mkdirs();
		clientStoreFile.createNewFile();
		String xml = xs.toXML(clientStore);
		writeXMLToFile(clientStoreFile, xml);


		if(!document.equals("")) {
			DocumentState documentState = new DocumentState(gui.getCollabModel().getDocumentText(), gui.getCollabModel().copyOfCV());
			fileName = dir + "/doc-" + document + ".txt";
			File documentStateFile = new File(fileName);
            documentStateFile.createNewFile();
			xml = xs.toXML(documentState);
			writeXMLToFile(documentStateFile, xml);
		}
		fileName = dir + "/sessionCiphers.txt";
		File sessionCiphersFile = new File(fileName);
        sessionCiphersFile.createNewFile();
		xml = xs.toXML(sessionCiphers);
		writeXMLToFile(sessionCiphersFile, xml);
	}

	private void writeXMLToFile(File file, String xml) throws IOException {
		FileWriter writer = new FileWriter(file, false);
		writer.write(xml);
		writer.close();
	}

	//reads and sets clientstore and sessionciphers. Returns true if we had this information, false otherwise. There is currently no check to missing/faulty files/directories, but fake data would just cause you to crash rather than get access.
	private boolean readFromFile() throws IOException {
		Class<?>[] classes = new Class[] {HashMap.class, ClientSessionCipher.class};
		XStream xs = new XStream(new DomDriver());
		XStream.setupDefaultSecurity(xs);
		xs.allowTypesByWildcard(new String[] {"org.whispersystems.libsignal.**"});
		xs.allowTypes(classes);

		if(!new File(dir).exists()) {
			return false;
		}

		String fileName = dir + "/clientStore.txt";
		File clientStoreFile = new File(fileName);
		String xml = readAllFile(clientStoreFile);
		clientStore = (InMemorySignalProtocolStore) xs.fromXML(xml);

		// TODO: when selecting a document (not here) check if file exists, if so use it, if not ask server for sessions

		fileName = dir + "/sessionCiphers.txt";
		File sessionCiphersFile = new File(fileName);
		xml = readAllFile(sessionCiphersFile);
		sessionCiphers = (HashMap<String, ArrayList<ClientSessionCipher>>) xs.fromXML(xml);

		return true;
	}

	public boolean readDocument() throws IOException, OperationEngineException {
		Class<?>[] classes = new Class[] {DocumentState.class };
		XStream xs = new XStream(new DomDriver());
		XStream.setupDefaultSecurity(xs);
		xs.allowTypes(classes);
		File documentFile = new File(dir + "/doc-" + document + ".txt");
		if(!documentFile.exists()) return false;

		String xml = readAllFile(documentFile);
		this.documentState = (DocumentState) xs.fromXML(xml);
		return true;
	}

	private String readAllFile(File file) throws IOException {
		FileInputStream fis = new FileInputStream(file);
		byte[] data = new byte[(int) file.length()];
		fis.read(data);
		fis.close();

		String str = new String(data);
		return str;
	}

	private List<PreKeyBundle> createPreKeyBundles(IdentityKeyPair	identityKeyPair,
												   int registrationId, List<PreKeyRecord> preKeys,
												   SignedPreKeyRecord signedPreKey) {

		List<PreKeyBundle> preKeyBundles = new ArrayList<>();
		for (PreKeyRecord pK : preKeys) {
			PreKeyBundle preKeyBundle = new PreKeyBundle(registrationId, 1, pK.getId(),
					pK.getKeyPair().getPublicKey(), 5, signedPreKey.getKeyPair().getPublicKey(),
					signedPreKey.getSignature(), identityKeyPair.getPublicKey());
			preKeyBundles.add(preKeyBundle);
		}
		return preKeyBundles;
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
	public void connect() throws IOException, InvalidKeyException {
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

			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				//doesnt work if process shuts down unexpectedly
				try {
					writeToFile();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (OperationEngineException e) {
					e.printStackTrace();
				}
			}));
			// TODO: authentication
			try {
			if(!readFromFile()) {
				transmit(new Pair(getUsername(), register()));
			}
			else {
				transmit(new Pair(getUsername(), new Boolean(true))); //send that we already registered
			}
			} catch (Exception e) {
				e.printStackTrace();
			}
			Object o = in.readObject();
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
				DocumentInstance documentInstance = (DocumentInstance) plaintext;
				ArrayList<EncryptedMessage> history = (ArrayList) p.second;
				if (history.size() > 0) {
					documentState.documentText = updateFromHistory(history, documentState.documentText);
				}
				try {
					this.gui = new ClientGui(documentState.documentText, this, label);
				} catch (OperationEngineException e) {
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
					System.out.println("site ID is the same " + getID());
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
			//TODO: we shouldn't be writing/reading the username twice but removing it seems to make the client/document list disappear. Fix
            out.writeObject(this.name);
            out.flush();
            label = this.name + " is editing document: " + this.document;
		}
		//The server is sending a list of sessioninfos.
		else if (o instanceof ArrayList) {
			try {
				buildSessions((ArrayList<SessionInfo>) o);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		else {
            throw new RuntimeException("Unrecognized object type received by client");
        }
	}

	//builds sessions for the first time from a list given by the server
	private void buildSessions(ArrayList<SessionInfo> sessions) throws UntrustedIdentityException, InvalidKeyException {
		for(SessionInfo session : sessions) {
			if(!session.documentID.equals(document)) continue;
			SignalProtocolAddress address = new SignalProtocolAddress(session.senderID, 1);
		//	clientStore.storeSession(address, new SessionRecord(new SessionState()));
		//	clientStore.saveIdentity(address, session.preKey.getIdentityKey());
			SessionBuilder sessionBuilder = new SessionBuilder(clientStore, address);
			sessionBuilder.process(session.preKey);  //note: sessionbuilder automatically stores the session in clientstore
			SessionCipher sessionCipher = new SessionCipher(clientStore, address);
			if(sessionCiphers.get(session.senderID) == null)  {
				sessionCiphers.put(session.senderID, new ArrayList<>());
			}
			//System.out.println("sender: " + session.senderID + " recipient: " + getUsername() +  " " + session.preKey.getPreKeyId());
			sessionCiphers.get(session.senderID).add(new ClientSessionCipher(sessionCipher, session.senderID, document));
		}
	}


	private Object encrypt(Object plaintext) throws IOException, UntrustedIdentityException {
		if(numClientsInDocument == 1) return null;
		Class<?>[] classes = new Class[] { Operation.class};
		XStream xs = new XStream(new DomDriver());
		XStream.setupDefaultSecurity(xs);
		xs.allowTypes(classes);
		EncryptedMessage[] messages = new EncryptedMessage[sessionCiphers.size()];
		int i = 0;
		for(String client : sessionCiphers.keySet()) {
			//find the appropriate sessioncipher to encrypt with
			ClientSessionCipher clientSessionCipher = null;
			for(ClientSessionCipher sessionCipher : sessionCiphers.get(client)) {
				//TODO change structure of sessionciphers to be specific to some document as well.get(client);
				if(sessionCipher.documentID.equals(document)) {
					clientSessionCipher = sessionCipher;
				}
			}
			if(clientSessionCipher == null) continue;
			//turn it into a string so we can send it easily
			String xml = xs.toXML(plaintext);

			//encrypt the string with the sessioncipher
			CiphertextMessage message = clientSessionCipher.sessionCipher.encrypt(xml.getBytes("UTF-8"));

			//create an encrypted message out of the data
			messages[i] = new EncryptedMessage(clientSessionCipher.senderID, getUsername(), clientSessionCipher.documentID, message.serialize());
			i++;
		}
		out.writeObject(messages);
		out.flush();
		return plaintext;
	}

	private Operation decrypt(EncryptedMessage signalMessage) throws DuplicateMessageException, InvalidMessageException, UntrustedIdentityException, LegacyMessageException, InvalidVersionException, InvalidKeyException, InvalidKeyIdException {
		Class<?>[] classes = new Class[] {InsertOperation.class, DeleteOperation.class,  Operation.class};
		XStream xs = new XStream(new DomDriver());
		XStream.setupDefaultSecurity(xs);
		xs.allowTypes(classes);

		//get the encrypted message

		//get the appropriate sessioncipher to decrypt this message
		ClientSessionCipher clientSessionCipher = null;
		for(ClientSessionCipher sessionCipher : sessionCiphers.get(signalMessage.senderID)) {
			//TODO change structure of sessionciphers to be specific to some document as well.get(client);
			if(sessionCipher.documentID.equals(document)) {
				clientSessionCipher = sessionCipher;
			}
		}

		//deserialize the byte array to get the signalmessage
		//decrypt the signal message to get the byte array of the message
		byte[] plaintext;
		//TODO: change to flag (prekeysignalmessage is sent when you have yet to receive a message, signalmessage is sent afterwards)
		try {
			plaintext = clientSessionCipher.sessionCipher.decrypt(new SignalMessage(signalMessage.message));
		}
		catch(Exception e) {
			plaintext = clientSessionCipher.sessionCipher.decrypt(new PreKeySignalMessage(signalMessage.message));
		}


		//convert the bytes to the xml message that the sent operation was converted to
		String xml = new String(plaintext);
		//convert the xml message to the operation that was sent
		Operation op = (Operation) xs.fromXML(xml);
		return op;
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
				this.gui.getCollabModel().remoteOp((Operation) o, true);
			} else if (o instanceof DeleteOperation) {
				this.gui.getCollabModel().remoteOp((Operation) o, false);
			} else {
				throw new RuntimeException("Shouldn't reach here");
			}
		} catch (OperationEngineException e) {
			new ErrorDialog(e.toString());
		} catch (BadLocationException e) {
			new ErrorDialog(e.toString());
		}
	}

	//simulates the history of operations on a stringbuilder to quickly get to the current document state.
	public String updateFromHistory(ArrayList<EncryptedMessage> history, String text) throws OperationEngineException {
		StringBuilder doc = new StringBuilder(text);
		Operation[] operations = new Operation[history.size()];
		int i = 0;
		for(EncryptedMessage message : history) {
			try {
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
					} else if (op instanceof DeleteOperation) {
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
			case AES:
				try {
					//encryptAES(o);
				} catch (Exception e) {
					e.printStackTrace();
				}
				//			out.flush();
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

	public void setCipher() throws NoSuchPaddingException, NoSuchAlgorithmException,
			java.security.InvalidKeyException, InvalidAlgorithmParameterException {
		SecureRandom random = new SecureRandom();
		this.keyBytes = new byte[16];
		this.iv = new byte[16];
		random.nextBytes(keyBytes);
		random.nextBytes(iv);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		this.cipherEnc = Cipher.getInstance("AES/CBC/PKCS5Padding");
		this.cipherDec = Cipher.getInstance("AES/CBC/PKCS5Padding");
		this.key = new SecretKeySpec(keyBytes, "AES");
		this.cipherEnc.init(Cipher.ENCRYPT_MODE, this.key, ivSpec);
		this.cipherDec.init(Cipher.DECRYPT_MODE, this.key, ivSpec);
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


/*
	private Object decryptAES(Object cipherText) throws IOException, BadPaddingException, IllegalBlockSizeException {
		CipherInputStream cipherInputStream = new CipherInputStream(in, cipherDec);
		ObjectInputStream inputStream = new ObjectInputStream(cipherInputStream);
		SealedObject sealedObject;
		try {
			sealedObject = (SealedObject) inputStream.readObject();
			return sealedObject.getObject(cipherDec);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	private void encryptAES(Object plaintext) throws IOException, IllegalBlockSizeException, java.security.InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, ClassNotFoundException {
		String transformation = "AES/CBC/PKCS5Padding";
		SecretKeySpec sks = new SecretKeySpec(keyBytes, "AES");
		cipherEnc = Cipher.getInstance(transformation);
		cipherEnc.init(Cipher.ENCRYPT_MODE, sks);
		SealedObject sealedObject = new SealedObject((Serializable)plaintext, cipherEnc);
		// Wrap the output stream
		out.writeObject(sealedObject);
		out.flush();
	}
*/

}
