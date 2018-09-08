package com.sensepost.mallet.swing;

import java.net.SocketAddress;
import java.util.BitSet;

import javax.swing.DefaultListModel;
import javax.swing.ListModel;

import com.sensepost.mallet.InterceptController.ChannelActiveEvent;
import com.sensepost.mallet.InterceptController.ChannelEvent;
import com.sensepost.mallet.InterceptController.ChannelInactiveEvent;
import com.sensepost.mallet.InterceptController.ChannelReadEvent;
import com.sensepost.mallet.InterceptController.ExceptionCaughtEvent;

public class ConnectionData {

	private DefaultListModel<ChannelEvent> events = new DefaultListModel<>();
	private BitSet pending = new BitSet();
	private int closed = 0;
	private boolean exception = false;
	private SocketAddress remoteAddress1, localAddress1, localAddress2 = null, remoteAddress2 = null;
	private String channelId1, channelId2 = null;

	public ConnectionData(String channelId1, SocketAddress remoteAddress1, SocketAddress localAddress1) {
		this.remoteAddress1 = remoteAddress1;
		this.localAddress1 = localAddress1;
	}

	public String getChannelId1() {
		return channelId1;
	}

	public String getChannelId2() {
		return channelId2;
	}

	public SocketAddress getRemoteAddress1() {
		return remoteAddress1;
	}

	public SocketAddress getLocalAddress1() {
		return localAddress1;
	}

	public void setConnectedAddresses(String channelId2, SocketAddress localAddress2, SocketAddress remoteAddress2) {
		this.channelId2 = channelId2;
		this.localAddress2 = localAddress2;
		this.remoteAddress2 = remoteAddress2;
	}

	public SocketAddress getLocalAddress2() {
		return localAddress2;
	}

	public SocketAddress getRemoteAddress2() {
		return remoteAddress2;
	}

	public void addChannelEvent(ChannelEvent e) throws Exception {
		events.addElement(e);
		if (e instanceof ExceptionCaughtEvent)
			exception = true;
		int n = events.size() - 1;
		pending.set(n);
		if ((e instanceof ChannelActiveEvent) && pending.nextSetBit(0) == n) {
			e.execute();
			pending.clear(n);
		}
		if (e instanceof ChannelInactiveEvent) {
			closed++;
		}
	}
	
	public int getEventCount() {
		return events.size();
	}
	
	public int getPendingEventCount() {
		return pending.cardinality();
	}

	public boolean isClosed() {
		return closed >= 2;
	}

	public boolean isException() {
		return exception;
	}
	
	public void executeNextEvents(int p) throws Exception {
		while (pending.nextSetBit(0) <= p && pending.nextSetBit(0) >= 0)
			doNextEvent(true);
	}
	
	public void dropNextEvents(int p) throws Exception {
		while (pending.nextSetBit(0) <= p && pending.nextSetBit(0) >= 0)
			doNextEvent(false);
	}
	
	public void executeNextEvent() throws Exception {
		doNextEvent(true);
	}
	
	public void dropNextEvent() throws Exception {
		doNextEvent(false);
	}

	private void doNextEvent(boolean execute) throws Exception {
		int n = pending.nextSetBit(0);
		if (n >= 0) {
			ChannelEvent e = events.elementAt(n);
			try {
				pending.clear(n);
				if (execute) {
					if (e instanceof ChannelReadEvent) {
						ChannelReadEvent cre = (ChannelReadEvent) e;
						events.set(n, cre);
					}
					e.execute();
				}
				events.set(n, events.get(n)); // trigger an update event
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

	}
	
	public int getNextPendingEvent() {
		return pending.nextSetBit(0);
	}
	
	public void executeAllEvents() throws Exception {
		while (pending.nextSetBit(0) >= 0)
			doNextEvent(true);
	}
	
	public void dropAllEvents()  throws Exception {
		while (pending.nextSetBit(0) >= 0)
			doNextEvent(false);
	}
	
	public ListModel<ChannelEvent> getEvents() {
		return events;
	}
	
}
