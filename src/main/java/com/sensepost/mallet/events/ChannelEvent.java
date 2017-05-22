package com.sensepost.mallet.events;

import java.net.SocketAddress;

public abstract class ChannelEvent {

	private SocketAddress src, dst;
	private long eventTime;
	
	public ChannelEvent(SocketAddress src, SocketAddress dst) {
		this.src = src;
		this.dst = dst;
		this.eventTime = System.currentTimeMillis();
	}
		
	public SocketAddress getSourceAddress() {
		return src;
	}
	
	public SocketAddress getDestinationAddress() {
		return dst;
	}
	
	public long getEventTime() {
		return eventTime;
	}
	
	public abstract void execute() throws Exception;
	
}
