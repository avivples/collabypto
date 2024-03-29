package gui;

import server_client.CollabClient;

import javax.swing.*;
import java.awt.*;


class LoginPage extends JFrame{

    // Default local IP address
    private static final String LOCAL_IP = "127.0.0.1";
    // Default server port to connect
    private static final int DEFAULT_PORT = 4444;
    // Port number of server
    private int portNo;

    // Prompt to the user
    private static final String PROMPT = "Please enter user name, IP address"
                                         + " and port or Default for local connection";
    // Prompt for IP
    private static final String IP = "Server IP";
    // Prompt for Port
    private static final String PORT = "Server Port";


    private static final String USER_NAME = "User Name";
    private static final String DEFAULT_BUTTON = "Default";
    private static final String SUBMIT_BUTTON = "Submit";

    // User's IP input
    private JTextField ipInput;
    // User's username input
    private JTextField userNameInput;
    // User's port input
    private JTextField portInput;

    // Instance of client
    private CollabClient collabClient;

    // Notify user when they enter wrong port number
    private static final String WRONG_PORT_NUMBER = "Please enter correct port number (xx.xxx.xx.xx)";
    // Notify user when they enter wrong IP or port number
    private static final String WRONG_IP_OR_PORT = "Please correct your IP and Port number";


    private LoginPage() { initGui(); }

    private void initGui() {

        final JFrame frame = new JFrame("Provide information");

        JPanel pane = new JPanel(new BorderLayout());

        JLabel prompt = new JLabel(PROMPT);
        JLabel userName = new JLabel(USER_NAME);
        userNameInput = new JTextField();
        userNameInput.setToolTipText("Enter your user name");

        JLabel ip = new JLabel(IP);
        ipInput = new JTextField();

        ipInput.setToolTipText("Enter the IP address");

        JLabel port = new JLabel(PORT);
        portInput = new JTextField();
        portInput.setToolTipText("Enter the port number");
        JButton defaultVal = new JButton(DEFAULT_BUTTON);
        JButton submit = new JButton(SUBMIT_BUTTON);

        //button actions checking the information is valid and starting a collabclient if it is.

        defaultVal.addActionListener(arg0 -> {
            String userNameInputText = userNameInput.getText(); //get the Text from the user
            if (userNameInputText.length() <= 0) {
                userNameInputText = JOptionPane.showInputDialog(frame, "Enter username:");
            } else {
                userNameInputText = userNameInput.getText().trim();
            }
            if (userNameInputText == null || !userNameInputText.matches("[A-Za-z0-9]+") && userNameInputText.length() < 1) {
                frame.dispose();
                new ErrorDialog("Please enter an alphanumeric username");
                SwingUtilities.invokeLater(() -> LoginPage.this.initGui());
                return;
            }

            frame.dispose();
            collabClient = new CollabClient(LOCAL_IP, DEFAULT_PORT, userNameInputText);
            Thread thread = new Thread(() -> collabClient.start());
            thread.start();
        });

        // connect to remote server
        submit.addActionListener(e -> {
            // get the Text from the user
            String userName1 = userNameInput.getText().trim();
            if (userName1 == null || !userName1.matches("[A-Za-z0-9]+") && userName1.length() < 1) {
                frame.dispose();
                new ErrorDialog("Please enter an alphanumeric username:");
                SwingUtilities.invokeLater(LoginPage.this::initGui);
                return;
            }

            String ip1 = ipInput.getText().trim();
            String portInputString = portInput.getText().trim();
            try {
                if (ip1.length() > 0 && portInputString.length() > 0) {
                    try {
                        // take the port number input
                        portNo = Integer.parseInt(portInputString);

                    } catch (NumberFormatException exception) {
                        frame.dispose();
                        new ErrorDialog(WRONG_PORT_NUMBER);
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                LoginPage.this.initGui();
                            }
                        });

                    }
                    frame.dispose();
                    collabClient = new CollabClient(ip1, portNo, userName1);
                    Thread thread = new Thread(() -> collabClient.start());
                    thread.start();
                }
                else {
                    frame.dispose();
                    new ErrorDialog(WRONG_IP_OR_PORT);
                    SwingUtilities.invokeLater(() -> LoginPage.this.initGui());

                }
            } catch (IllegalArgumentException exception) {
                frame.dispose();
                new ErrorDialog(WRONG_IP_OR_PORT);
                SwingUtilities.invokeLater(() -> LoginPage.this.initGui());
            }
        });

        pane.add(prompt, BorderLayout.PAGE_START);
        JPanel ipPortPane = new JPanel();
        GroupLayout ipPortLayout = new GroupLayout(ipPortPane);
        ipPortPane.setLayout(ipPortLayout);
        ipPortLayout.setAutoCreateGaps(true);

        ipPortLayout.setAutoCreateContainerGaps(true);

        // Create a sequential group for the horizontal axis.
        GroupLayout.SequentialGroup hGroup = ipPortLayout
                .createSequentialGroup();
        hGroup.addGroup(ipPortLayout.createParallelGroup()
                .addComponent(userName).addComponent(ip).addComponent(port));
        hGroup.addGroup(ipPortLayout.createParallelGroup()
                .addComponent(userNameInput).addComponent(ipInput)
                .addComponent(portInput));
        ipPortLayout.setHorizontalGroup(hGroup);

        // Create a sequential group for the vertical axis.
        GroupLayout.SequentialGroup vGroup = ipPortLayout
                .createSequentialGroup();
        vGroup.addGroup(ipPortLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(userName).addComponent(userNameInput));
        vGroup.addGroup(ipPortLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(ip).addComponent(ipInput));
        vGroup.addGroup(ipPortLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(port).addComponent(portInput));
        ipPortLayout.setVerticalGroup(vGroup);
        ipPortPane.setPreferredSize(new Dimension(200, 100));
        pane.add(ipPortPane, BorderLayout.CENTER);

        JPanel clickPane = new JPanel();
        clickPane.add(defaultVal);
        clickPane.add(submit);
        pane.add(clickPane, BorderLayout.PAGE_END);
        frame.setContentPane(pane);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setResizable(false);
        frame.pack();
        frame.setVisible(true);
    }

    public static void main(String[] args) throws ClassNotFoundException, UnsupportedLookAndFeelException, InstantiationException, IllegalAccessException {
        UIManager.setLookAndFeel("com.jtattoo.plaf.hifi.HiFiLookAndFeel");
        SwingUtilities.invokeLater(() -> new LoginPage());
    }
}
