package controller;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

import gui.ErrorDialog;

/**
 * Performs an undo action when the undo button is pressed by the client.
 */
public class UnDoAction extends AbstractAction {
	private static final long serialVersionUID = -5124268535759633915L;

    // Maintains the ordered lists of edits
    private final UndoManager manager;

	public UnDoAction(UndoManager manager) {
		this.manager = manager;
	}

	@Override
	public void actionPerformed(ActionEvent evt) {
		try {
			this.manager.undo();
		} catch (CannotUndoException e) {
			new ErrorDialog("Nothing to undo");
		}
	}

}
