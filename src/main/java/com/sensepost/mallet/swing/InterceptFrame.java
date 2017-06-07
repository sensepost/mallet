package com.sensepost.mallet.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.sensepost.mallet.InterceptController;
import com.sensepost.mallet.events.ChannelActiveEvent;
import com.sensepost.mallet.events.ChannelEvent;
import com.sensepost.mallet.events.ChannelReadEvent;

import io.netty.buffer.ByteBufHolder;

public class InterceptFrame extends JFrame implements InterceptController {
	private JList<Integer> list;
	private DefaultListModel<Integer> listModel = new DefaultListModel<>();

	private Map<Integer, AddrPair> connAddrMap = new HashMap<>();
	private Map<Integer, ConnectionData> channelEventMap = new LinkedHashMap<>();

	private ConnectionDataPanel cdp;
	private JCheckBoxMenuItem interceptMenuItem;

	public InterceptFrame() {
		setTitle("Mallet");
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

		JSplitPane splitPane = new JSplitPane();
		getContentPane().add(splitPane, BorderLayout.CENTER);

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

		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);

		JMenu mnFile = new JMenu("File");
		menuBar.add(mnFile);

		JMenuItem mntmExit = new JMenuItem("Exit");
		mntmExit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				System.exit(0);
			}
		});
		mnFile.add(mntmExit);

		interceptMenuItem = new JCheckBoxMenuItem("Intercept");
		interceptMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (!interceptMenuItem.isSelected()) {
					sendAllPendingEvents();
				}
			}
		});
		menuBar.add(interceptMenuItem);
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

	@Override
	public void addChannelEvent(final ChannelEvent evt) throws Exception {
		if (evt instanceof ChannelReadEvent) {
			Object o = ((ChannelReadEvent)evt).getMessage();
			if (o instanceof ByteBufHolder) {
				o = ((ByteBufHolder) o).duplicate();
				((ChannelReadEvent)evt).setMessage(o);
			}
		}
		SwingUtilities.invokeAndWait(new Runnable() {
			public void run() {
				try {
					addChannelEventEDT(evt);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	protected void addChannelEventEDT(ChannelEvent evt) throws Exception {
		Integer cp = evt.getConnectionIdentifier();

		if (evt instanceof ChannelActiveEvent) {
			ChannelActiveEvent cae = (ChannelActiveEvent) evt;
			SocketAddress src = cae.getSourceAddress();
			SocketAddress dst = cae.getDestinationAddress();
			AddrPair ap = new AddrPair(src, dst);
			synchronized (connAddrMap) {
				if (connAddrMap.get(cp) == null)
					connAddrMap.put(cp, ap);
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
			if (!interceptMenuItem.isSelected()) {
				try {
					eventList.executeAllEvents();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	protected JComponent createComponent(ChannelEvent e) {
		String text = "<html><body>" + e.getEventTime() + "<br>" + e.getClass().getSimpleName();
		if (e instanceof ChannelReadEvent) {
			text += "<br>" + ((ChannelReadEvent) e).getMessage().toString();
		}
		text += "</body></html>";
		return new JLabel(text);
	}

	protected JList<Integer> getConnectionList() {
		return list;
	}

	protected static class AddrPair {
		private SocketAddress src, dst;

		public AddrPair(SocketAddress src, SocketAddress dst) {
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
			JLabel renderer = (JLabel) defaultRenderer.getListCellRendererComponent(list, text, index, isSelected,
					cellHasFocus);

			return renderer;
		}

	}

	protected JCheckBoxMenuItem getInterceptMenuItem() {
		return interceptMenuItem;
	}

}
