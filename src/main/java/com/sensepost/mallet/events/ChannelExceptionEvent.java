package com.sensepost.mallet.events;

public abstract class ChannelExceptionEvent extends ChannelEvent {

	private Throwable cause;
	
	public ChannelExceptionEvent(int connection, Direction direction, Throwable cause) {
		super(connection, direction);
		this.cause = cause;
	}
	
	public Throwable getCause() {
		return cause;
	}
	
}
