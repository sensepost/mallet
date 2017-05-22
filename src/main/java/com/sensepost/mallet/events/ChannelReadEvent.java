package com.sensepost.mallet.events;

import java.net.SocketAddress;

public abstract class ChannelReadEvent extends ChannelEvent {

	private Object msg;
	
	public ChannelReadEvent(SocketAddress src, SocketAddress dst, Object msg) {
		super(src, dst);
		this.msg = msg;
	}
	
	public Object getMessage() {
		return msg;
	}
	
	public void setMessage(Object msg) {
		this.msg = msg;
	}
}
