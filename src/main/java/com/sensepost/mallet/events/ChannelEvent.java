package com.sensepost.mallet.events;

public abstract class ChannelEvent {

	private long eventTime;
	private int connection_number;
	private Direction direction;
	
	public enum Direction {
		Client_Server,
		Server_Client
	}

	public ChannelEvent(int connection, Direction direction) {
		this.connection_number = connection;
		this.direction = direction;
		this.eventTime = System.currentTimeMillis();
	}

	public int getConnectionIdentifier() {
		return connection_number;
	}

	public long getEventTime() {
		return eventTime;
	}

	public Direction getDirection() {
		return direction;
	}
	
	public abstract void execute() throws Exception;

}
