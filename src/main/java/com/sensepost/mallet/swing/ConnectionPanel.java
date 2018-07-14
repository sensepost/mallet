package com.sensepost.mallet.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.net.SocketAddress;
import java.sql.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

import com.sensepost.mallet.InterceptController;
import com.sensepost.mallet.persistence.MessageDAO;

public class ConnectionPanel extends JPanel implements InterceptController {

	private ConnectionDataPanel cdp;

	private JTable table;
	private DefaultListModel<ConnectionData> listModel = new DefaultListModel<>();

	private Map<ConnectionData, AddrPair> connAddrMap = new HashMap<>();
	private Map<String, ConnectionData> channelEventMap = new LinkedHashMap<>();

	private boolean intercept = false;
	private MessageDAO dao = null;
	
	public ConnectionPanel() {
		setLayout(new BorderLayout(0, 0));
		JSplitPane splitPane = new JSplitPane();
		splitPane.setResizeWeight(0.25);
		add(splitPane);

		JScrollPane scrollPane = new JScrollPane();
		splitPane.setLeftComponent(scrollPane);

		table = new JTable(new ListTableModelAdapter(listModel)) {
			public Component prepareRenderer(TableCellRenderer renderer,
					int row, int column) {
				Component c = super.prepareRenderer(renderer, row, column);

				ConnectionData cd = listModel.getElementAt(row);
				if (table.getSelectedRow() == row) {
					c.setBackground(getSelectionBackground());
					c.setForeground(getSelectionForeground());
				} else if (cd.isException()) {
					c.setBackground(Color.PINK);
					c.setForeground(getForeground());
				} else if (cd.isClosed()) {
					c.setBackground(Color.LIGHT_GRAY);
					c.setForeground(getForeground());
				} else {
					c.setBackground(getBackground());
					c.setForeground(getForeground());
				}
				return c;
			}
		};
		table.setRowSelectionAllowed(true);
		table.setColumnSelectionAllowed(false);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting())
					return;
				ConnectionData cd = null;
				int selected = table.getSelectedRow();
				if (selected != -1) {
					selected = table.convertRowIndexToModel(selected);
					synchronized (channelEventMap) {
						cd = listModel.getElementAt(selected);
					}
				}
				cdp.setConnectionData(cd);
			}
		});
		table.setDefaultRenderer(Date.class, new DateRenderer(false));
		table.setAutoCreateRowSorter(true);
		scrollPane.setViewportView(table);

		cdp = new ConnectionDataPanel();
		splitPane.setRightComponent(cdp);


	}
	
	public boolean isIntercept() {
		return intercept;
	}

	public void setIntercept(boolean intercept) {
		this.intercept = intercept;
		if (!intercept) {
			sendAllPendingEvents();
		}
	}

	public void setMessageDAO(MessageDAO dao) {
		this.dao = dao;
	}
	
	@Override
	public void linkChannels(final String channel1, final String channel2) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				synchronized(channelEventMap) {
					ConnectionData event1 = channelEventMap.get(channel1);
					ConnectionData event2 = channelEventMap.get(channel2);
					if (event1 == event2 && event1 != null) {
						return; // already linked?
					} else if (event1 != null && event2 != null) {
						// whoa!
						throw new RuntimeException("Trying to link two channels with existing events - not yet supported!");
					} else if (event1 != null) {
						channelEventMap.put(channel2, event1);
					} else if (event2 != null) {
						channelEventMap.put(channel1, event2);
					} else {
						ConnectionData eventList = new ConnectionData();
						channelEventMap.put(channel1, eventList);
						channelEventMap.put(channel2, eventList);
						listModel.addElement(eventList);
					}
				}
			}
		});
	}

	@Override
	public void addChannelEvent(final ChannelEvent evt) throws Exception {
		if (evt instanceof ChannelReadEvent && dao != null) {
			((ChannelReadEvent) evt).setDao(dao);
		}
		
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				try {
					addChannelEventEDT(evt);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	private void addChannelEventEDT(ChannelEvent evt) throws Exception {
		String cp = evt.getConnectionIdentifier();
		ConnectionData connectionData;
		synchronized(channelEventMap) {
			connectionData = channelEventMap.get(cp);
			if (connectionData == null) {
				connectionData = new ConnectionData();
				channelEventMap.put(cp, connectionData);
				listModel.addElement(connectionData);
			}
			connectionData.addChannelEvent(evt);
		}

		if (evt instanceof ChannelActiveEvent) {
			ChannelActiveEvent cae = (ChannelActiveEvent) evt;
			SocketAddress remote = cae.getRemoteAddress();
			SocketAddress local = cae.getLocalAddress();
			synchronized (connAddrMap) {
				if (connAddrMap.get(connectionData) == null) {
					AddrPair ap = new AddrPair(remote, local);
					connAddrMap.put(connectionData, ap);
				} else {
					connAddrMap.get(connectionData).dst = remote;
				}
			}	
		}

		// force redraw of the element in the list
		int index = listModel.indexOf(connectionData);
		listModel.setElementAt(listModel.getElementAt(index), index);

		if (!intercept) {
			try {
				connectionData.executeAllEvents();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void sendAllPendingEvents() {
		synchronized (channelEventMap) {
			for (String conn : channelEventMap.keySet()) {
				try {
					channelEventMap.get(conn).executeAllEvents();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	protected static class AddrPair {
		private SocketAddress src, dst;

		public AddrPair(SocketAddress src, SocketAddress dst) {
			if (src == null)
				throw new NullPointerException("src");
			if (dst == null)
				throw new NullPointerException("dst");
			this.src = src;
			this.dst = dst;
		}

		public int hashCode() {
			return src.hashCode() ^ dst.hashCode();
		}

		public boolean equals(Object obj) {
			if (!(obj instanceof AddrPair))
				return false;
			AddrPair that = (AddrPair) obj;
			return (this.src == that.src && this.dst == that.dst) || (this.src == that.dst && this.dst == that.src);
		}
	}

	private class ChannelPairCellRenderer implements ListCellRenderer<Integer> {
		protected DefaultListCellRenderer defaultRenderer = new DefaultListCellRenderer();

		@Override
		public Component getListCellRendererComponent(JList<? extends Integer> list, Integer value, int index,
				boolean isSelected, boolean cellHasFocus) {

			AddrPair ap = connAddrMap.get(value);
			String text = value + " : " + ap.src + " -> " + ap.dst;
			
			ConnectionData cd = channelEventMap.get(value);
			text += " (" + cd.getPendingEventCount() + "/" + cd.getEventCount() + ")";
			if (cd.isClosed())
				text += " CLOSED";
			JLabel renderer = (JLabel) defaultRenderer.getListCellRendererComponent(list, text, index, isSelected,
					cellHasFocus);
			if (cd.isException())
				renderer.setBackground(Color.PINK);
			else if (cd.isClosed())
				renderer.setBackground(Color.LIGHT_GRAY);

			return renderer;
		}

	}

	private class ListTableModelAdapter extends AbstractTableModel implements ListDataListener {

		private ListModel<ConnectionData> listModel = null;
		private String[] columnNames = new String[] { "Src", "Dst", "Events", "Opened", "Closed" };
		private Class<?>[] columnClasses = new Class<?>[] { SocketAddress.class, SocketAddress.class, String.class, Date.class, Date.class};

		public ListTableModelAdapter(ListModel<ConnectionData> listModel) {
			setListModel(listModel);
		}

		public void setListModel(ListModel<ConnectionData> listModel) {
			if (this.listModel == listModel)
				return;
			if (this.listModel != null)
				this.listModel.removeListDataListener(this);
			this.listModel = listModel;
			if (this.listModel != null)
				this.listModel.addListDataListener(this);
			fireTableDataChanged();
		}

		@Override
		public int getRowCount() {
			return listModel == null ? 0 : listModel.getSize();
		}

		@Override
		public int getColumnCount() {
			return columnNames.length;
		}

		@Override
		public String getColumnName(int columnIndex) {
			return columnNames[columnIndex];
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			return columnClasses[columnIndex];
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			if (listModel == null || rowIndex > listModel.getSize())
				return null;
			ConnectionData cd = listModel.getElementAt(rowIndex);
			AddrPair ap;
			switch (columnIndex) {
			case 0:
				ap = connAddrMap.get(cd);
				return ap.src;
			case 1:
				ap = connAddrMap.get(cd);
				return ap.dst;
			case 2:
				return cd.getPendingEventCount() + "/" + cd.getEventCount();
			case 3:
				return new Date(cd.getEvents().getElementAt(0).getEventTime());
			case 4:
				return cd.isClosed() ? new Date(cd.getEvents().getElementAt(cd.getEventCount()-1).getExecutionTime()) : null;
			}
			return null;
		}

		@Override
		public void intervalAdded(ListDataEvent e) {
			fireTableRowsInserted(e.getIndex0(), e.getIndex1());
		}

		@Override
		public void intervalRemoved(ListDataEvent e) {
			fireTableRowsDeleted(e.getIndex0(), e.getIndex1());
		}

		@Override
		public void contentsChanged(ListDataEvent e) {
			fireTableRowsUpdated(e.getIndex0(), e.getIndex1());
		}

	}
}
