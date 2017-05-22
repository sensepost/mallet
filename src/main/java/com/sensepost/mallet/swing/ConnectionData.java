package com.sensepost.mallet.swing;

import javax.swing.DefaultListModel;
import javax.swing.ListModel;

import com.sensepost.mallet.events.ChannelActiveEvent;
import com.sensepost.mallet.events.ChannelEvent;
import com.sensepost.mallet.events.ChannelReadEvent;

import io.netty.buffer.Unpooled;

public class ConnectionData {

	private DefaultListModel<ChannelEvent> completedEvents = new DefaultListModel<>();
	private DefaultListModel<ChannelEvent> pendingEvents = new DefaultListModel<>();
	
	public ConnectionData() {
	}
	
	public void addChannelEvent(ChannelEvent e) throws Exception {
		if ((e instanceof ChannelActiveEvent) && pendingEvents.getSize() == 0) {
			((ChannelActiveEvent)e).execute();
			completedEvents.addElement(e);
		} else {
			pendingEvents.addElement(e);
		}
	}
	
	public void executeNextEvent() {
		if (pendingEvents.getSize() > 0) {
			ChannelEvent e = pendingEvents.remove(0);
			try {
				e.execute();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			completedEvents.addElement(e);
		}
	}
	
	public void dropNextEvent() {
		if (pendingEvents.getSize() > 0) {
			ChannelEvent e = pendingEvents.remove(0);
			if (e instanceof ChannelReadEvent)
				((ChannelReadEvent)e).setMessage(Unpooled.EMPTY_BUFFER);
			try {
				e.execute();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			completedEvents.addElement(e);
		}
	}
	
	public ListModel<ChannelEvent> getCompletedEvents() {
		return completedEvents;
	}
	
	public ListModel<ChannelEvent> getPendingEvents() {
		return pendingEvents;
	}
}
