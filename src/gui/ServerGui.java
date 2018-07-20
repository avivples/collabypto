package gui;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;

import server_client.CollabModel;
import server_client.CollabInterface;
import server_client.CollabServer;
import controller.DocumentSelectionListener;
import controller.TextChangeListener;
import document.OperationEngineException;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

/*
 * Testing Strategy:
 * See ClientGui
 * In addition to what currently tested in ClientGUI, the text documents of the server
 * is uneditable, everything else is identical to the strategy indicated in Client Gui
 * So we can tested by trying to edit the documents
 * 
 * This follows the MVC design pattern too*/

/**
 * ServerGui inherit from Edit Gui, it includes some methods to get the
 * collabModel getSideID and setModelKey. Thread-safe argument This is
 * thread-safe for the same reason as Client GUI since it is run in the seperate
 * swing thread which won't interfere with the main thread. See Client GUI
 * thread-safe argument for more information
 * 
 * @author
 * 
 */

public class ServerGui extends JPanel {

	private static final long serialVersionUID = -1426186299786063098L;
	//private final DocumentSelectionListener controller;
	private final CollabServer collabServer;
	/** The initial String to show in the Documents */
	private static final String WELCOME_MESSAGE = "Welcome to Collab Edit";
	/**
	 * The name to show on top of the documents to see whose document belong to
	 */
	private static final String PROMPT_FOR_SERVER = "Server for document: ";

    public static void main(String[] args) throws ClassNotFoundException, UnsupportedLookAndFeelException, InstantiationException, IllegalAccessException {
        UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        CollabServer server = new CollabServer("0.0.0.0", 4444, "server");
	    ServerGui gui = new ServerGui(server);
        Thread thread = new Thread() {
            @Override
            public void run() {
                gui.collabServer.start(gui);
            }
        };
        thread.start();
    }

	/**
	 * ServerGui sets up the GUI for the local client
	 */
	public ServerGui(CollabServer server) {
        collabServer = server;
        createGUI();
	}

	public void createGUI() {
		JFrame f = new JFrame("Collabypto Demo: Server GUI");
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
				String token = collabServer.generateToken();
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


}
