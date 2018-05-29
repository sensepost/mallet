package com.sensepost.mallet.swing.editors;

import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

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

	private Class<?> objectClass = null;
	
	private PropertyChangeListener listener = new PropertyChangeListener() {

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
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
			Object o;
			String text = textArea.getText();
			if (objectClass == String.class) {
				o = text;
			} else if (objectClass == TextWebSocketFrame.class) {
				o = new TextWebSocketFrame(text);
			} else 
				o = null;
			if (controller != null && o != null)
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
		objectClass = o == null ? null : o.getClass();

		String text;
		boolean editable = !controller.isReadOnly();
		if (o instanceof String) {
			text = (String) o;
		} else if (o instanceof TextWebSocketFrame) {
			text = ((TextWebSocketFrame)o).text();
		} else {
			text = "";
			editable = false;
		}

		textArea.setText(text);
		textArea.setCaretPosition(0);
		textArea.setEditable(editable);
		updating = false;
	}

	@Override
	public JComponent getEditorComponent() {
		return this;
	}

	@Override
	public Class<?>[] getSupportedClasses() {
		return new Class<?>[] { String.class, TextWebSocketFrame.class };
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
