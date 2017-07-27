package com.sensepost.mallet.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.net.SocketAddress;
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
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.sensepost.mallet.InterceptController.ChannelActiveEvent;
import com.sensepost.mallet.InterceptController.ChannelEvent;

public class ConnectionPanel extends JPanel {

	private ConnectionDataPanel cdp;

	private JList<Integer> list;
	private DefaultListModel<Integer> listModel = new DefaultListModel<>();

	private Map<Integer, AddrPair> connAddrMap = new HashMap<>();
	private Map<Integer, ConnectionData> channelEventMap = new LinkedHashMap<>();

	private boolean intercept = false;
	
	public ConnectionPanel() {
		setLayout(new BorderLayout(0, 0));
		JSplitPane splitPane = new JSplitPane();
		splitPane.setResizeWeight(0.5);
		add(splitPane);

		JScrollPane scrollPane = new JScrollPane();
		splitPane.setLeftComponent(scrollPane);

		list = new JList<>(listModel);
		list.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting())
					return;
				ConnectionData cd = null;
				if (list.getSelectedValue() != null) {
					synchronized (channelEventMap) {
						cd = channelEventMap.get(list.getSelectedValue());
					}
				}
				cdp.setConnectionData(cd);
			}
		});
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new ChannelPairCellRenderer());
		scrollPane.setViewportView(list);

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

	public void addChannelEvent(ChannelEvent evt) throws Exception {
		Integer cp = evt.getConnectionIdentifier();

		if (evt instanceof ChannelActiveEvent) {
			ChannelActiveEvent cae = (ChannelActiveEvent) evt;
			SocketAddress src = cae.getSourceAddress();
			SocketAddress dst = cae.getDestinationAddress();
			AddrPair ap = new AddrPair(src, dst);
			synchronized (connAddrMap) {
				if (connAddrMap.get(cp) == null)
					connAddrMap.put(cp, ap);
				else
					connAddrMap.get(cp).dst = cae.getSourceAddress();
			}	
		}
		
		synchronized (channelEventMap) {
			ConnectionData eventList;
			if (!channelEventMap.containsKey(cp)) {
				eventList = new ConnectionData();
				channelEventMap.put(cp, eventList);
				listModel.addElement(cp);
			} else {
				eventList = channelEventMap.get(cp);
			}
			eventList.addChannelEvent(evt);

			// force redraw of the element in the list
			int index = listModel.indexOf(cp);
			listModel.setElementAt(listModel.getElementAt(index), index);

			if (!intercept) {
				try {
					eventList.executeAllEvents();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void sendAllPendingEvents() {
		synchronized (channelEventMap) {
			for (Integer conn : channelEventMap.keySet()) {
				try {
					channelEventMap.get(conn).executeAllEvents();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}


	protected JList<Integer> getConnectionList() {
		return list;
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
			if (cd.isClosed())
				renderer.setBackground(Color.LIGHT_GRAY);

			return renderer;
		}

	}


}
