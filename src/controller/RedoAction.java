package controller;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.UndoManager;

import gui.ErrorDialog;

/**
 * Performs a redo action when the redo button is pressed by the client.
 */
public class RedoAction extends AbstractAction {

    private static final long serialVersionUID = 8940521437273260980L;
	// Maintains the ordered lists of edits
	private final UndoManager manager;

	public RedoAction(UndoManager manager) {
		this.manager = manager;
	}

	@Override
	public void actionPerformed(ActionEvent evt) {
		try {
			this.manager.redo();
		} catch (CannotRedoException e) {
			new ErrorDialog("Nothing To Redo.");
		}

	}
}
