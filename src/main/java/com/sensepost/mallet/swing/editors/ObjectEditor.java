package com.sensepost.mallet.swing.editors;

import javax.swing.JComponent;

public interface ObjectEditor {

	/**
	 * returns the user interface component of this editor;
	 * 
	 * @return
	 */
	JComponent getComponent();

	/**
	 * the description of this editor, when added to the user interface
	 * 
	 * @return
	 */
	String getName();

	/**
	 * the list of classes that this editor is capable of editing
	 * 
	 * It is probably best to list only concrete classes here, rather than
	 * interfaces, as the editor will be expected to replace the original object
	 * with one of the same type in the event of any changes. That is, unless
	 * the editor is read-only
	 * 
	 * @return
	 */
	Class<?>[] getSupportedClasses();

	/**
	 * Sets the editor controller for this editor. The controller supports
	 * PropertyChange events when the object being edited changes. Editors
	 * should add a propertychange listener to receive these events, and update
	 * itself accordingly.
	 * 
	 * Editors are encouraged to update their state lazily, i.e. only if the
	 * editor component is visible, in order to minimize any performance impact
	 * 
	 * @param controller
	 */
	void setEditorController(EditorController controller);
}
