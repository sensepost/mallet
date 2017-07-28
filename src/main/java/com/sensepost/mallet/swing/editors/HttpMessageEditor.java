package com.sensepost.mallet.swing.editors;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;

import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;

public class HttpMessageEditor extends JPanel implements ObjectEditor {

	private PropertyChangeListener listener = new PropertyChangeListener() {

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			if (EditorController.OBJECT.equals(evt.getPropertyName())) {
				Object o = evt.getNewValue();
				if (o instanceof HttpMessage) {
					updateMessage((HttpMessage) o);
				} else {
					updateMessage(null);
				}
			} else if (EditorController.READ_ONLY.equals(evt.getPropertyName())) {
				boolean editable = Boolean.FALSE.equals(evt.getNewValue());
				updateEditable(editable);
			}
		}

	};

	private EditorController controller = null;
	private JTextField methodField, urlField, versionField, statusField, messageField;
	private JPanel methodPanel, urlPanel, versionPanel, statusPanel, messagePanel;
	private HeaderTableModel headerModel = new HeaderTableModel();
	private ByteBufEditor contentEditor;
	private EditorController contentController = new EditorController();

	public HttpMessageEditor() {
		setLayout(new BorderLayout(0, 0));

		JSplitPane splitPane = new JSplitPane();
		splitPane.setResizeWeight(0.5);
		splitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
		add(splitPane);

		JPanel panel = new JPanel();
		splitPane.setLeftComponent(panel);
		panel.setLayout(new BorderLayout(0, 0));

		JPanel statusLinePanel = new JPanel();
		statusLinePanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		panel.add(statusLinePanel, BorderLayout.NORTH);

		methodField = new JTextField();
		methodField.setColumns(7);
		methodPanel = createPanel("Method", methodField);
		statusLinePanel.add(methodPanel);

		urlField = new JTextField();
		urlField.setColumns(10);
		urlPanel = createPanel("URL", urlField);
		statusLinePanel.add(urlPanel);

		versionField = new JTextField();
		versionField.setColumns(10);
		versionPanel = createPanel("Version", versionField);
		statusLinePanel.add(versionPanel);

		statusField = new JTextField();
		statusField.setColumns(3);
		statusPanel = createPanel("Status", statusField);
		statusLinePanel.add(statusPanel);

		messageField = new JTextField();
		messageField.setColumns(10);
		messagePanel = createPanel("Message", messageField);
		statusLinePanel.add(messagePanel);

		JScrollPane scrollPane = new JScrollPane();
		panel.add(scrollPane, BorderLayout.CENTER);

		JTable headerTable = new JTable(headerModel);
		scrollPane.setViewportView(headerTable);

		JPanel panel_1 = new JPanel();
		splitPane.setRightComponent(panel_1);
		panel_1.setLayout(new BorderLayout(0, 0));

		contentEditor = new ByteBufEditor();
		contentEditor.setEditorController(contentController);
		panel_1.add(contentEditor.getEditorComponent(), BorderLayout.CENTER);
	}

	private JPanel createPanel(String label, JTextField field) {
		JPanel p = new JPanel();
		p.setLayout(new GridLayout(0, 1, 0, 0));

		JLabel l = new JLabel(label);
		p.add(l);
		p.add(field);
		return p;
	}

	@Override
	public JComponent getEditorComponent() {
		return this;
	}

	@Override
	public Class<?>[] getSupportedClasses() {
		return new Class<?>[] { HttpRequest.class, HttpResponse.class, HttpMessage.class, HttpContent.class,
				LastHttpContent.class };
	}

	private void updateEditable(boolean editable) {
		methodField.setEditable(editable);
		urlField.setEditable(editable);
		versionField.setEditable(editable);
		statusField.setEditable(editable);
		messageField.setEditable(editable);
		headerModel.setEditable(editable);
		contentController.setReadOnly(!editable);
	}

	private void updateMessage(HttpMessage message) {
		if (message == null) {
			headerModel.setHeaders(null);
			contentController.setObject(null);
			this.setVisible(false);
			this.repaint();
		} else {
			methodPanel.setVisible(message instanceof HttpRequest);
			urlPanel.setVisible(message instanceof HttpRequest);
			if (message instanceof HttpRequest) {
				HttpRequest req = (HttpRequest) message;
				methodField.setText(req.method().name());
				urlField.setText(req.uri());
			}
			HttpVersion version = message.protocolVersion();
			versionField.setText(version.text());
			statusPanel.setVisible(message instanceof HttpResponse);
			messagePanel.setVisible(message instanceof HttpResponse);
			if (message instanceof HttpResponse) {
				HttpResponse resp = (HttpResponse) message;
				statusField.setText(resp.status().codeAsText().toString());
				messageField.setText(resp.status().reasonPhrase());
			}
			headerModel.setHeaders(message.headers());
			if (message instanceof HttpContent) {
				HttpContent hc = (HttpContent) message;
				contentController.setObject(hc.content());
			} else {
				contentController.setObject(null);
			}
		}

	}

	@Override
	public void setEditorController(EditorController controller) {
		if (this.controller != null)
			controller.removePropertyChangeListener(listener);
		this.controller = controller;
		if (this.controller != null) {
			controller.addPropertyChangeListener(listener);
			updateMessage((HttpMessage) controller.getObject());
			updateEditable(!controller.isReadOnly());
		} else {
			updateMessage(null);
			updateEditable(false);
		}
	}

	@Override
	public String getEditorName() {
		return "HTTP Message";
	}

	private static class HeaderTableModel extends AbstractTableModel {

		private static String[] names = new String[] { "Name", "Value" };

		private HttpHeaders headers = null;
		private List<Entry<String, String>> entries = null;
		private boolean editable = false;

		public void setHeaders(HttpHeaders headers) {
			this.headers = headers;
			if (headers != null)
				this.entries = headers.entries();
			else
				this.entries = Collections.<Entry<String, String>>emptyList();
			fireTableDataChanged();
		}

		public void setEditable(boolean editable) {
			this.editable = editable;
		}

		@Override
		public String getColumnName(int column) {
			return names[column];
		}

		@Override
		public Class<?> getColumnClass(int column) {
			return String.class;
		}

		@Override
		public int getColumnCount() {
			return 2;
		}

		@Override
		public int getRowCount() {
			int ret = headers == null ? 0 : entries.size();
			return ret;
		}

		@Override
		public Object getValueAt(int row, int column) {
			Entry<String, String> e = entries.get(row);
			switch (column) {
			case 0:
				return e.getKey();
			case 1:
				return e.getValue();
			}
			return null;
		}

		@Override
		public boolean isCellEditable(int row, int column) {
			return editable;
		}

	}
}
