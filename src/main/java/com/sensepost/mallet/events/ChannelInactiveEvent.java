package com.sensepost.mallet.events;

public abstract class ChannelInactiveEvent extends ChannelEvent {

	public ChannelInactiveEvent(int connection, Direction direction) {
		super(connection, direction);
	}
	
}
