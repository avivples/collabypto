package controller;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;

/**
 * Here, we listen to the caret changes in the documents and show the current position
 * of the caret at the bottom of the screen.
 */
@SuppressWarnings("serial")
public class CaretListenerLabel extends JLabel implements CaretListener {

	public CaretListenerLabel(String label) {
		super(label);
	}

    /**
     * Set the caret position for the document
     * @param e
     */
	@Override
	public void caretUpdate(CaretEvent e) {
        SwingUtilities.invokeLater(() -> {
            if (e.getDot() == e.getMark()) { // no selection
                setText("Text position: " + e.getDot() + "\n");
            }
            else if (e.getDot() < e.getMark()) {
                setText("Selection from: " + e.getDot() + " to " + e.getMark() + "\n");
            }
            else {
                setText("Selection from: " + e.getMark() + " to " + e.getDot() + "\n");
            }
        });
	}
}
