package com.sensepost.mallet.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import com.sensepost.mallet.events.ChannelActiveEvent;
import com.sensepost.mallet.events.ChannelEvent;
import com.sensepost.mallet.events.ChannelInactiveEvent;
import com.sensepost.mallet.events.ChannelReadEvent;
import com.sensepost.mallet.events.ChannelUserEvent;
import com.sensepost.mallet.swing.editors.ByteBufEditor;
import com.sensepost.mallet.swing.editors.EditorController;
import com.sensepost.mallet.swing.editors.ObjectEditor;

import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.ChannelInputShutdownEvent;

public class ConnectionDataPanel extends JPanel {

	private final ListModel<ChannelEvent> EMPTY = new DefaultListModel<ChannelEvent>();

	private JList<ChannelEvent> pendingList;
	private JList<ChannelEvent> completedList;
	private ConnectionData connectionData = null;
	private EditorController editorController = new EditorController();
	private ListDataListener pendingListener = new PendingListener();
	private ChannelEventRenderer channelEventRenderer = new ChannelEventRenderer();

	public ConnectionDataPanel() {
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[] { 450, 0 };
		gridBagLayout.rowHeights = new int[] { 132, 36, 132, 0 };
		gridBagLayout.columnWeights = new double[] { 0.0, Double.MIN_VALUE };
		gridBagLayout.rowWeights = new double[] { 0.0, 1.0, 0.0, Double.MIN_VALUE };
		setLayout(gridBagLayout);

		JScrollPane scrollPane = new JScrollPane();
		GridBagConstraints gbc_scrollPane = new GridBagConstraints();
		gbc_scrollPane.weightx = 1.0;
		gbc_scrollPane.weighty = 0.5;
		gbc_scrollPane.fill = GridBagConstraints.BOTH;
		gbc_scrollPane.insets = new Insets(0, 0, 5, 0);
		gbc_scrollPane.gridx = 0;
		gbc_scrollPane.gridy = 0;
		add(scrollPane, gbc_scrollPane);

		completedList = new JList<>();
		completedList.setCellRenderer(channelEventRenderer);
		scrollPane.setViewportView(completedList);

		JPanel pendingPanel = new JPanel();
		GridBagConstraints gbc_pendingPanel = new GridBagConstraints();
		gbc_pendingPanel.weighty = 0.25;
		gbc_pendingPanel.fill = GridBagConstraints.BOTH;
		gbc_pendingPanel.insets = new Insets(0, 0, 5, 0);
		gbc_pendingPanel.gridx = 0;
		gbc_pendingPanel.gridy = 1;
		add(pendingPanel, gbc_pendingPanel);
		pendingPanel.setLayout(new BorderLayout(0, 0));

		 ObjectEditor editor = new ByteBufEditor();
		//		ObjectEditor editor = new ReflectionEditor();
		editor.setEditorController(editorController);
		pendingPanel.add(editor.getComponent(), BorderLayout.CENTER);

		JPanel buttonPanel = new JPanel();
		pendingPanel.add(buttonPanel, BorderLayout.SOUTH);
		buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));

		JButton dropButton = new JButton("Drop");
		dropButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (pendingList.getModel().getSize() > 0) {
					connectionData.dropNextEvent();
				}
			}
		});
		buttonPanel.add(dropButton);

		JButton sendButton = new JButton("Send");
		sendButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (pendingList.getModel().getSize() > 0) {
					ChannelEvent evt = pendingList.getModel().getElementAt(0);
					if ((evt instanceof ChannelReadEvent)) {
						((ChannelReadEvent) evt).setMessage(editorController.getObject());
					}
					connectionData.executeNextEvent();
				}
			}
		});
		buttonPanel.add(sendButton);

		JScrollPane scrollPane_1 = new JScrollPane();
		GridBagConstraints gbc_scrollPane_1 = new GridBagConstraints();
		gbc_scrollPane_1.weightx = 1.0;
		gbc_scrollPane_1.weighty = 0.5;
		gbc_scrollPane_1.fill = GridBagConstraints.BOTH;
		gbc_scrollPane_1.gridx = 0;
		gbc_scrollPane_1.gridy = 2;
		add(scrollPane_1, gbc_scrollPane_1);

		pendingList = new JList<>();
		pendingList.setCellRenderer(channelEventRenderer);
		pendingList.addPropertyChangeListener(new PropertyChangeListener() {
			@SuppressWarnings("rawtypes")
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				if ("model".equals(evt.getPropertyName())) {
					ListModel lm = (ListModel) evt.getOldValue();
					if (lm != null)
						lm.removeListDataListener(pendingListener);
					lm = (ListModel) evt.getNewValue();
					if (lm != null) {
						lm.addListDataListener(pendingListener);
						pendingListener.contentsChanged(null);
					}
				}
			}

		});
		scrollPane_1.setViewportView(pendingList);
	}

	public void setConnectionData(ConnectionData connectionData) {
		pendingList.setModel(EMPTY);
		completedList.setModel(EMPTY);

		this.connectionData = connectionData;

		if (connectionData != null) {
			pendingList.setModel(connectionData.getPendingEvents());
			completedList.setModel(connectionData.getCompletedEvents());
		}
	}

	private class PendingListener implements ListDataListener {

		@Override
		public void intervalAdded(ListDataEvent e) {
			if (e.getIndex0() == 0) {
				ChannelEvent evt = pendingList.getModel().getElementAt(0);
				if (evt instanceof ChannelReadEvent) {
					editorController.setObject(((ChannelReadEvent) evt).getMessage());
				} else
					editorController.setObject(null);
			}
		}

		@Override
		public void intervalRemoved(ListDataEvent e) {
			if (e.getIndex0() == 0) {
				if (pendingList.getModel().getSize() == 0) {
					editorController.setObject(null);
				} else {
					ChannelEvent evt = pendingList.getModel().getElementAt(0);
					if (evt instanceof ChannelReadEvent) {
						editorController.setObject(((ChannelReadEvent) evt).getMessage());
					} else
						editorController.setObject(null);
				}
			}
		}

		@Override
		public void contentsChanged(ListDataEvent e) {
			if (pendingList.getModel().getSize() == 0) {
				editorController.setObject(null);
			} else {
				ChannelEvent evt = pendingList.getModel().getElementAt(0);
				if (evt instanceof ChannelReadEvent) {
					editorController.setObject(((ChannelReadEvent) evt).getMessage());
				} else
					editorController.setObject(null);
			}
		}

	}

	private class ChannelEventRenderer extends DefaultListCellRenderer {

		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
				boolean cellHasFocus) {
			if (value instanceof ChannelEvent) {
				ChannelEvent evt = (ChannelEvent) value;
				value = evt.getSourceAddress() + " -> " + evt.getDestinationAddress();
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
