package com.sensepost.mallet;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.SocketAddress;
import java.util.concurrent.RejectedExecutionException;

import com.sensepost.mallet.persistence.MessageDAO;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

public interface InterceptController {

    static final InternalLogger logger = InternalLoggerFactory.getInstance(InterceptController.class);

	void setMessageDAO(MessageDAO dao);

	void addChannel(String id, SocketAddress local, SocketAddress remote);

	void addChannelEvent(ChannelEvent evt) throws Exception;

	void linkChannels(String channel1, String channel2, SocketAddress localAddress2, SocketAddress remoteAddress2);

	public enum Direction {
		Client_Server, Server_Client
	}

	public enum State {
		PENDING, SENT, DROPPED
	}

	public abstract class ChannelEvent implements Runnable {

		protected ChannelHandlerContext ctx = null;
		private long eventTime, executionTime = -1;
		private String connection;
		private Direction direction;
		private State state;

		public ChannelEvent(String connection, Direction direction,
				State state, long eventTime, long executionTime) {
			this.connection = connection;
			this.direction = direction;
			this.state = state;
			this.eventTime = eventTime;
			this.executionTime = executionTime;
		}

		public ChannelEvent(ChannelHandlerContext ctx) {
			if (ctx == null)
				throw new NullPointerException("ctx");
			this.ctx = ctx;
			connection = ctx.channel().id().asLongText();
			direction = ctx.channel().parent() == null ? Direction.Server_Client
					: Direction.Client_Server;
			this.state = State.PENDING;
			this.eventTime = System.currentTimeMillis();
		}

		public ChannelHandlerContext context() {
			return ctx;
		}

		public void run() {
			if (isExecuted())
				throw new IllegalStateException("Already executed!");
			try {
				switch (state) {
				case SENT: execute(); break;
				case DROPPED: drop(); break;
				default: throw new IllegalStateException("run() should only be called via execute() or drop()");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
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

		public State getState() {
			return state;
		}

		protected abstract void execute0() throws Exception;

		public void execute() throws Exception {
			if (ctx == null)
				throw new IllegalStateException("Cannot execute with no context");
			state = State.SENT;
			EventExecutor executor = ctx.executor();
			if (executor.inEventLoop()) {
				executionTime = System.currentTimeMillis();
				execute0();
            } else {
                try {
                    executor.execute(this);
                } catch (RejectedExecutionException e) {
                    if (logger.isWarnEnabled()) {
                        logger.warn(
                                "Can't invoke execute() as the EventExecutor {} rejected it.",
                                executor, ctx.name(), e);
                    }
                }
			}
		}

		protected void drop0() throws Exception {
		}

		public void drop() throws Exception {
			if (ctx == null)
				throw new IllegalStateException("Cannot drop with no context");
			state = State.DROPPED;
			EventExecutor executor = ctx.executor();
			if (executor.inEventLoop()) {
				executionTime = System.currentTimeMillis();
				drop0();
            } else {
                try {
                    executor.execute(this);
                } catch (RejectedExecutionException e) {
                    if (logger.isWarnEnabled()) {
                        logger.warn(
                                "Can't invoke execute() as the EventExecutor {} rejected it.",
                                executor, ctx.name(), e);
                    }
                }
			}
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

		public ChannelActiveEvent(String connection, Direction direction, State state,
				long eventTime, long executionTime, SocketAddress remoteAddress, SocketAddress localAddress) {
			super(connection, direction, state, eventTime, executionTime);
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
		public void execute0() throws Exception {
			ctx.fireChannelActive();
		}
	}

	public class ChannelInactiveEvent extends ChannelEvent {
		public ChannelInactiveEvent(String connection, Direction direction, State state,
				long eventTime, long executionTime) {
			super(connection, direction, state, eventTime, executionTime);
		}

		public ChannelInactiveEvent(ChannelHandlerContext ctx) {
			super(ctx);
		}

		@Override
		public void execute0() throws Exception {
			ctx.fireChannelInactive();
		}
	}

	public class ExceptionCaughtEvent extends ChannelEvent {

		private Throwable cause;
		private String causeString;

		public ExceptionCaughtEvent(String connection, Direction direction, State state,
				long eventTime, long executionTime, String cause) {
			super(connection, direction, state, eventTime, executionTime);
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
		public void execute0() throws Exception {
			ctx.fireExceptionCaught(cause);
		}
	}

	public abstract class ChannelMessageEvent extends ChannelEvent {

		private Object msg;
		private MessageDAO dao = null;
		private String messageId = null;

		public ChannelMessageEvent(String connection, Direction direction, State state,
				long eventTime, long executionTime, MessageDAO dao,
				String messageId) {
			super(connection, direction, state, eventTime, executionTime);
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

		@Override
		public void execute() throws Exception {
			super.execute();
			if (messageId != null)
				msg = null;
		}
	}

	public class ChannelReadEvent extends ChannelMessageEvent {

		public ChannelReadEvent(String connection, Direction direction, State state,
				long eventTime, long executionTime, MessageDAO dao,
				String messageId) {
			super(connection, direction, state, eventTime, executionTime, dao,
					messageId);
		}

		public ChannelReadEvent(ChannelHandlerContext ctx, Object msg) {
			super(ctx, msg);
		}

		@Override
		public void execute0() throws Exception {
			ctx.fireChannelRead(getMessage());
		}

		@Override
		protected void drop0() throws Exception {
			ReferenceCountUtil.release(getMessage());
		}
	}

	public class WriteEvent extends ChannelMessageEvent {

		protected ChannelPromise promise;

		public WriteEvent(String connection, Direction direction, State state,
				long eventTime, long executionTime, MessageDAO dao,
				String messageId) {
			super(connection, direction, state, eventTime, executionTime, dao,
					messageId);
		}

		public WriteEvent(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
			super(ctx, msg);
			this.promise = promise;
		}

		@Override
		public void execute0() throws Exception {
			ctx.write(getMessage(), promise);
		}

	}

	public class UserEventTriggeredEvent extends ChannelEvent {

		private Object evt;

		public UserEventTriggeredEvent(String connection, Direction direction, State state,
				long eventTime, long executionTime, Object evt) {
			super(connection, direction, state, eventTime, executionTime);
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
		public void execute0() throws Exception {
			ctx.fireUserEventTriggered(getUserEvent());
		}

	}

	public abstract class ChannelPromiseEvent extends ChannelEvent {

		protected ChannelPromise promise;

		public ChannelPromiseEvent(String connection, Direction direction, State state,
				long eventTime, long executionTime) {
			super(connection, direction, state, eventTime, executionTime);
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

		public BindEvent(String connection, Direction direction, State state,
				long eventTime, long executionTime, SocketAddress localAddress) {
			super(connection, direction, state, eventTime, executionTime);
		}

		public BindEvent(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) {
			super(ctx, promise);
			this.localAddress = localAddress;
		}

		public SocketAddress getLocalAddress() {
			return localAddress;
		}

		@Override
		public void execute0() throws Exception {
			ctx.bind(localAddress, promise);
		}
	}

	public class ConnectEvent extends ChannelPromiseEvent {

		private SocketAddress remoteAddress, localAddress;

		public ConnectEvent(String connection, Direction direction, State state,
				long eventTime, long executionTime,
				SocketAddress remoteAddress, SocketAddress localAddress) {
			super(connection, direction, state, eventTime, executionTime);
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
		public void execute0() throws Exception {
			ctx.connect(remoteAddress, localAddress, promise);
		}
	}

	public class DisconnectEvent extends ChannelPromiseEvent {

		public DisconnectEvent(String connection, Direction direction, State state,
				long eventTime, long executionTime) {
			super(connection, direction, state, eventTime, executionTime);
		}

		public DisconnectEvent(ChannelHandlerContext ctx, ChannelPromise promise) {
			super(ctx, promise);
		}

		@Override
		public void execute0() throws Exception {
			ctx.disconnect(promise);
		}
	}

	public class CloseEvent extends ChannelPromiseEvent {

		public CloseEvent(String connection, Direction direction, State state,
				long eventTime, long executionTime) {
			super(connection, direction, state, eventTime, executionTime);
		}

		public CloseEvent(ChannelHandlerContext ctx, ChannelPromise promise) {
			super(ctx, promise);
		}

		@Override
		public void execute0() throws Exception {
			ctx.close(promise);
		}
	}

	public class DeregisterEvent extends ChannelPromiseEvent {

		public DeregisterEvent(String connection, Direction direction, State state,
				long eventTime, long executionTime) {
			super(connection, direction, state, eventTime, executionTime);
		}

		public DeregisterEvent(ChannelHandlerContext ctx, ChannelPromise promise) {
			super(ctx, promise);
		}

		@Override
		public void execute0() throws Exception {
			ctx.deregister(promise);
		}
	}

	public class ReadEvent extends ChannelEvent {

		public ReadEvent(String connection, Direction direction, State state,
				long eventTime, long executionTime) {
			super(connection, direction, state, eventTime, executionTime);
		}

		public ReadEvent(ChannelHandlerContext ctx) {
			super(ctx);
		}

		@Override
		public void execute0() throws Exception {
			ctx.read();
		}
	}

	public class ChannelRegisteredEvent extends ChannelEvent {

		public ChannelRegisteredEvent(String connection, Direction direction, State state,
				long eventTime, long executionTime) {
			super(connection, direction, state, eventTime, executionTime);
		}

		public ChannelRegisteredEvent(ChannelHandlerContext ctx) {
			super(ctx);
		}

		@Override
		public void execute0() throws Exception {
			ctx.fireChannelRegistered();
		}
	}

	public class ChannelUnregisteredEvent extends ChannelEvent {

		public ChannelUnregisteredEvent(String connection, Direction direction, State state,
				long eventTime, long executionTime) {
			super(connection, direction, state, eventTime, executionTime);
		}

		public ChannelUnregisteredEvent(ChannelHandlerContext ctx) {
			super(ctx);
		}

		@Override
		public void execute0() throws Exception {
			ctx.fireChannelUnregistered();
		}
	}

	public class FlushEvent extends ChannelEvent {

		public FlushEvent(String connection, Direction direction, State state,
				long eventTime, long executionTime) {
			super(connection, direction, state, eventTime, executionTime);
		}

		public FlushEvent(ChannelHandlerContext ctx) {
			super(ctx);
		}

		@Override
		public void execute0() throws Exception {
			ctx.flush();
		}
	}
	
	public class ChannelWritabilityChangedEvent extends ChannelEvent {

		public ChannelWritabilityChangedEvent(String connection, Direction direction, State state,
				long eventTime, long executionTime) {
			super(connection, direction, state, eventTime, executionTime);
		}

		public ChannelWritabilityChangedEvent(ChannelHandlerContext ctx) {
			super(ctx);
		}

		@Override
		public void execute0() throws Exception {
			ctx.fireChannelWritabilityChanged();
		}
	}

	public class ChannelReadCompleteEvent extends ChannelEvent {

		public ChannelReadCompleteEvent(String connection, Direction direction, State state,
				long eventTime, long executionTime) {
			super(connection, direction, state, eventTime, executionTime);
		}

		public ChannelReadCompleteEvent(ChannelHandlerContext ctx) {
			super(ctx);
		}

		@Override
		public void execute0() throws Exception {
			ctx.fireChannelReadComplete();
		}
	}

}
