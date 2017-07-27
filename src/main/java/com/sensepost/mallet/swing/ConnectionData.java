package com.sensepost.mallet.swing;

import java.util.BitSet;

import javax.swing.DefaultListModel;
import javax.swing.ListModel;

import com.sensepost.mallet.InterceptController.ChannelActiveEvent;
import com.sensepost.mallet.InterceptController.ChannelEvent;
import com.sensepost.mallet.InterceptController.ChannelInactiveEvent;

public class ConnectionData {

	private DefaultListModel<ChannelEvent> events = new DefaultListModel<>();
	private BitSet pending = new BitSet();
	private int closed = 0;
	
	public ConnectionData() {
	}
	
	public void addChannelEvent(ChannelEvent e) throws Exception {
		events.addElement(e);
		int n = events.size() - 1;
		pending.set(n);
		if ((e instanceof ChannelActiveEvent) && pending.nextSetBit(0) == n) {
			e.execute();
			pending.clear(events.size());
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
		return closed == 2;
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
				if (execute)
					e.execute();
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
