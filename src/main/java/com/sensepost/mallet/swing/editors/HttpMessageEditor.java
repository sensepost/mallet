package com.sensepost.mallet.swing.editors;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;

public class HttpMessageEditor extends JPanel {

	private JTextField methodField, urlField, versionField, statusField,
			messageField;

	private JPanel methodPanel, urlPanel, versionPanel, statusPanel,
			messagePanel;
	private HeaderTableModel headerModel = new HeaderTableModel();
	private ByteArrayEditor.Editor contentEditor;
	private EditorController contentController = new EditorController();

	private HttpMessage message = null;

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

		contentEditor = new ByteArrayEditor.Editor();
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

	private void updateEditable(boolean editable) {
		methodField.setEditable(editable);
		urlField.setEditable(editable);
		versionField.setEditable(editable);
		statusField.setEditable(editable);
		messageField.setEditable(editable);
		headerModel.setEditable(editable);
		contentController.setReadOnly(!editable);
	}

	private void addTextUpdateListener(DocumentListener listener) {
		methodField.getDocument().addDocumentListener(listener);
		urlField.getDocument().addDocumentListener(listener);
		versionField.getDocument().addDocumentListener(listener);
		statusField.getDocument().addDocumentListener(listener);
		messageField.getDocument().addDocumentListener(listener);
	}
	
	private void updateMessage(HttpMessage message) {
		this.message = message;
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
				HttpMethod method = req.method();
				methodField.setText(method.name());
				urlField.setText(req.uri());
			}
			HttpVersion version = message.protocolVersion();
			versionField.setText(version.text());
			statusPanel.setVisible(message instanceof HttpResponse);
			messagePanel.setVisible(message instanceof HttpResponse);
			if (message instanceof HttpResponse) {
				HttpResponse resp = (HttpResponse) message;
				HttpResponseStatus status = resp.status();
				statusField.setText(status.codeAsText().toString());
				messageField.setText(status.reasonPhrase());
			}
			headerModel.setHeaders(message.headers());
			if (message instanceof FullHttpMessage) {
				FullHttpMessage fhm = (FullHttpMessage) message;
				contentController.setObject(fhm.content());
			} else {
				contentController.setObject(null);
			}
		}

	}

	private void duplicateMessage() {
		if (message instanceof FullHttpMessage) {
			message = ((FullHttpMessage) message).duplicate();
		} else if (message instanceof HttpRequest) {
			HttpRequest orig = (HttpRequest) message;
			HttpRequest req = new DefaultHttpRequest(orig.protocolVersion(),
					orig.method(), orig.uri(), orig.headers().copy());
			req.setDecoderResult(orig.decoderResult());
			message = req;
		} else if (message instanceof HttpResponse) {
			HttpResponse orig = (HttpResponse) message;
			HttpResponse response = new DefaultHttpResponse(
					orig.protocolVersion(), orig.status(), orig.headers().copy());
			response.setDecoderResult(orig.decoderResult());
			message = response;
		}
	}

	private HttpMessage getMessage() {
		duplicateMessage();

		if (message instanceof HttpRequest) {
			HttpRequest req = (HttpRequest) message;
			if (!methodField.getText().equals(req.method().name())) {
				try {
					req.setMethod(HttpMethod.valueOf(methodField.getText()));
				} catch (IllegalArgumentException iae) {
					req.setDecoderResult(DecoderResult.failure(iae));
				}
			}
			req.setUri(urlField.getText());
		}
		if (!versionField.getText().equals(message.protocolVersion().text())) {
			try {
				message.setProtocolVersion(HttpVersion.valueOf(versionField
						.getText()));
			} catch (IllegalArgumentException e) {
				message.setDecoderResult(DecoderResult.failure(e));
			}
		}
		if (message instanceof HttpResponse) {
			HttpResponse response = (HttpResponse) message;
			if (!statusField.getText().equals(response.status().codeAsText().toString())
					|| !messageField.getText().equals(
							response.status().reasonPhrase())) {
				try {
					response.setStatus(HttpResponseStatus.valueOf(
							Integer.parseInt(statusField.getText()),
							messageField.getText()));
				} catch (Exception e) {
					response.setDecoderResult(DecoderResult.failure(e));
				}
			}
		}

		message.headers().set(headerModel.getHeaders());

		if (message instanceof FullHttpMessage) {
			FullHttpMessage fhm = (FullHttpMessage) message;
			message = fhm.replace((ByteBuf) contentController.getObject());
		}
		return message;
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
				this.entries = Collections.<Entry<String, String>> emptyList();
			fireTableDataChanged();
		}

		public HttpHeaders getHeaders() {
			return headers;
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
			return false && editable; // FIXME: Figure out how to make the header table editable
		}

		@Override
		public void setValueAt(Object value, int row, int column) {
		}
	}

	public static class Editor extends EditorSupport<HttpMessageEditor> {
		private Class<?> editingClass = null;
		private HttpMessageEditor editor;

		private DocumentListener textListener = new DocumentListener() {
			public void changedUpdate(DocumentEvent e) {
				update(e);
			}

			public void removeUpdate(DocumentEvent e) {
				update(e);
			}

			public void insertUpdate(DocumentEvent e) {
				update(e);
			}

			public void update(DocumentEvent e) {
				if (isUpdating())
					return;
				setUpdatedObject(editor.getMessage());
			}
		};

		private TableModelListener headerListener = new TableModelListener() {
			
			@Override
			public void tableChanged(TableModelEvent e) {
				if (isUpdating())
					return;
				setUpdatedObject(editor.getMessage());
			}
		};
		
		public Editor() {
			super("Http Message", new Class<?>[] { HttpMessage.class },
					new HttpMessageEditor());
			editor = getEditorComponent();
			editor.addTextUpdateListener(textListener);
			editor.headerModel.addTableModelListener(headerListener);
		}

		@Override
		public void setEditObject(Object o, boolean editable) {
			editingClass = o == null ? null : o.getClass();
			if (editingClass == null) {
				editor.updateMessage(null);
			} else if (HttpMessage.class.isAssignableFrom(editingClass)) {
				editor.updateMessage((HttpMessage) o);
			} else {
				editor.updateMessage(null);
			}
			editor.updateEditable(editingClass != null && editable);
		}
	}
}
