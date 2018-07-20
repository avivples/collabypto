package gui;

import document.OperationEngineException;
import document.Pair;
import server_client.CollabClient;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * The UI for the users to select the document to edit.
 */
public class DocumentSelectionPage extends JFrame {

	private static final long serialVersionUID = 8079538911030861097L;

	private static final String INSTRUCTION = "Select An Existing Document to Edit";
	private static final String SELECT_BUTTON = "Select Document";
	private static final String REFRESH_BUTTON = "Refresh List";
	private static final String NEW_DOCUMENT = "New Document";
    private static final String GENERATE_TOKENS = "Generate Tokens";


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
		JPanel mainPanel = new JPanel();
		Border border = BorderFactory.createTitledBorder("Welcome " + client.getUsername() + "!");
		mainPanel.setBorder(border);
		mainPanel.grabFocus();
		//creates an action that refreshes the document list, and binds it to F5.
		Action updateDocuments = new AbstractAction("updateDocuments") {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					//asks the server for the list of documents.
					client.transmit("refresh");
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
		Border border2 = BorderFactory.createLineBorder(Color.BLACK);
		documentScrollPane.setBorder(BorderFactory.createCompoundBorder(border2,
				BorderFactory.createEmptyBorder()));
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
						client.transmit(new Pair(docName, client.readDocument()));
					} catch (IOException e1) {
						e1.printStackTrace();
					} catch (OperationEngineException e1) {
						e1.printStackTrace();
					}
					dispose();
				} else {
					new ErrorDialog(
							"Please select the document you want to edit");
				}
			}
		});

		JButton refreshButton = new JButton(REFRESH_BUTTON);
		refreshButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					//asks the server for the list of documents.
					client.transmit("refresh");
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				try {
					updateDocumentsList(client.readNewDocumentsList());
				} catch (IOException | ClassNotFoundException e1) {
					e1.printStackTrace();
				}
			}
		});

		leftPane.add(label, BorderLayout.PAGE_START);
		leftPane.add(documentScrollPane, BorderLayout.CENTER);
		JPanel subPanel = new JPanel();
		subPanel.add(selectButton);
		subPanel.add(refreshButton);
		leftPane.add(subPanel, BorderLayout.PAGE_END);

		JPanel rightPane = new JPanel(new BorderLayout());
		rightPane.requestFocusInWindow();
		JLabel label2 = new JLabel("Create A New Document");
		documentInput = new JTextArea();
		documentInput.setBorder(BorderFactory.createCompoundBorder(border2,
				BorderFactory.createEmptyBorder()));
		documentInput.setPreferredSize(new Dimension(250, 20));
		documentInput.setMinimumSize(new Dimension(10, 10));

		JButton newDocumentButton = new JButton(NEW_DOCUMENT);
		newDocumentButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (documentInput.getText().length() > 0) {

					// Check if document name already exists
					ListModel model = listOfDocument.getModel();
					for(int i = 0; i < model.getSize(); i++) {
						String doc = (String) model.getElementAt(i);
						if(documentInput.getText().equals(doc)) {
							new ErrorDialog("Document name already exists! Please enter a new name.");
							return;
						}
					}

					//when the client creates a new document, we ask them to provide the list of clients that can access the document.
                    // TODO: client selection list goes here.
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
						client.transmit(new Pair(documentInput.getText(), false));
						client.transmit(clientList);
					} catch (IOException e) {
						e.printStackTrace();
					}
					dispose();
				} else {
					new ErrorDialog("Please Enter the name of the document");
				}

			}

		});

        JButton generateTokenButton = new JButton(GENERATE_TOKENS);
        generateTokenButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                generateTokenGUI();
            }
        });

        rightPane.add(label2, BorderLayout.PAGE_START);
		rightPane.add(documentInput, BorderLayout.CENTER);
		JPanel subPanel2 = new JPanel();
		subPanel2.add(newDocumentButton);
		subPanel2.add(generateTokenButton);
		rightPane.add(subPanel2, BorderLayout.PAGE_END);

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPane, rightPane);
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

    public void generateTokenGUI() {
        JFrame f = new JFrame("Generate Tokens");
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new FlowLayout());
        mainPanel.setPreferredSize(new Dimension(500, 200));
        mainPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(""),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        JLabel token = new JLabel("Token Generation:");
        JTextField text = new JTextField(10);
        text.setPreferredSize(new Dimension(200,30));
        text.setEditable(false);
        JButton button = new JButton("Generate Token");
        mainPanel.add(token, BorderLayout.PAGE_START);
        mainPanel.add(text, BorderLayout.CENTER);
        mainPanel.add(button, BorderLayout.PAGE_END);
        f.add(mainPanel);
        f.pack();
        f.setVisible(true);
        JLabel copied = new JLabel("Token copied to clipboard! Send token to invite!");

        button.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                String token = generateToken();
                text.setText(token);
                StringSelection stringSelection = new StringSelection(token);
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(stringSelection, null);
                mainPanel.add(copied);
                f.revalidate();
                f.repaint();
            }
        });
    }

    public String generateToken() {
//        String generatedString = RandomStringUtils.randomAlphanumeric(10);
        String generatedString = "aaaa";
        return generatedString;
    }

}
