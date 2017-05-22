package com.sensepost.mallet.events;

import java.net.SocketAddress;

public abstract class ChannelActiveEvent extends ChannelEvent {

	public ChannelActiveEvent(SocketAddress src, SocketAddress dst) {
		super(src, dst);
	}

}
