package com.sensepost.mallet.events;

public abstract class ChannelUserEvent extends ChannelEvent {

	private Object evt;

	public ChannelUserEvent(int connection, Direction direction, Object evt) {
		super(connection, direction);
		this.evt = evt;
	}

	public Object getUserEvent() {
		return evt;
	}
	
}
