package gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.LayoutStyle;
import javax.swing.ListSelectionModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import javax.swing.undo.UndoManager;

import server_client.CollabModel;
import server_client.CollabInterface;
import controller.CaretListenerLabel;
import controller.RedoAction;
import controller.TextChangeListener;
import controller.UnDoAction;
import document.OperationEngineException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

/**
 * The main GUI of the client and server. This GUI will pop off after the user
 * enters his name, IP, port, and the document to edit.
 *
 * This GUI is thread-safe because the OT algorithm is run separately on each client.
 *
 */
public class ClientGui extends JPanel {

    private static final long serialVersionUID = -2826617458739559881L;

    // Initial test of the document
    private String init;
    // Client's label
    private final String label;

    // Holds the document area
    protected JTextPane textArea;
    protected JButton undoButton;
    protected JButton redoButton;

    // Copy button
    protected JButton copyButton;
    // Paste button
    protected JButton pasteButton;
    // Cut Paste
    protected JButton cutButton;
    // Holds list of all users currently editing the document
    protected JList userList;
    // List all the documents the user has access to
    protected JList documentList;

    // Model to store the current state of hte document
    private CollabModel collabModel;

    protected JPanel wholePane;
    protected DefaultListModel listModel;
    private DefaultListModel documentListModel;

    /**
     * Create GUI for the client
     */
    public ClientGui(String init, String label) {
        this.label = label;
        this.init = init;
        createGUI();
    }

    /**
     * Create GUI for the client
     */
    public ClientGui(String init, CollabInterface cc, String label) throws OperationEngineException {
        this.label = label;
        this.init = init;
        // create the GUI for this user
        createGUI();
        // create the model that take in the content of the textArea and a site id
        collabModel = new CollabModel(textArea, cc);
        // listen to change in the document
        textArea.getDocument().addDocumentListener(new TextChangeListener(collabModel));
    }

    /**
     * Created the initial GUI and add all of the necessary listeners
     * to the appropriate components inside the GUI
     */
    private void createGUI() {

        setLayout(new BorderLayout());
        FlowLayout toolBarLayout = new FlowLayout();
        toolBarLayout.setAlignment(FlowLayout.LEFT);
        JPanel toolBarPane = new JPanel();

        // Create all of the buttonsw

        // copy button
        ImageIcon copy = new ImageIcon("raw/copy.png");
        copyButton = new JButton(copy);
        copyButton.setToolTipText("Copy Text");
        copyButton.addActionListener(e -> textArea.copy());
        // paste button
        ImageIcon paste = new ImageIcon("raw/paste.png");
        pasteButton = new JButton(paste);
        pasteButton.setToolTipText("Paste Text");
        pasteButton.addActionListener(e -> textArea.paste());
        // cut button
        ImageIcon cut = new ImageIcon("raw/cut.png");
        cutButton = new JButton(cut);
        cutButton.setToolTipText("Cut Text");
        cutButton.addActionListener(e -> textArea.cut());
        // undo button
        ImageIcon undo = new ImageIcon("raw/undo.png");
        undoButton = new JButton(undo);
        // redo button
        ImageIcon redo = new ImageIcon("raw/redo.png");
        redoButton = new JButton(redo);

        JMenuBar toolBar = new JMenuBar();

        // add all button to toolBar
        toolBar.add(copyButton);
        toolBar.add(pasteButton);
        toolBar.add(cutButton);
        toolBar.add(undoButton);
        toolBar.add(redoButton);
        toolBarPane.setLayout(toolBarLayout);
        toolBarPane.add(toolBar);

        // Create a text area.
        textArea = createTextPane();
        CaretListenerLabel caretLabelListener = new CaretListenerLabel("Caret Status:");
        textArea.addCaretListener(caretLabelListener);

        // carry out doing
        UndoManager manager = new UndoManager();
        textArea.getDocument().addUndoableEditListener(manager);
        Action undoAction = new UnDoAction(manager);
        undoButton.addActionListener(undoAction);

        Action redoAction = new RedoAction(manager);
        redoButton.addActionListener(redoAction);
        this.registerKeyboardAction(undoAction,
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_MASK),
                JComponent.WHEN_FOCUSED);
        this.registerKeyboardAction(redoAction,
                KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_MASK),
                JComponent.WHEN_FOCUSED);

        JScrollPane areaScrollPane = new JScrollPane(textArea);
        areaScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        areaScrollPane.setPreferredSize(new Dimension(500, 500));
        areaScrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createTitledBorder(label),
                        BorderFactory.createEmptyBorder(5, 5, 5, 5)),
                areaScrollPane.getBorder()));

        userList = createUserLogin(new String[] { "No users right now." });
        JLabel userLabel = new JLabel("Who's Viewing");
        JScrollPane userLoginScrollPane = new JScrollPane(userList);
        userLoginScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        userLoginScrollPane.setPreferredSize(new Dimension(100, 145));
        userLoginScrollPane.setMinimumSize(new Dimension(10, 10));

        // Create a document display
        JPanel documentAreaPane = new JPanel();
        GroupLayout documentsListGroupLayout = new GroupLayout(documentAreaPane);

        documentList = createDocumentsLogin(new String[] { "No documents right now" });
        JLabel documentsLabel = new JLabel("List of Documents");
        JScrollPane documentsListScrollPane = new JScrollPane(documentList);
        userLoginScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        userLoginScrollPane.setPreferredSize(new Dimension(100, 145));
        userLoginScrollPane.setMinimumSize(new Dimension(10, 10));

        documentsListGroupLayout.setHorizontalGroup(documentsListGroupLayout
                .createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(userLabel)
                .addComponent(userLoginScrollPane)
                .addComponent(documentsLabel)
                .addComponent(documentsListScrollPane));

        documentsListGroupLayout.setVerticalGroup(documentsListGroupLayout
                .createSequentialGroup()
                .addComponent(userLabel)
                .addComponent(userLoginScrollPane)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(documentsLabel)
                .addComponent(documentsListScrollPane));
        documentAreaPane.setLayout(documentsListGroupLayout);

        JButton documentSelectButton = new JButton("Back to Document Selection");
        documentSelectButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                // TODO: add functionality
            }
        });

        JButton saveButton = new JButton("Download Text");
        saveButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                String text = textArea.getText();
                String documentName = label.split(": ")[1];
                try (PrintWriter out = new PrintWriter(documentName + ".txt")) {
                    out.println(text);
                } catch (FileNotFoundException e1) {
                    e1.printStackTrace();
                }
            }
        });

        JPanel rightPane = new JPanel(new BorderLayout());
        rightPane.add(documentAreaPane);
        rightPane.add(saveButton, BorderLayout.SOUTH);
        rightPane.add(documentSelectButton, BorderLayout.PAGE_START);
        rightPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(""),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        // Put everything together.
        JPanel leftPane = new JPanel(new BorderLayout());
        leftPane.add(toolBarPane, BorderLayout.PAGE_START);
        leftPane.add(areaScrollPane, BorderLayout.CENTER);
        leftPane.add(caretLabelListener, BorderLayout.SOUTH);

        wholePane = new JPanel();
        GroupLayout allGroup = new GroupLayout(wholePane);
        allGroup.setHorizontalGroup(allGroup.createSequentialGroup()
                .addComponent(leftPane).addComponent(rightPane));
        allGroup.setVerticalGroup(allGroup
                .createParallelGroup(Alignment.BASELINE).addComponent(leftPane)
                .addComponent(rightPane));
        wholePane.setLayout(allGroup);
        add(wholePane);
    }

    /**
     * Update the new list of user currently editting the document.
     *
     * @param names names of users
     *
     */
    public void updateUsers(Object[] names) {
        listModel.clear();
        if (names.length > 0) {
            for (Object i : names)
                listModel.addElement(i);
        }
        wholePane.revalidate();
        wholePane.repaint();
    }

    /**
     * Update the new list of documents being editted
     * @param documents String of documents
     */
    public void updateDocumentsList(Object[] documents) {
        documentListModel.clear();
        if (documents.length > 0) {
            for (Object i : documents)
                documentListModel.addElement(i);
        }
        wholePane.revalidate();
        wholePane.repaint();
    }

    /**
     * Returns a list of people of who are already logged in.
     * @return JList
     */
    private JList createUserLogin(String[] initialData) {
        listModel = new DefaultListModel();
        if (initialData.length > 0) {
            for (String i : initialData)
                listModel.addElement(i);
        }
        userList = new JList(listModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        return userList;
    }

    /**
     * Returns a list of documents currently editing.
     *
     * @return JList holds the list of the documents currentingly editing
     *         modifies: documentListModel and documentList. Both will be
     *         initialize with this initial strings
     */
    private JList createDocumentsLogin(String[] strings) {
        documentListModel = new DefaultListModel();
        if (strings.length > 0) {
            for (String i : strings)
                documentListModel.addElement(i);
        }
        documentList = new JList(documentListModel);
        documentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        return documentList;

    }

    /**
     * This creates the textArea where we can edit the document
     *
     * @return JTextPane, set to editable
     */
    private JTextPane createTextPane() {
        JTextPane textPane = new JTextPane();
        StyledDocument doc = textPane.getStyledDocument();
        addStylesToDocument(doc);

        try {
            doc.insertString(0, this.init, doc.getStyle("regular"));

        } catch (BadLocationException ble) {
            System.err.println("Couldn't insert initial text into text pane.");
        }

        return textPane;
    }

    private void addStylesToDocument(StyledDocument doc) {
        // Initialize some styles.
        Style def = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);

        Style regular = doc.addStyle("regular", def);
        StyleConstants.setFontFamily(def, "SansSerif");

        Style s = doc.addStyle("italic", regular);
        StyleConstants.setItalic(s, true);

        s = doc.addStyle("bold", regular);
        StyleConstants.setBold(s, true);

        s = doc.addStyle("small", regular);
        StyleConstants.setFontSize(s, 10);

        s = doc.addStyle("large", regular);
        StyleConstants.setFontSize(s, 16);

        s = doc.addStyle("icon", regular);
        StyleConstants.setAlignment(s, StyleConstants.ALIGN_CENTER);

        s = doc.addStyle("button", regular);
        StyleConstants.setAlignment(s, StyleConstants.ALIGN_CENTER);
    }

    /**
     * Get the model of this client
     * @return CollabModel
     */
    public CollabModel getCollabModel() {
        return this.collabModel;
    }


    /**
     * This will access the CollabModel and set the document key
     * @param str key
     */
    public void setModelKey(String str) {
        this.collabModel.setKey(str);
    }
}