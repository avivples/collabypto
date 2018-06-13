package gui;

import server_client.CollabClient;

import javax.swing.*;
import javax.swing.GroupLayout.Alignment;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;


/*Gui Testing strategy:
 *   There are two buttons and two views for this GUI. The user have to options:
 *  	if the user clicks on any availabel documents , he will be directed to that documents.
 *  	if the uesr type in new documents and clicks on new documents, new documents will be created
 *  	and he will be directed to the main document editing GUI.
 *      if the user selects any of the button without specifying the document or type in the document
 *      name , there will be an error dialog popup to ask him to specify those, but this won't affect
 *      the procudure at all.
 *      if the user close the window in this procedure, connection will be closed.
 *  
 */

/**
 * The UI for the users to select the document to edit. This inheritted from
 * JFrame. Thread-safe argument: This UI is thread safe since every thing is
 * confined , and it exits when the user closes it.
 * 
 * @author viettran
 * 
 */
public class DocumentSelectionPage extends JFrame {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8079538911030861097L;
	/** Instruction for user */
	private static final String INSTRUCTION = "Select Document to Edit Or Create New Document";
	/** constant string for select button */
	private static final String SELECT_BUTTON = "Select Document";
	/** constant string for making new document button */
	private static final String NEW_DOCUMENT = "New Document";
	/** The JList that currently hold the list of documents to display */
	JList listOfDocument;

	/** The ArrayList that hold the names of all documents */

	ArrayList<String> documents;
	/**
	 * The underlying list documents model that in charge of modifying the
	 * internal documents
	 */
	DefaultListModel listDocumentModel;
	/** The Button used to select the documents to edit */
	JButton selectButton;
	/** JTextArea to hold the name of new document created */
	JTextArea documentInput;
	/** The server_client */
	CollabClient client;

	/**
	 * This constructor takes in the list of documents and the server_client to create
	 * the GUI directly after created
	 * 
	 * @param documents
	 *            the list of name of the documents
	 * @param client
	 *            the CollabClient
	 */
	public DocumentSelectionPage(ArrayList<String> documents,
                                 final CollabClient client) {
		Collections.sort(documents);
		JPanel mainPanel = new JPanel();
		mainPanel.grabFocus();
		Action updateDocuments = new AbstractAction("updateDocuments") {
			@Override
			public void actionPerformed(ActionEvent e) {
					try {
						client.transmit("give documents please thanks");
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					try {
						updateDocumentsList(client.readNewDocumentsList());
					} catch (IOException e1) {
						e1.printStackTrace();
					} catch (ClassNotFoundException e1) {
						e1.printStackTrace();
					}
			}
		};
		mainPanel.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "updateDocuments");
		mainPanel.getActionMap().put("updateDocuments", updateDocuments);
		setContentPane(mainPanel);
		JPanel leftPane = new JPanel(new BorderLayout());
		leftPane.requestFocusInWindow();
		JLabel label = new JLabel(INSTRUCTION);
		listDocumentModel = new DefaultListModel();
		for (String doc : documents) {
			listDocumentModel.addElement(doc);
		}

		listOfDocument = new JList(listDocumentModel);

		listOfDocument.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		JScrollPane documentScrollPane = new JScrollPane(listOfDocument);
		documentScrollPane.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "updateDocuments");
		documentScrollPane.getActionMap().put("updateDocuments", updateDocuments);
		documentScrollPane.requestFocusInWindow();
		documentScrollPane.setPreferredSize(new Dimension(250, 145));
		documentScrollPane.setMinimumSize(new Dimension(10, 10));
		documentScrollPane.setAutoscrolls(true);

		JButton selectButton = new JButton(SELECT_BUTTON);
		selectButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				int index = listOfDocument.getSelectedIndex();
				if (index != -1) {
				    String docName = (String) listDocumentModel.get(index);
					client.setDocument(docName);
                    try {
                        client.transmit(docName);
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    dispose();
				} else {
					new ErrorDialog(
							"Please select the document you want to edit");
				}

			}
		});

		leftPane.add(label, BorderLayout.PAGE_START);
		leftPane.add(documentScrollPane, BorderLayout.CENTER);
		leftPane.add(selectButton, BorderLayout.PAGE_END);

		JPanel rightPane = new JPanel();
		GroupLayout newDocumentLayout = new GroupLayout(rightPane);
		documentInput = new JTextArea();
		documentInput.setToolTipText("Enter the name of the Document");
		documentInput.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "updateDocuments");
		documentInput.getActionMap().put("updateDocuments", updateDocuments);
		JButton newDocumentButton = new JButton(NEW_DOCUMENT);
		newDocumentButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (documentInput.getText().length() > 0) {
					String clientCommas = JOptionPane.showInputDialog("Enter list of clients to share with separated by commas:");
					String[] clientNames = clientCommas.split(",");
					String[] clientList = new String[clientNames.length + 1];
					for (int i = 0; i < clientList.length - 1; i++) {
						clientList[i] = clientNames[i];
					}
					clientList[clientList.length - 1] = client.getUsername();
					// TODO: We're not checking if clientList is empty for now or if the clients exist (assume client is right)
					client.setDocument(documentInput.getText());
					client.setClientList(clientList);
					try {
						client.transmit(documentInput.getText());
						client.transmit(clientList);
					} catch (IOException e) {
						e.printStackTrace();
					}

					dispose();
				} else {
					new ErrorDialog("Please Enter the name of the Document");
				}

			}

		});
		newDocumentLayout.setHorizontalGroup(newDocumentLayout
				.createParallelGroup(Alignment.LEADING)
				.addComponent(documentInput).addComponent(newDocumentButton));
		newDocumentLayout.setVerticalGroup(newDocumentLayout
				.createSequentialGroup().addComponent(documentInput)
				.addComponent(newDocumentButton));
		rightPane.setLayout(newDocumentLayout);
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
				leftPane, rightPane);
		mainPanel.add(splitPane);
		pack();
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setResizable(false);
		setVisible(true);

	}

	/**
	 * This will add the appropriate document to the underlying listModel
	 * 
	 * @param document
	 *            modifies: the listModel
	 */
	public void addDocument(String document) {
		listDocumentModel.addElement(document);
	}

	/**
	 * This will delete the appropriate document from the underlying listModel
	 * 
	 * @param document
	 *            modifies: the listModel
	 */
	public void deleteDocument(String document) {
		listDocumentModel.removeElement(document);
	}

	public void updateDocumentsList(Object[] documents) {
		listDocumentModel.clear();
		if (documents.length > 0) {
			for (Object i : documents) {
				listDocumentModel.addElement(i);
			}
		}
		this.repaint();
		this.revalidate();
	}
}
