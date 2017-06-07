package com.sensepost.mallet.events;

import java.net.SocketAddress;

public abstract class ChannelActiveEvent extends ChannelEvent {

	private SocketAddress src, dst;
	
	public ChannelActiveEvent(int connection, Direction direction, SocketAddress src, SocketAddress dst) {
		super(connection, direction);
		this.src = src;
		this.dst = dst;
	}

	public SocketAddress getSourceAddress() {
		return src;
	}
	
	public SocketAddress getDestinationAddress() {
		return dst;
	}
}
