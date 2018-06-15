package controller;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;

import server_client.CollabModel;
import document.OperationEngineException;

/**
 * This is the listener for changes in the main document,
 * Using the collabModel, we make the necessary updates for the document.
 * 
 */
public class TextChangeListener implements DocumentListener {

    private final CollabModel model;

	public TextChangeListener(CollabModel model) {
		this.model = model;
    }

	/**
	 * Executes the insert operation at the correct location and send the update
     * to the other clients.
	 * 
	 * @param e insert event
	 */
	@Override
	public void insertUpdate(DocumentEvent e) {		
		int offset = e.getOffset();
		String change = null;
		try {
			change = e.getDocument().getText(offset, e.getLength());
		} catch (BadLocationException ex) {
			ex.printStackTrace();
		}

		if (!this.model.remote) {
			try {
				this.model.addString(offset, change);
			} catch (OperationEngineException ex) {
				ex.printStackTrace();
			}
		} else {
			this.model.remote = false;
		}

	}

	/**
     * Executes the delete operation at the correct location and send the update
     * to the other clients.
     *
	 * @param e delete event
	 * */
	@Override
	public void removeUpdate(DocumentEvent e) {
		int offset = e.getOffset();
		int change = e.getLength();
		if (!this.model.remote) {
			try {
				this.model.deleteString(offset, change);
			} catch (OperationEngineException ex) {
				ex.printStackTrace();
			}
		} else {
			this.model.remote = false;
		}
	}

    @Override
    public void changedUpdate(DocumentEvent e) {
        // Nothing to do here for us
    }

}
