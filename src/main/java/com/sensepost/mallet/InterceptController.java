package com.sensepost.mallet;

import java.net.SocketAddress;

public interface InterceptController {

	void addChannelEvent(ChannelEvent evt) throws Exception;

	public enum Direction {
		Client_Server, Server_Client
	}

	public class ChannelEvent {

		private long eventTime, executionTime = -1;
		private int connection_number;
		private Direction direction;
		private boolean executed = false;

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

		public void execute() throws Exception {
			executed = true;
			executionTime = System.currentTimeMillis();
		}

		public boolean isExecuted() {
			return executed;
		}
		
		public long getExecutionTime() {
			return executionTime;
		}
	}

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

	public abstract class ChannelInactiveEvent extends ChannelEvent {
		public ChannelInactiveEvent(int connection, Direction direction) {
			super(connection, direction);
		}
	}

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

}
