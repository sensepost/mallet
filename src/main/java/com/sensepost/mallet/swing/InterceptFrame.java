package com.sensepost.mallet.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.SocketAddress;
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
import com.sensepost.mallet.events.ChannelEvent;
import com.sensepost.mallet.events.ChannelReadEvent;

public class InterceptFrame extends JFrame implements InterceptController {
	private JList<AddrPair> list;
	private DefaultListModel<AddrPair> listModel = new DefaultListModel<>();

	private Map<AddrPair, ConnectionData> channelEventMap = new LinkedHashMap<>();

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
			for (AddrPair cp : channelEventMap.keySet()) {
				try {
					channelEventMap.get(cp).executeAllEvents();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	public void addChannelEvent(final ChannelEvent evt) throws Exception {
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
		SocketAddress src = evt.getSourceAddress();
		SocketAddress dst = evt.getDestinationAddress();
		synchronized (channelEventMap) {
			AddrPair cp = new AddrPair(src, dst);
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

	protected JList<AddrPair> getConnectionList() {
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

	private static class ChannelPairCellRenderer implements ListCellRenderer<AddrPair> {
		protected DefaultListCellRenderer defaultRenderer = new DefaultListCellRenderer();

		@Override
		public Component getListCellRendererComponent(JList<? extends AddrPair> list, AddrPair value, int index,
				boolean isSelected, boolean cellHasFocus) {

			String text = value.src + " -> " + value.dst;
			JLabel renderer = (JLabel) defaultRenderer.getListCellRendererComponent(list, text, index, isSelected,
					cellHasFocus);

			return renderer;
		}

	}

	protected JCheckBoxMenuItem getInterceptMenuItem() {
		return interceptMenuItem;
	}

}
