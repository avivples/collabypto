package gui;

import server_client.CollabServer;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

/**
 * A GUI that the owner of the server can use to create tokens for the users.
 * The tokens are stored in the server and are used by clients to enter the server.
 */

class ServerGui extends JPanel {

	private static final long serialVersionUID = -1426186299786063098L;
	private final CollabServer collabServer;

    public static void main(String[] args) throws ClassNotFoundException, UnsupportedLookAndFeelException, InstantiationException, IllegalAccessException {
        UIManager.setLookAndFeel("com.jtattoo.plaf.hifi.HiFiLookAndFeel");

		CollabServer server = new CollabServer(4444);
	    ServerGui gui = new ServerGui(server);
        Thread thread = new Thread(() -> gui.collabServer.start());
        thread.start();
    }


	private ServerGui(CollabServer server) {
        collabServer = server;
        createGUI();
	}

	private void createGUI() {
		JFrame f = new JFrame("Collabypto Demo: Server GUI");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
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
		JLabel copied = new JLabel("Token copied to clipboard. Send token to invite!");

		button.addActionListener(e -> {
			String token1 = collabServer.generateToken();
			text.setText(token1);
			StringSelection stringSelection = new StringSelection(token1);
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			clipboard.setContents(stringSelection, null);
			mainPanel.add(copied);
			f.revalidate();
			f.repaint(); });
	}
}
