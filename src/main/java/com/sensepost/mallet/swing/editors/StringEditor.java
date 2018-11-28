package com.sensepost.mallet.swing.editors;

import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.awt.BorderLayout;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class StringEditor extends JPanel {

	private JTextArea textArea;
	
	public StringEditor() {
		setLayout(new BorderLayout(0, 0));

		JScrollPane scrollPane = new JScrollPane();
		add(scrollPane);

		textArea = new JTextArea();
		scrollPane.setViewportView(textArea);
	}

	public static class Editor extends EditorSupport<StringEditor> {

		private Class<?> editingClass = null;

		public Editor() {
			super("String", new Class<?>[] { String.class, TextWebSocketFrame.class }, new StringEditor());
			getEditorComponent().textArea.getDocument().addDocumentListener(new DocumentListener() {

				@Override
				public void insertUpdate(DocumentEvent e) {
					update();
				}

				@Override
				public void removeUpdate(DocumentEvent e) {
					update();
				}

				@Override
				public void changedUpdate(DocumentEvent e) {
					update();
				}
				
				private void update() {
					if (isUpdating() || editingClass == null) {
						return;
					} else if (String.class.isAssignableFrom(editingClass)) {
						setUpdatedObject(getEditorComponent().textArea.getText());
					} else if (TextWebSocketFrame.class.isAssignableFrom(editingClass)) {
						setUpdatedObject(new TextWebSocketFrame(getEditorComponent().textArea.getText()));
					}
				}
			});
		}

		@Override
		public void setEditObject(Object o, boolean editable) {
			editingClass = o == null ? null : o.getClass();
			if (editingClass == null) {
				getEditorComponent().textArea.setText("");
			} else if (String.class.isAssignableFrom(editingClass)) {
				getEditorComponent().textArea.setText((String)o);
			} else if (TextWebSocketFrame.class.isAssignableFrom(editingClass)) {
				getEditorComponent().textArea.setText(((TextWebSocketFrame)o).text());
			} else {
				getEditorComponent().textArea.setText("");
			}
			getEditorComponent().textArea.setCaretPosition(0);
			getEditorComponent().textArea.setEditable(editingClass != null && editable);
		}
	}

	public static class ToStringEditor extends EditorSupport<StringEditor> {

		public ToStringEditor() {
			super("toString", new Class<?>[] { Object.class }, new StringEditor());
			getEditorComponent().textArea.setEditable(false);
		}

		@Override
		public void setEditObject(Object o, boolean editable) {
			if (o == null) {
				getEditorComponent().textArea.setText("");
			} else {
				getEditorComponent().textArea.setText(o.toString());
			}
			getEditorComponent().textArea.setCaretPosition(0);
		}
	}
}
