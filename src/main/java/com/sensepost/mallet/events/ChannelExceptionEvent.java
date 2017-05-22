package com.sensepost.mallet.events;

import java.net.SocketAddress;

public abstract class ChannelExceptionEvent extends ChannelEvent {

	private Throwable cause;
	
	public ChannelExceptionEvent(SocketAddress src, SocketAddress dst, Throwable cause) {
		super(src, dst);
		this.cause = cause;
	}
	
	public Throwable getCause() {
		return cause;
	}
	
}
