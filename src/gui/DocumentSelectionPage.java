package gui;

import server_client.CollabClient;

import javax.crypto.NoSuchPaddingException;
import javax.swing.*;
import javax.swing.GroupLayout.Alignment;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;

/**
 * The UI for the users to select the document to edit.
 */
public class DocumentSelectionPage extends JFrame {

	private static final long serialVersionUID = 8079538911030861097L;

	private static final String INSTRUCTION = "Select Document to Edit Or Create New Document";
	private static final String SELECT_BUTTON = "Select Document";
	private static final String NEW_DOCUMENT = "New Document";

	// Holds the list of documents to display
	private JList listOfDocument;
    private DefaultListModel listDocumentModel;
    // Holds name of created document
	private JTextArea documentInput;


    /**
	 * This constructor takes in the list of documents and the server_client to create
	 * the GUI directly after created
	 * 
	 * @param documents
	 *            the list of name of the documents
	 * @param client
	 *            the CollabClient
	 */
	public DocumentSelectionPage(ArrayList<String> documents, final CollabClient client) {
		Collections.sort(documents);
		//TODO: Add the user's name as the frame's title
		JPanel mainPanel = new JPanel();
		mainPanel.grabFocus();
		//creates an action that refreshes the document list, and binds it to F5.
		Action updateDocuments = new AbstractAction("updateDocuments") {
			@Override
			public void actionPerformed(ActionEvent e) {
					try {
						//asks the server for the list of documents.
						client.transmit("give documents please thanks");
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					try {
						updateDocumentsList(client.readNewDocumentsList());
					} catch (IOException | ClassNotFoundException e1) {
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
					//when the client creates a new document, we ask them to provide the list of clients that can access the document.
					String clientCommas = JOptionPane.showInputDialog("Enter list of clients to share with separated by commas:");
					String[] clientNames = clientCommas.split(",");
					String[] clientList;
					if(clientNames[0].equals("") && clientNames.length == 1) {
						clientList = new String[1];
						clientList[0] = client.getUsername();
					}
					else {
						clientList = new String[clientNames.length + 1];
						System.arraycopy(clientNames, 0, clientList, 0, clientList.length - 1);
						clientList[clientList.length - 1] = client.getUsername();
					}
					// TODO: We're not checking if clientList is empty for now or if the clients exist (assume client is right)
					client.setDocument(documentInput.getText());

					try {
						//give the document name and client list to the server.
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

	private void updateDocumentsList(Object[] documents) {
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
