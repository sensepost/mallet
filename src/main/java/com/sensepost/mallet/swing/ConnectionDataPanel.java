package com.sensepost.mallet.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.sensepost.mallet.events.ChannelActiveEvent;
import com.sensepost.mallet.events.ChannelEvent;
import com.sensepost.mallet.events.ChannelEvent.Direction;
import com.sensepost.mallet.events.ChannelInactiveEvent;
import com.sensepost.mallet.events.ChannelReadEvent;
import com.sensepost.mallet.events.ChannelUserEvent;
import com.sensepost.mallet.swing.editors.EditorController;
import com.sensepost.mallet.swing.editors.ObjectEditor;
import com.sensepost.mallet.swing.editors.ReflectionEditor;

import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import javax.swing.ListSelectionModel;

public class ConnectionDataPanel extends JPanel {

	private final ListModel<ChannelEvent> EMPTY = new DefaultListModel<ChannelEvent>();
	private JList<ChannelEvent> eventList;
	private ConnectionData connectionData = null;
	private EditorController editorController = new EditorController();
	private ChannelEventRenderer channelEventRenderer = new ChannelEventRenderer();

	private ChannelReadEvent editing = null;
	
	public ConnectionDataPanel() {
		setLayout(new BorderLayout(0, 0));

		JScrollPane scrollPane = new JScrollPane();
		add(scrollPane, BorderLayout.CENTER);

		eventList = new JList<>();
		eventList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		eventList.setCellRenderer(channelEventRenderer);
		eventList.addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting())
					return;
				if (editing != null) {
					editing.setMessage(editorController.getObject());
				}
				ChannelEvent evt = eventList.getSelectedValue();
				if (evt == null || !(evt instanceof ChannelReadEvent)) {
					editing = null;
					editorController.setObject(null);
					editorController.setReadOnly(true);
				} else {
					editing = (ChannelReadEvent) evt;
					editorController.setObject(editing.getMessage());
					editorController.setReadOnly(false);
				}
			}
		});
		scrollPane.setViewportView(eventList);

		JPanel pendingPanel = new JPanel();
		add(pendingPanel, BorderLayout.SOUTH);
		pendingPanel.setLayout(new BorderLayout(0, 0));

		// ObjectEditor editor = new ByteBufEditor();
		ObjectEditor editor = new ReflectionEditor();
		editor.setEditorController(editorController);
		pendingPanel.add(editor.getComponent(), BorderLayout.CENTER);

		JPanel buttonPanel = new JPanel();
		pendingPanel.add(buttonPanel, BorderLayout.SOUTH);
		buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));

		JButton dropButton = new JButton("Drop");
		dropButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int n = eventList.getSelectedIndex();
				if (n >= 0) {
					try {
						connectionData.dropNextEvents(n);
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				} else {
					try {
						connectionData.dropNextEvent();
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			}
		});
		buttonPanel.add(dropButton);

		JButton sendButton = new JButton("Send");
		sendButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int n = eventList.getSelectedIndex();
				if (n >= 0) {
					if (editing != null) {
						editing.setMessage(editorController.getObject());
						editing = null;
						editorController.setObject(null);
					}
					try {
						connectionData.executeNextEvents(n);
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				} else {
					try {
						connectionData.executeNextEvent();
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			}
		});
		buttonPanel.add(sendButton);
	}

	public void setConnectionData(ConnectionData connectionData) {
		eventList.setModel(EMPTY);

		this.connectionData = connectionData;

		if (connectionData != null) {
			eventList.setModel(connectionData.getEvents());
		}
	}

	private class ChannelEventRenderer extends DefaultListCellRenderer {

		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
				boolean cellHasFocus) {
			if (value instanceof ChannelEvent) {
				ChannelEvent evt = (ChannelEvent) value;
				value = evt.getDirection() == Direction.Client_Server ? " -> " : " <- ";
				if (evt instanceof ChannelActiveEvent) {
					value += " Open";
				} else if (evt instanceof ChannelReadEvent) {
					Object o = ((ChannelReadEvent) evt).getMessage();
					value += " Read: ";
					if (o != null) {
						value += o.getClass().getName();
						if (o instanceof ByteBuf)
							value += " (" + ((ByteBuf) o).readableBytes() + " bytes)";
						else if (o instanceof byte[]) {
							value += " (" + ((byte[]) o).length + " bytes)";
						}
					}
				} else if (evt instanceof ChannelInactiveEvent) {
					value += " Close";
				} else if (evt instanceof ChannelUserEvent) {
					Object uevt = ((ChannelUserEvent) evt).getUserEvent();
					if (uevt != null) {
						if ((uevt instanceof ChannelInputShutdownEvent))
							value += " Input Shutdown";
						else
							value += " UserEvent " + uevt.toString();
					} else
						value += " UserEvent (null)";
				}
			}
			return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
		}

	}
}
