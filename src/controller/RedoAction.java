package controller;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

import gui.ErrorDialog;

/**
 * This class will make the redo action when the user performs the click on the
 * redo button 
 * Thread-safe argument: This is thread-safe as indicated in the
 * Javadoc of UndoManager
 * 
 * @author viettran
 * 
 */
public class RedoAction extends AbstractAction {
	private static final long serialVersionUID = 8940521437273260980L;
	/**
	 * This maintains an ordered list of edits and the index of the next edit in
	 * that list.
	 */
	private final UndoManager manager;

	/**
	 * This constructor take in an UndoManager to perform undo and redo action
	 * 
	 * @param manager
	 */
	public RedoAction(UndoManager manager) {
		this.manager = manager;
	}

	@Override
	public void actionPerformed(ActionEvent evt) {
		try {
			manager.redo();
		} catch (CannotRedoException e) {
			new ErrorDialog("Nothing to redo");
			Toolkit.getDefaultToolkit().beep();
		}

	}
}
