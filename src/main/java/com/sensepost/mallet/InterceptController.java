package com.sensepost.mallet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.SocketAddress;

import com.sensepost.mallet.persistence.MessageDAO;

public interface InterceptController {

	void setMessageDAO(MessageDAO dao);
	
	void addChannelEvent(ChannelEvent evt) throws Exception;

	public enum Direction {
		Client_Server, Server_Client
	}

	public class ChannelEvent {

		private long eventTime, executionTime = -1;
		private int connectionNumber;
		private Direction direction;
		private Throwable previousExecution = null;

		public ChannelEvent(int connection, Direction direction, long eventTime, long executionTime) {
			this.connectionNumber = connection;
			this.direction = direction;
			this.eventTime = eventTime;
			this.executionTime = executionTime;
		}
		
		public ChannelEvent(int connection, Direction direction) {
			this.connectionNumber = connection;
			this.direction = direction;
			this.eventTime = System.currentTimeMillis();
		}

		public int getConnectionIdentifier() {
			return connectionNumber;
		}

		public long getEventTime() {
			return eventTime;
		}

		public Direction getDirection() {
			return direction;
		}

		public void execute() throws Exception {
			if (!isExecuted()) {
				executionTime = System.currentTimeMillis();
				previousExecution = new RuntimeException("Executed by");
			}
			else 
				throw new IllegalStateException("Already executed!", previousExecution);
		}

		public boolean isExecuted() {
			return executionTime != -1;
		}
		
		public long getExecutionTime() {
			return executionTime;
		}
	}

	public abstract class ChannelActiveEvent extends ChannelEvent {

		private SocketAddress remote, local;

		public ChannelActiveEvent(int connection, Direction direction, long eventTime, long executionTime, SocketAddress remote, SocketAddress local) {
			super(connection, direction, eventTime, executionTime);
			this.remote = remote;
			this.local = local;
		}
		
		public ChannelActiveEvent(int connection, Direction direction, SocketAddress remote, SocketAddress local) {
			super(connection, direction);
			this.remote = remote;
			this.local = local;
		}

		public SocketAddress getRemoteAddress() {
			return remote;
		}

		public SocketAddress getLocalAddress() {
			return local;
		}
	}

	public abstract class ChannelInactiveEvent extends ChannelEvent {
		public ChannelInactiveEvent(int connection, Direction direction, long eventTime, long executionTime) {
			super(connection, direction, eventTime, executionTime);
		}
		
		public ChannelInactiveEvent(int connection, Direction direction) {
			super(connection, direction);
		}
	}

	public abstract class ChannelExceptionEvent extends ChannelEvent {

		private String cause;

		public ChannelExceptionEvent(int connection, Direction direction, long eventTime, long executionTime, String cause) {
			super(connection, direction, eventTime, executionTime);
			this.cause = cause;
		}
		
		public ChannelExceptionEvent(int connection, Direction direction, Throwable cause) {
			super(connection, direction);
			StringWriter sw = new StringWriter();
			cause.printStackTrace(new PrintWriter(sw));
			this.cause = sw.toString();
		}

		public String getCause() {
			return cause;
		}

	}

	public abstract class ChannelReadEvent extends ChannelEvent {

		private Object msg;
		private MessageDAO dao = null;
		private String messageId = null;
		private Class<?> msgClass = null;
		
		public ChannelReadEvent(int connection, Direction direction, long eventTime, long executionTime, MessageDAO dao, String messageId) {
			super(connection, direction, eventTime, executionTime);
			this.dao = dao;
			this.messageId = messageId;
		}
		
		public ChannelReadEvent(int connection, Direction direction, Object msg) {
			super(connection, direction);
			setMessage(msg);
		}
		
		public Object getMessage() {
			if (msg == null && dao != null && messageId != null) {
				return dao.readObject(messageId);
			}
			if (msg instanceof ByteBuf) { // FIXME: Check if this actually makes a difference
				return ((ByteBuf) msg).duplicate();
			} else if (msg instanceof ByteBufHolder) {
				return ((ByteBufHolder) msg).duplicate();
			}
			return msg;
		}
		
		public void setMessage(Object msg) {
			if (msgClass != null) {
				System.out.println("Changing message! Class was previously " + msgClass + ", now changing to " + msg.getClass());
			}
			msgClass = msg.getClass();
			if (dao != null && msg != null)
				this.messageId = dao.writeObject(msg);
			this.msg = msg;
		}
		
		public void setDao(MessageDAO dao) {
			messageId = dao.writeObject(getMessage());
			this.dao = dao;
		}
		
		public String getMessageId() {
			return messageId;
		}
		
		public void execute() throws Exception {
			super.execute();
			if (messageId != null)
				msg = null;
		}
	}

	public abstract class ChannelUserEvent extends ChannelEvent {

		private Object evt;

		public ChannelUserEvent(int connection, Direction direction, long eventTime, long executionTime, Object evt) {
			super(connection, direction, eventTime, executionTime);
			this.evt = evt;
		}
		
		public ChannelUserEvent(int connection, Direction direction, Object evt) {
			super(connection, direction);
			this.evt = evt;
		}

		public Object getUserEvent() {
			return evt;
		}
		
	}

}
