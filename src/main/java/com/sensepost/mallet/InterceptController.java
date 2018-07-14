package com.sensepost.mallet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.SocketAddress;

import com.sensepost.mallet.persistence.MessageDAO;

public interface InterceptController {

	void setMessageDAO(MessageDAO dao);
	
	void addChannelEvent(ChannelEvent evt) throws Exception;

	void linkChannels(String channel1, String channel2);

	public enum Direction {
		Client_Server, Server_Client
	}

	public class ChannelEvent {

		private ChannelHandlerContext ctx = null;
		private long eventTime, executionTime = -1;
		private String connection;
		private Direction direction;
		private Throwable previousExecution = null;

		public ChannelEvent(String connection, Direction direction, long eventTime, long executionTime) {
			this.connection = connection;
			this.direction = direction;
			this.eventTime = eventTime;
			this.executionTime = executionTime;
		}
		
		public ChannelEvent(ChannelHandlerContext ctx, String connection, Direction direction) {
			this.ctx = ctx;
			this.connection = connection;
			this.direction = direction;
			this.eventTime = System.currentTimeMillis();
		}

		public ChannelHandlerContext getChannelHandlerContext() {
			return ctx;
		}

		public String getConnectionIdentifier() {
			return connection;
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

		public ChannelActiveEvent(String connection, Direction direction, long eventTime, long executionTime, SocketAddress remote, SocketAddress local) {
			super(connection, direction, eventTime, executionTime);
			this.remote = remote;
			this.local = local;
		}
		
		public ChannelActiveEvent(ChannelHandlerContext ctx, String connection, Direction direction, SocketAddress remote, SocketAddress local) {
			super(ctx, connection, direction);
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
		public ChannelInactiveEvent(String connection, Direction direction, long eventTime, long executionTime) {
			super(connection, direction, eventTime, executionTime);
		}
		
		public ChannelInactiveEvent(ChannelHandlerContext ctx, String connection, Direction direction) {
			super(ctx, connection, direction);
		}
	}

	public abstract class ChannelExceptionEvent extends ChannelEvent {

		private String cause;

		public ChannelExceptionEvent(String connection, Direction direction, long eventTime, long executionTime, String cause) {
			super(connection, direction, eventTime, executionTime);
			this.cause = cause;
		}
		
		public ChannelExceptionEvent(ChannelHandlerContext ctx, String connection, Direction direction, Throwable cause) {
			super(ctx, connection, direction);
			StringWriter sw = new StringWriter();
			cause.printStackTrace(new PrintWriter(sw));
			this.cause = sw.toString();
		}

		public String getCause() {
			return cause;
		}

	}

	public abstract class ChannelMessageEvent extends ChannelEvent {

		private Object msg;
		private MessageDAO dao = null;
		private String messageId = null;

		public ChannelMessageEvent(String connection, Direction direction, long eventTime, long executionTime, MessageDAO dao, String messageId) {
			super(connection, direction, eventTime, executionTime);
			this.dao = dao;
			this.messageId = messageId;
		}

		public ChannelMessageEvent(ChannelHandlerContext ctx, String connection, Direction direction, Object msg) {
			super(ctx, connection, direction);
			setMessage(msg);
		}

		public Object getMessage() {
			if (msg == null && dao != null && messageId != null) {
				return dao.readObject(messageId);
			}
			if (msg instanceof ByteBuf) {
				return ((ByteBuf) msg).copy();
			} else if (msg instanceof ByteBufHolder) {
				return ((ByteBufHolder) msg).copy();
			} else {
				return ReferenceCountUtil.retain(msg);
			}
		}

		public void setMessage(Object msg) {
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

	public abstract class ChannelReadEvent extends ChannelMessageEvent {

		public ChannelReadEvent(String connection, Direction direction, long eventTime, long executionTime, MessageDAO dao, String messageId) {
			super(connection, direction, eventTime, executionTime, dao, messageId);
		}

		public ChannelReadEvent(ChannelHandlerContext ctx, String connection, Direction direction, Object msg) {
			super(ctx, connection, direction, msg);
		}

	}

	public abstract class ChannelWriteEvent extends ChannelMessageEvent {

		public ChannelWriteEvent(String connection, Direction direction, long eventTime, long executionTime, MessageDAO dao, String messageId) {
			super(connection, direction, eventTime, executionTime, dao, messageId);
		}

		public ChannelWriteEvent(ChannelHandlerContext ctx, String connection, Direction direction, Object msg) {
			super(ctx, connection, direction, msg);
		}

	}

	public abstract class ChannelUserEvent extends ChannelEvent {

		private Object evt;

		public ChannelUserEvent(String connection, Direction direction, long eventTime, long executionTime, Object evt) {
			super(connection, direction, eventTime, executionTime);
			this.evt = evt;
		}
		
		public ChannelUserEvent(ChannelHandlerContext ctx, String connection, Direction direction, Object evt) {
			super(ctx, connection, direction);
			this.evt = evt;
		}

		public Object getUserEvent() {
			return evt;
		}
		
	}

}
