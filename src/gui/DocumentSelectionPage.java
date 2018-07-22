package gui;

import document.OperationEngineException;
import document.Pair;
import org.apache.commons.lang3.RandomStringUtils;
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
	private CollabClient client;


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
	    this.client = client;
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
                    // TODO: need to check all the documents on the server - not just unique to the user
                    // TODO: check shouldn't be on the client side
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
                    ArrayList<String> userList = new ArrayList<>();
                    try {
                        client.transmit(userList);
                        userList = client.getUserList();
                        userList.remove(client.getUsername());
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                    JFrame f = new JFrame("Invite Users");
                    JPanel leftPanel = new JPanel();
                    leftPanel.setLayout(new BorderLayout());
                    leftPanel.setPreferredSize(new Dimension(400, 200));
                    leftPanel.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createTitledBorder(""),
                            BorderFactory.createEmptyBorder(10, 10, 10, 10)));

                    DefaultListModel availModel = new DefaultListModel();
                    for(String u : userList) availModel.addElement(u);
                    sortListModel(availModel);
                    JLabel avail = new JLabel("Available Users");
                    JList<String> availList = new JList(availModel);
                    leftPanel.add(avail, BorderLayout.PAGE_START);
                    leftPanel.add(new JScrollPane(availList), BorderLayout.CENTER);

                    JPanel rightPanel = new JPanel();
                    rightPanel.setLayout(new BorderLayout());
                    rightPanel.setPreferredSize(new Dimension(400, 200));
                    rightPanel.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createTitledBorder(""),
                            BorderFactory.createEmptyBorder(10, 10, 10, 10)));
                    JLabel selected = new JLabel("Selected Users");

                    DefaultListModel selectedModel = new DefaultListModel();
                    JList<String> selectedList = new JList(selectedModel);
                    rightPanel.add(selected, BorderLayout.PAGE_START);
                    rightPanel.add(new JScrollPane(selectedList), BorderLayout.CENTER);


                    JButton add = new JButton("Add");
                    JButton remove = new JButton("Remove");

                    add.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            int index = availList.getSelectedIndex();
                            String name = availList.getSelectedValue();
                            availModel.remove(index);
                            selectedModel.addElement(name);
                        }
                    });

                    remove.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            int index = selectedList.getSelectedIndex();
                            String name = selectedList.getSelectedValue();
                            selectedModel.remove(index);
                            availModel.addElement(name);
//                            sortListModel(availModel);
                        }
                    });

                    JPanel subPanel = new JPanel();

                    subPanel.setLayout(new GridBagLayout());
                    GridBagConstraints gbc = new GridBagConstraints();
                    gbc.gridwidth = GridBagConstraints.REMAINDER;
                    gbc.fill = GridBagConstraints.HORIZONTAL;

                    subPanel.add(add, gbc);
                    subPanel.add(remove, gbc);

                    JButton inviteButton = new JButton("Send Document Invites");
                    inviteButton.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent e) {
                            ArrayList<String> clientNames = new ArrayList<>();
                            // Get all the selected items using the indices
                            for (int i = 0; i < selectedList.getModel().getSize(); i++) {
                                clientNames.add(selectedList.getModel().getElementAt(i));
                            }

                            if(!clientNames.contains(client.getUsername())) {
                                clientNames.add(client.getUsername());
                            }

                            client.setDocument(documentInput.getText());

                            try {
                                //give the document name and client list to the server.
                                client.transmit(new Pair(documentInput.getText(), false));
                                client.transmit(clientNames);
                            }
                            catch (IOException ex) {
                                ex.printStackTrace();
                            }
                            dispose();
                            f.dispose();
                        }
                    });

                    f.add(leftPanel, BorderLayout.WEST);
                    f.add(rightPanel, BorderLayout.EAST);
                    f.add(inviteButton, BorderLayout.PAGE_END);
                    f.add(subPanel, BorderLayout.CENTER);
                    f.pack();
                    f.setVisible(true);
                }

                else {
                    new ErrorDialog("Please enter the name of the document");
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
        JFrame f = new JFrame("Create Invite");
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new FlowLayout());
        mainPanel.setPreferredSize(new Dimension(500, 200));
        mainPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(""),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        JLabel token = new JLabel("Token:");
        JTextField text = new JTextField(10);
        text.setPreferredSize(new Dimension(200,30));
        text.setEditable(false);
        JButton button = new JButton("Create Invitation");
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
                try {
                    client.transmit(token);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
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
        String generatedString = RandomStringUtils.randomAlphanumeric(10);
        return generatedString;
    }

    private void sortListModel(DefaultListModel<String> listModel) {
        ArrayList<String> items = new ArrayList<String>();
        for (int i = 0; i < listModel.getSize(); i++) {
            items.add(listModel.getElementAt(i));
        }
        Collections.sort(items); // sort
        listModel.clear();       // remove all elements
        for(String e : items) {  // add elements
            listModel.addElement(e);
        }
    }

}
