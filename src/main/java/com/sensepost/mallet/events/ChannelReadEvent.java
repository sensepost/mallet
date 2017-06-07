package com.sensepost.mallet.events;

public abstract class ChannelReadEvent extends ChannelEvent {

	private Object msg;
	
	public ChannelReadEvent(int connection, Direction direction, Object msg) {
		super(connection, direction);
		this.msg = msg;
	}
	
	public Object getMessage() {
		return msg;
	}
	
	public void setMessage(Object msg) {
		this.msg = msg;
	}
}
