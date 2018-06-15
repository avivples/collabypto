package gui;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;

/**
 * This class will generate a pop up indicate the type of error that occur
 * during the connection.
 */
public class ErrorDialog extends JDialog {

    private static final long serialVersionUID = -7549769136652082371L;

    @SuppressWarnings("serial")
    public ErrorDialog(String message) {
        this.setLayout(new GridLayout(0, 1));
        this.add(new JLabel(message, JLabel.CENTER));
        this.add(new JButton(new AbstractAction("Close") {

            @Override
            public void actionPerformed(ActionEvent e) {
                ErrorDialog.this.setVisible(false);
                ErrorDialog.this.dispatchEvent(new WindowEvent(
                        ErrorDialog.this, WindowEvent.WINDOW_CLOSING));
            }
        }));
        this.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
                System.out.println(e.paramString());
            }
        });

        this.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        this.pack();
        this.setLocationRelativeTo(null);
        this.setVisible(true);
    }

}
