package com.sensepost.mallet.swing;

import java.util.BitSet;

import javax.swing.DefaultListModel;
import javax.swing.ListModel;

import com.sensepost.mallet.events.ChannelActiveEvent;
import com.sensepost.mallet.events.ChannelEvent;
import com.sensepost.mallet.events.ChannelReadEvent;

import io.netty.buffer.Unpooled;

public class ConnectionData {

	private DefaultListModel<ChannelEvent> events = new DefaultListModel<>();
	private BitSet pending = new BitSet();
	
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
				if (execute)
					e.execute();
				pending.clear(n);
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
