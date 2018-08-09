package com.sensepost.mallet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
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

		protected ChannelHandlerContext ctx = null;
		private long eventTime, executionTime = -1;
		private String connection;
		private Direction direction;
		private Throwable previousExecution = null;

		public ChannelEvent(String connection, Direction direction,
				long eventTime, long executionTime) {
			this.connection = connection;
			this.direction = direction;
			this.eventTime = eventTime;
			this.executionTime = executionTime;
		}

		public ChannelEvent(ChannelHandlerContext ctx) {
			this.ctx = ctx;
			connection = ctx.channel().id().asLongText();
			direction = ctx.channel().parent() == null ? Direction.Server_Client
					: Direction.Client_Server;
			this.eventTime = System.currentTimeMillis();
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
			} else
				throw new IllegalStateException("Already executed!",
						previousExecution);
		}

		public boolean isExecuted() {
			return executionTime != -1;
		}

		public long getExecutionTime() {
			return executionTime;
		}

		public String getEventDescription() {
			String name = getClass().getSimpleName();
			return name.substring(0, name.length()-5);
		}

		@Override
		public String toString() {
			return getEventDescription();
		}
	}

	public class ChannelActiveEvent extends ChannelEvent {

		private SocketAddress remoteAddress, localAddress;

		public ChannelActiveEvent(String connection, Direction direction,
				long eventTime, long executionTime, SocketAddress remoteAddress, SocketAddress localAddress) {
			super(connection, direction, eventTime, executionTime);
			this.remoteAddress = remoteAddress;
			this.localAddress = localAddress;
		}

		public ChannelActiveEvent(ChannelHandlerContext ctx) {
			super(ctx);
			this.remoteAddress = ctx.channel().remoteAddress();
			this.localAddress = ctx.channel().localAddress();
		}

		public SocketAddress getRemoteAddress() {
			return remoteAddress;
		}

		public SocketAddress getLocalAddress() {
			return localAddress;
		}

		@Override
		public void execute() throws Exception {
			if (ctx != null)
				ctx.fireChannelActive();
			super.execute();
		}
	}

	public class ChannelInactiveEvent extends ChannelEvent {
		public ChannelInactiveEvent(String connection, Direction direction,
				long eventTime, long executionTime) {
			super(connection, direction, eventTime, executionTime);
		}

		public ChannelInactiveEvent(ChannelHandlerContext ctx) {
			super(ctx);
		}

		@Override
		public void execute() throws Exception {
			if (ctx != null)
				ctx.fireChannelInactive();
			super.execute();
		}
	}

	public class ExceptionCaughtEvent extends ChannelEvent {

		private Throwable cause;
		private String causeString;

		public ExceptionCaughtEvent(String connection, Direction direction,
				long eventTime, long executionTime, String cause) {
			super(connection, direction, eventTime, executionTime);
			this.causeString = cause;
		}

		public ExceptionCaughtEvent(ChannelHandlerContext ctx, Throwable cause) {
			super(ctx);
			this.cause = cause;
			StringWriter sw = new StringWriter();
			cause.printStackTrace(new PrintWriter(sw));
			this.causeString = sw.toString();
		}

		public String getCause() {
			return causeString;
		}

		@Override
		public void execute() throws Exception {
			if (ctx != null)
				ctx.fireExceptionCaught(cause);
			super.execute();
		}

	}

	public abstract class ChannelMessageEvent extends ChannelEvent {

		private Object msg;
		private MessageDAO dao = null;
		private String messageId = null;

		public ChannelMessageEvent(String connection, Direction direction,
				long eventTime, long executionTime, MessageDAO dao,
				String messageId) {
			super(connection, direction, eventTime, executionTime);
			this.dao = dao;
			this.messageId = messageId;
		}

		public ChannelMessageEvent(ChannelHandlerContext ctx, Object msg) {
			super(ctx);
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

	public class ChannelReadEvent extends ChannelMessageEvent {

		public ChannelReadEvent(String connection, Direction direction,
				long eventTime, long executionTime, MessageDAO dao,
				String messageId) {
			super(connection, direction, eventTime, executionTime, dao,
					messageId);
		}

		public ChannelReadEvent(ChannelHandlerContext ctx, Object msg) {
			super(ctx, msg);
		}

		@Override
		public void execute() throws Exception {
			if (ctx != null)
				ctx.fireChannelRead(getMessage());
			super.execute();
		}

	}

	public class WriteEvent extends ChannelMessageEvent {

		protected ChannelPromise promise;

		public WriteEvent(String connection, Direction direction,
				long eventTime, long executionTime, MessageDAO dao,
				String messageId) {
			super(connection, direction, eventTime, executionTime, dao,
					messageId);
		}

		public WriteEvent(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
			super(ctx, msg);
			this.promise = promise;
		}

		@Override
		public void execute() throws Exception {
			if (ctx != null)
				ctx.write(getMessage());
			super.execute();
		}

	}

	public class UserEventTriggeredEvent extends ChannelEvent {

		private Object evt;

		public UserEventTriggeredEvent(String connection, Direction direction,
				long eventTime, long executionTime, Object evt) {
			super(connection, direction, eventTime, executionTime);
			this.evt = evt;
		}

		public UserEventTriggeredEvent(ChannelHandlerContext ctx, Object evt) {
			super(ctx);
			this.evt = evt;
		}

		public Object getUserEvent() {
			return evt;
		}

		@Override
		public void execute() throws Exception {
			if (ctx != null)
				ctx.fireUserEventTriggered(getUserEvent());
			super.execute();
		}

	}

	public abstract class ChannelPromiseEvent extends ChannelEvent {

		protected ChannelPromise promise;

		public ChannelPromiseEvent(String connection, Direction direction,
				long eventTime, long executionTime) {
			super(connection, direction, eventTime, executionTime);
		}
		public ChannelPromiseEvent(ChannelHandlerContext ctx, ChannelPromise promise) {
			super(ctx);
			this.promise = promise;
		}
		
		protected ChannelPromise getPromise() {
			return promise;
		}
	}

	public class BindEvent extends ChannelPromiseEvent {

		private SocketAddress localAddress;

		public BindEvent(String connection, Direction direction,
				long eventTime, long executionTime, SocketAddress localAddress) {
			super(connection, direction, eventTime, executionTime);
		}

		public BindEvent(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) {
			super(ctx, promise);
			this.localAddress = localAddress;
		}

		public SocketAddress getLocalAddress() {
			return localAddress;
		}

		@Override
		public void execute() throws Exception {
			if (ctx != null)
				ctx.bind(localAddress, promise);
			super.execute();
		}
	}

	public class ConnectEvent extends ChannelPromiseEvent {

		private SocketAddress remoteAddress, localAddress;

		public ConnectEvent(String connection, Direction direction,
				long eventTime, long executionTime,
				SocketAddress remoteAddress, SocketAddress localAddress) {
			super(connection, direction, eventTime, executionTime);
			this.remoteAddress = remoteAddress;
			this.localAddress = localAddress;
		}

		public ConnectEvent(ChannelHandlerContext ctx,
				SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
			super(ctx, promise);
			this.remoteAddress = remoteAddress;
			this.localAddress = localAddress;
		}

		public SocketAddress getRemoteAddress() {
			return remoteAddress;
		}

		public SocketAddress getLocalAddress() {
			return localAddress;
		}

		@Override
		public void execute() throws Exception {
			if (ctx != null)
				ctx.connect(remoteAddress, localAddress, promise);
			super.execute();
		}
	}

	public class DisconnectEvent extends ChannelPromiseEvent {

		public DisconnectEvent(String connection, Direction direction,
				long eventTime, long executionTime) {
			super(connection, direction, eventTime, executionTime);
		}

		public DisconnectEvent(ChannelHandlerContext ctx, ChannelPromise promise) {
			super(ctx, promise);
		}

		@Override
		public void execute() throws Exception {
			if (ctx != null)
				ctx.disconnect(promise);
			super.execute();
		}
	}

	public class CloseEvent extends ChannelPromiseEvent {

		public CloseEvent(String connection, Direction direction,
				long eventTime, long executionTime) {
			super(connection, direction, eventTime, executionTime);
		}

		public CloseEvent(ChannelHandlerContext ctx, ChannelPromise promise) {
			super(ctx, promise);
		}

		@Override
		public void execute() throws Exception {
			if (ctx != null)
				ctx.close(promise);
			super.execute();
		}
	}

	public class DeregisterEvent extends ChannelPromiseEvent {

		public DeregisterEvent(String connection, Direction direction,
				long eventTime, long executionTime) {
			super(connection, direction, eventTime, executionTime);
		}

		public DeregisterEvent(ChannelHandlerContext ctx, ChannelPromise promise) {
			super(ctx, promise);
		}

		@Override
		public void execute() throws Exception {
			if (ctx != null)
				ctx.deregister(promise);
			super.execute();
		}
	}

	public class ReadEvent extends ChannelEvent {

		public ReadEvent(String connection, Direction direction,
				long eventTime, long executionTime) {
			super(connection, direction, eventTime, executionTime);
		}

		public ReadEvent(ChannelHandlerContext ctx) {
			super(ctx);
		}

		@Override
		public void execute() throws Exception {
			if (ctx != null)
				ctx.read();
			super.execute();
		}
	}

	public class ChannelRegisteredEvent extends ChannelEvent {

		public ChannelRegisteredEvent(String connection, Direction direction,
				long eventTime, long executionTime) {
			super(connection, direction, eventTime, executionTime);
		}

		public ChannelRegisteredEvent(ChannelHandlerContext ctx) {
			super(ctx);
		}

		@Override
		public void execute() throws Exception {
			if (ctx != null)
				ctx.fireChannelRegistered();
			super.execute();
		}
	}

	public class ChannelUnregisteredEvent extends ChannelEvent {

		public ChannelUnregisteredEvent(String connection, Direction direction,
				long eventTime, long executionTime) {
			super(connection, direction, eventTime, executionTime);
		}

		public ChannelUnregisteredEvent(ChannelHandlerContext ctx) {
			super(ctx);
		}

		@Override
		public void execute() throws Exception {
			if (ctx != null)
				ctx.fireChannelUnregistered();
			super.execute();
		}
	}

	public class FlushEvent extends ChannelEvent {

		public FlushEvent(String connection, Direction direction,
				long eventTime, long executionTime) {
			super(connection, direction, eventTime, executionTime);
		}

		public FlushEvent(ChannelHandlerContext ctx) {
			super(ctx);
		}

		@Override
		public void execute() throws Exception {
			if (ctx != null)
				ctx.flush();
			super.execute();
		}
	}
	
	public class ChannelWritabilityChangedEvent extends ChannelEvent {

		public ChannelWritabilityChangedEvent(String connection, Direction direction,
				long eventTime, long executionTime) {
			super(connection, direction, eventTime, executionTime);
		}

		public ChannelWritabilityChangedEvent(ChannelHandlerContext ctx) {
			super(ctx);
		}

		@Override
		public void execute() throws Exception {
			if (ctx != null)
				ctx.fireChannelWritabilityChanged();
			super.execute();
		}
	}

	public class ChannelReadCompleteEvent extends ChannelEvent {

		public ChannelReadCompleteEvent(String connection, Direction direction,
				long eventTime, long executionTime) {
			super(connection, direction, eventTime, executionTime);
		}

		public ChannelReadCompleteEvent(ChannelHandlerContext ctx) {
			super(ctx);
		}

		@Override
		public void execute() throws Exception {
			if (ctx != null)
				ctx.fireChannelReadComplete();
			super.execute();
		}
	}

}
