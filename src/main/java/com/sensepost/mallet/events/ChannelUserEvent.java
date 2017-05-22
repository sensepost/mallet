package com.sensepost.mallet.events;

import java.net.SocketAddress;

public abstract class ChannelUserEvent extends ChannelEvent {

	private Object evt;

	public ChannelUserEvent(SocketAddress src, SocketAddress dst, Object evt) {
		super(src, dst);
		this.evt = evt;
	}

	public Object getUserEvent() {
		return evt;
	}
	
}
