package com.sensepost.mallet.swing.editors;

import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class ToStringEditor extends JPanel implements ObjectEditor {

	private JTextArea textArea;

	private EditorController controller = null;

	private PropertyChangeListener listener = new PropertyChangeListener() {

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			if (EditorController.OBJECT.equals(evt.getPropertyName())) {
				Object o = evt.getNewValue();
				updateString(o);
			}
		}

	};

	public ToStringEditor() {
		setLayout(new BorderLayout(0, 0));

		JScrollPane scrollPane = new JScrollPane();
		add(scrollPane);

		textArea = new JTextArea();
		textArea.setEditable(false);
		scrollPane.setViewportView(textArea);
	}

	private void updateString(Object o) {
		if (o != null) {
			textArea.setText(o.toString());
			textArea.setToolTipText(o.getClass().getName());
			textArea.setCaretPosition(0);
		} else {
			textArea.setText("");
			textArea.setToolTipText("");
		}
	}
	
	@Override
	public JComponent getEditorComponent() {
		return this;
	}

	@Override
	public Class<?>[] getSupportedClasses() {
		return new Class<?>[] { Object.class };
	}
	
	@Override
	public String getEditorName() {
		return "toString()";
	}

	@Override
	public void setEditorController(EditorController controller) {
		if (this.controller != null)
			controller.removePropertyChangeListener(listener);
		this.controller = controller;
		if (this.controller != null) {
			controller.addPropertyChangeListener(listener);
			updateString(controller.getObject());
		} else {
			updateString(null);
		}
	}

}
