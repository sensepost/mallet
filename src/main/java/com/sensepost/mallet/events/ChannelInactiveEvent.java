package com.sensepost.mallet.events;

import java.net.SocketAddress;

public abstract class ChannelInactiveEvent extends ChannelEvent {

	public ChannelInactiveEvent(SocketAddress src, SocketAddress dst) {
		super(src, dst);
	}
	
}
