package com.sensepost.mallet.swing.editors;

import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class StringEditor extends JPanel implements ObjectEditor {

	private JTextArea textArea;

	private EditorController controller = null;

	private boolean updating = false;

	private PropertyChangeListener listener = new PropertyChangeListener() {

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			System.out.println(evt);
			if (evt.getSource().equals(controller) && (EditorController.OBJECT.equals(evt.getPropertyName())
					|| EditorController.READ_ONLY.equals(evt.getPropertyName()))) {
				updateEditor();
			}
		}

	};

	private DocumentListener docListener = new DocumentListener() {

		@Override
		public void removeUpdate(DocumentEvent e) {
			updateController();
		}

		@Override
		public void insertUpdate(DocumentEvent e) {
			updateController();
		}

		@Override
		public void changedUpdate(DocumentEvent e) {
			updateController();
		}

		private void updateController() {
			if (updating)
				return;
			updating = true;
			String o = textArea.getText();
			if (controller != null)
				controller.setObject(o);
			updating = false;
		}
	};

	public StringEditor() {
		setLayout(new BorderLayout(0, 0));

		JScrollPane scrollPane = new JScrollPane();
		add(scrollPane);

		textArea = new JTextArea();
		textArea.getDocument().addDocumentListener(docListener);
		scrollPane.setViewportView(textArea);
	}

	private void updateEditor() {
		if (updating)
			return;
		updating = true;
		Object o = controller != null ? controller.getObject() : null;
		if (o instanceof String) {
			textArea.setText((String) o);
			textArea.setCaretPosition(0);
			textArea.setEditable(!controller.isReadOnly());
		} else {
			textArea.setText("");
			textArea.setToolTipText("");
			textArea.setEditable(false);
		}
		updating = false;
	}

	@Override
	public JComponent getEditorComponent() {
		return this;
	}

	@Override
	public Class<?>[] getSupportedClasses() {
		return new Class<?>[] { String.class };
	}

	@Override
	public String getEditorName() {
		return "String";
	}

	@Override
	public void setEditorController(EditorController controller) {
		if (this.controller != null)
			controller.removePropertyChangeListener(listener);
		this.controller = controller;
		if (this.controller != null) {
			controller.addPropertyChangeListener(listener);
		}
		updateEditor();
	}

}
