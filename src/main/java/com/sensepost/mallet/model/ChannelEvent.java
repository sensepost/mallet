package com.sensepost.mallet.model;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.SocketAddress;
import java.util.Date;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;

/**
 * Represents an event that passes through the Channel
 * @author rogan
 *
 */
public interface ChannelEvent {
	
	public enum ChannelEventType {
		BIND,
		CHANNEL_ACTIVE,
		CHANNEL_INACTIVE,
		CHANNEL_READ_COMPLETE,
		CHANNEL_READ,
		CHANNEL_REGISTERED,
		CHANNEL_UNREGISTERED,
		CHANNEL_WRITABILITY_CHANGED,
		CLOSE,
		CONNECT,
		DEREGISTER,
		DISCONNECT,
		EXCEPTION_CAUGHT,
		FLUSH,
		READ,
		USER_EVENT_TRIGGERED,
		WRITE,
	}

	public enum EventState {
		PENDING,
		EXECUTED,
		DROPPED
	}

	ChannelEventType type();

	ChannelHandlerContext context();

	String channelId();

	Date eventTime();

	EventState state();

	void execute();

	void drop();

	default boolean isExecuted() {
		return state() != EventState.PENDING;
	}

	Date executionTime();

	default String description() {
		return type().toString();
	}

	public interface BindEvent extends ChannelEvent {
		String localAddress();
	}
	
	public interface ConnectEvent extends ChannelEvent {
		String addresses();
	}

	public interface ExceptionCaughtEvent extends ChannelEvent {
		String cause();
	}

	public interface ChannelMessageEvent extends ChannelEvent {
		void setMessage(Object msg);
		Object getMessage();
	}

	public interface UserEventTriggeredEvent extends ChannelEvent {
		Object userEvent();
	}

	public static BindEvent newBindEvent(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) {
		return new DefaultBindEvent(ctx, localAddress, promise);
	}
	
	public static BindEvent newBindEvent(String channelId, EventState state, Date eventTime, Date executionTime, String localAddress) {
		return new DefaultBindEvent(channelId, state, eventTime, executionTime, localAddress);
	}

	public static ChannelEvent newChannelActiveEvent(ChannelHandlerContext ctx) {
		return new DefaultChannelEvent(ChannelEventType.CHANNEL_ACTIVE, ctx, null);
	}

	public static ChannelEvent newChannelActiveEvent(String channelId, EventState state, Date eventTime, Date executionTime) {
		return new DefaultChannelEvent(ChannelEventType.CHANNEL_ACTIVE, channelId, state, eventTime, executionTime);
	}

	public static ChannelEvent newChannelInactiveEvent(ChannelHandlerContext ctx) {
		return new DefaultChannelEvent(ChannelEventType.CHANNEL_INACTIVE, ctx, null);
	}

	public static ChannelEvent newChannelInactiveEvent(String channelId, EventState state, Date eventTime, Date executionTime) {
		return new DefaultChannelEvent(ChannelEventType.CHANNEL_INACTIVE, channelId, state, eventTime, executionTime);
	}

	public static ChannelMessageEvent newChannelReadEvent(ChannelHandlerContext ctx, Object msg) {
		return new DefaultMessageEvent(ChannelEventType.CHANNEL_READ, ctx, null, msg);
	}

	public static ChannelMessageEvent newChannelReadEvent(String channelId, EventState state, Date eventTime, Date executionTime, Object msg) {
		return new DefaultMessageEvent(ChannelEventType.CHANNEL_READ, channelId, state, eventTime, executionTime, msg);
	}

	public static ChannelEvent newChannelReadCompleteEvent(ChannelHandlerContext ctx) {
		return new DefaultChannelEvent(ChannelEventType.CHANNEL_READ_COMPLETE, ctx, null);
	}

	public static ChannelEvent newChannelReadCompleteEvent(String channelId, EventState state, Date eventTime, Date executionTime) {
		return new DefaultChannelEvent(ChannelEventType.CHANNEL_READ_COMPLETE, channelId, state, eventTime, executionTime);
	}

	public static ChannelEvent newChannelRegisteredEvent(ChannelHandlerContext ctx) {
		return new DefaultChannelEvent(ChannelEventType.CHANNEL_REGISTERED, ctx, null);
	}

	public static ChannelEvent newChannelRegisteredEvent(String channelId, EventState state, Date eventTime, Date executionTime) {
		return new DefaultChannelEvent(ChannelEventType.CHANNEL_REGISTERED, channelId, state, eventTime, executionTime);
	}

	public static ChannelEvent newChannelUnregisteredEvent(ChannelHandlerContext ctx) {
		return new DefaultChannelEvent(ChannelEventType.CHANNEL_UNREGISTERED, ctx, null);
	}

	public static ChannelEvent newChannelUnregisteredEvent(String channelId, EventState state, Date eventTime, Date executionTime) {
		return new DefaultChannelEvent(ChannelEventType.CHANNEL_UNREGISTERED, channelId, state, eventTime, executionTime);
	}

	public static ChannelEvent newChannelWritabilityChangedEvent(ChannelHandlerContext ctx) {
		return new DefaultChannelEvent(ChannelEventType.CHANNEL_WRITABILITY_CHANGED, ctx, null);
	}

	public static ChannelEvent newChannelWritabilityChangedEvent(String channelId, EventState state, Date eventTime, Date executionTime) {
		return new DefaultChannelEvent(ChannelEventType.CHANNEL_WRITABILITY_CHANGED, channelId, state, eventTime, executionTime);
	}

	public static ChannelEvent newCloseEvent(ChannelHandlerContext ctx, ChannelPromise promise) {
		return new DefaultChannelEvent(ChannelEventType.CLOSE, ctx, promise);
	}

	public static ChannelEvent newCloseEvent(String channelId, EventState state, Date eventTime, Date executionTime) {
		return new DefaultChannelEvent(ChannelEventType.CLOSE, channelId, state, eventTime, executionTime);
	}

	public static ConnectEvent newConnectEvent(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
		return new DefaultConnectEvent(ctx, remoteAddress, localAddress, promise);
	}

	public static ConnectEvent newConnectEvent(String channelId, EventState state, Date eventTime, Date executionTime, String addresses) {
		return new DefaultConnectEvent(channelId, state, eventTime, executionTime, addresses);
	}

	public static ChannelEvent newDeregisterEvent(ChannelHandlerContext ctx, ChannelPromise promise) {
		return new DefaultChannelEvent(ChannelEventType.DEREGISTER, ctx, promise);
	}

	public static ChannelEvent newDeregisterEvent(String channelId, EventState state, Date eventTime, Date executionTime) {
		return new DefaultChannelEvent(ChannelEventType.DEREGISTER, channelId, state, eventTime, executionTime);
	}

	public static ChannelEvent newDisconnectEvent(ChannelHandlerContext ctx, ChannelPromise promise) {
		return new DefaultChannelEvent(ChannelEventType.DISCONNECT, ctx, promise);
	}

	public static ChannelEvent newDisconnectEvent(String channelId, EventState state, Date eventTime, Date executionTime) {
		return new DefaultChannelEvent(ChannelEventType.DISCONNECT, channelId, state, eventTime, executionTime);
	}

	public static ChannelEvent newExceptionCaughtEvent(ChannelHandlerContext ctx, Throwable cause) {
		return new DefaultExceptionCaughtEvent(ctx, cause);
	}

	public static ChannelEvent newExceptionCaughtEvent(String channelId, EventState state, Date eventTime, Date executionTime, String cause) {
		return new DefaultExceptionCaughtEvent(channelId, state, eventTime, executionTime, cause);
	}

	public static ChannelEvent newFlushEvent(ChannelHandlerContext ctx) {
		return new DefaultChannelEvent(ChannelEventType.FLUSH, ctx, null);
	}

	public static ChannelEvent newFlushEvent(String channelId, EventState state, Date eventTime, Date executionTime) {
		return new DefaultChannelEvent(ChannelEventType.FLUSH, channelId, state, eventTime, executionTime);
	}

	public static ChannelEvent newReadEvent(ChannelHandlerContext ctx) {
		return new DefaultChannelEvent(ChannelEventType.READ, ctx, null);
	}

	public static ChannelEvent newReadEvent(String channelId, EventState state, Date eventTime, Date executionTime) {
		return new DefaultChannelEvent(ChannelEventType.READ, channelId, state, eventTime, executionTime);
	}

	public static UserEventTriggeredEvent newUserEventTriggeredEvent(ChannelHandlerContext ctx, Object evt) {
		return new DefaultUserEventTriggeredEvent(ctx, evt);
	}
	
	public static UserEventTriggeredEvent newUserEventTriggeredEvent(String channelId, EventState state, Date eventTime, Date executionTime, String evt) {
		return new DefaultUserEventTriggeredEvent(channelId, state, eventTime, executionTime, evt);
	}

	public static ChannelMessageEvent newWriteEvent(ChannelHandlerContext ctx, ChannelPromise promise, Object msg) {
		return new DefaultMessageEvent(ChannelEventType.WRITE, ctx, promise, msg);
	}

	public static ChannelMessageEvent newWriteEvent(String channelId, EventState state, Date eventTime, Date executionTime, Object msg) {
		return new DefaultMessageEvent(ChannelEventType.WRITE, channelId, state, eventTime, executionTime, msg);
	}

	class DefaultChannelEvent extends BaseEntity implements ChannelEvent {
		final private ChannelEventType type;
		private ChannelHandlerContext ctx;
		final private String channelId;
		private EventState state;
		private final Date eventTime;
		private Date executionTime = null;
		private final ChannelPromise promise;
		
		DefaultChannelEvent(ChannelEventType type, String channelId, EventState state, Date eventTime, Date executionTime) {
			if (type == null)
				throw new NullPointerException("type");
			this.type = type;
			if (channelId == null)
				throw new NullPointerException("channelId");
			this.channelId = channelId;
			if (state == null)
				throw new NullPointerException("state");
			if (state.equals(EventState.PENDING))
				throw new IllegalArgumentException("Cannot construct an Event with a PENDING state");
			this.state = state;
			if (eventTime == null)
				throw new NullPointerException("eventTime");
			this.eventTime = eventTime;
			if (executionTime == null)
				throw new NullPointerException("executionTime");
			this.executionTime = executionTime;
			this.promise = null;
		}

		DefaultChannelEvent(ChannelEventType type, ChannelHandlerContext ctx, ChannelPromise promise) {
			if (type == null)
				throw new NullPointerException("type");
			this.type = type;
			if (ctx == null)
				throw new NullPointerException("ctx");
			this.ctx = ctx;
			this.state = EventState.PENDING;
			this.promise = promise;
			this.channelId = ctx.channel().id().asLongText();
			this.eventTime = new Date();
		}

		protected void ensureNotExecuted() {
			if (isExecuted())
				throw new IllegalStateException("Event is already executed!");
		}

		@Override
		public ChannelHandlerContext context() {
			return ctx;
		}

		@Override
		public ChannelEventType type() {
			return type;
		}

		ChannelPromise promise() {
			return promise;
		}

		@Override
		public EventState state() {
			return state;
		}

		protected void setState(EventState state) {
			this.state = state;
			executionTime = new Date();
		}

		@Override
		public String channelId() {
			return channelId;
		}

		@Override
		public Date eventTime() {
			return eventTime;
		}

		@Override
		public Date executionTime() {
			return executionTime;
		}

		@Override
		public void execute() {
			ensureNotExecuted();
			switch (type()) {
			case CHANNEL_ACTIVE:
				context().fireChannelActive();
				break;
			case CHANNEL_INACTIVE:
				context().fireChannelInactive();
				break;
			case CHANNEL_READ:
				context().read();
				break;
			case CHANNEL_READ_COMPLETE:
				context().fireChannelReadComplete();
				break;
			case CHANNEL_REGISTERED:
				context().fireChannelRegistered();
				break;
			case CHANNEL_UNREGISTERED:
				context().fireChannelUnregistered();
				break;
			case CHANNEL_WRITABILITY_CHANGED:
				context().fireChannelWritabilityChanged();
				break;
			case CLOSE:
				context().close(promise());
				break;
			case DEREGISTER:
				context().deregister(promise());
				break;
			case DISCONNECT:
				context().disconnect(promise());
				break;
			case FLUSH:
				context().flush();
				break;
			case READ:
				context().read();
				break;
			default:
				throw new UnsupportedOperationException("Don't know how to execute " + type());
			}
			setState(EventState.EXECUTED);
		}

		@Override
		public void drop() {
			ensureNotExecuted();
			switch (type()) {
			case CHANNEL_ACTIVE:
			case CHANNEL_INACTIVE:
			case CHANNEL_READ:
			case CHANNEL_READ_COMPLETE:
			case CHANNEL_REGISTERED:
			case CHANNEL_UNREGISTERED:
			case CHANNEL_WRITABILITY_CHANGED:
			case CLOSE:
			case DEREGISTER:
			case DISCONNECT:
			case FLUSH:
			case READ:
				break;
			default:
				throw new UnsupportedOperationException("Don't know how to drop " + type());
			}
			setState(EventState.DROPPED);
		}
		
		@Override
		public String toString() {
			return channelId() + "(" + type() + ": " + description() + ")";
		}
	}
	
	class DefaultMessageEvent extends DefaultChannelEvent implements ChannelMessageEvent {

		private Object msg;
		
		DefaultMessageEvent(ChannelEventType type, String channelId, EventState state, Date eventTime, Date executionTime, Object msg) {
			super(type, channelId, state, eventTime, executionTime);
			this.msg = msg;
		}

		DefaultMessageEvent(ChannelEventType type, ChannelHandlerContext ctx, ChannelPromise promise, Object msg) {
			super(type, ctx, promise);
			this.msg = msg;
		}

		@Override
		public void setMessage(Object msg) {
			ensureNotExecuted();
			ReferenceCountUtil.retain(msg);
			this.msg = msg;
		}

		@Override
		public Object getMessage() {
			ReferenceCountUtil.retain(msg);
			return msg;
		}
		
		@Override
		public void execute() {
			ensureNotExecuted();
			switch (type()) {
			case CHANNEL_READ:
				context().fireChannelRead(getMessage());
				break;
			case WRITE:
				context().write(getMessage(), promise());
				break;
			default:
				throw new UnsupportedOperationException("MessageEvent can only be a CHANNEL_READ or WRITE, got " + type());	
			}
			setState(EventState.EXECUTED);
		}

		@Override
		public void drop() {
			ensureNotExecuted();
			switch (type()) {
			case CHANNEL_READ:
				ReferenceCountUtil.release(getMessage());
				break;
			case WRITE:
				ReferenceCountUtil.release(getMessage());
				promise().trySuccess();
				break;
			default:
				throw new UnsupportedOperationException("MessageEvent can only be a CHANNEL_READ or WRITE, got " + type());	
			}
			setState(EventState.DROPPED);
		}
		
		@Override
		public String description() {
			return getMessage() == null ? "null" : getMessage().getClass().getName();
		}
	}
	
	class DefaultBindEvent extends DefaultChannelEvent implements BindEvent {

		private final SocketAddress localAddress;
		private final String localAddressString;

		DefaultBindEvent(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) {
			super(ChannelEventType.BIND, ctx, promise);
			this.localAddress = localAddress;
			this.localAddressString = localAddress.toString();
		}

		public DefaultBindEvent(String channelId, EventState state, Date eventTime, Date executionTime, String localAddress) {
			super(ChannelEventType.BIND, channelId, state, eventTime, executionTime);
			this.localAddress = null;
			this.localAddressString = localAddress;
		}

		@Override
		public String localAddress() {
			return localAddressString;
		}

		@Override
		public void execute() {
			ensureNotExecuted();
			context().bind(localAddress, promise());
			setState(EventState.EXECUTED);
		}

		@Override
		public void drop() {
			ensureNotExecuted();
			promise().trySuccess();
			setState(EventState.DROPPED);
		}

		@Override
		public String description() {
			return super.description() + ": " + localAddressString;
		}
	}
	
	class DefaultConnectEvent extends DefaultChannelEvent implements ConnectEvent {

		private final SocketAddress remoteAddress, localAddress;
		private final String addresses;

		DefaultConnectEvent(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
			super(ChannelEventType.CONNECT, ctx, promise);
			this.remoteAddress = remoteAddress;
			this.localAddress = localAddress;
			this.addresses = (localAddress != null ? localAddress.toString() : "") + " -> " + remoteAddress.toString();
		}

		public DefaultConnectEvent(String channelId, EventState state, Date eventTime, Date executionTime, String addresses) {
			super(ChannelEventType.CONNECT, channelId, state, eventTime, executionTime);
			this.remoteAddress = this.localAddress = null;
			this.addresses = addresses;
		}

		@Override
		public String addresses() {
			return addresses;
		}

		@Override
		public void execute() {
			ensureNotExecuted();
			context().connect(remoteAddress, localAddress, promise());
			setState(EventState.EXECUTED);
		}

		@Override
		public void drop() {
			ensureNotExecuted();
			promise().trySuccess();
			setState(EventState.DROPPED);
		}

		@Override
		public String description() {
			return super.description() + ": " + addresses;
		}
	}

	class DefaultExceptionCaughtEvent extends DefaultChannelEvent implements ExceptionCaughtEvent {

		private final String causeString;
		private final Throwable cause;

		DefaultExceptionCaughtEvent(String channelId, EventState state, Date eventTime,
				Date executionTime, String cause) {
			super(ChannelEventType.EXCEPTION_CAUGHT, channelId, state, eventTime, executionTime);
			this.cause = null;
			this.causeString = cause;
		}

		DefaultExceptionCaughtEvent(ChannelHandlerContext ctx, Throwable cause) {
			super(ChannelEventType.EXCEPTION_CAUGHT, ctx, null);
			this.cause = cause;
			this.causeString = throwableToString(cause);
		}

		private String throwableToString(Throwable cause) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			cause.printStackTrace(pw);
			return sw.toString();
		}

		@Override
		public String cause() {
			return causeString;
		}

		@Override
		public void execute() {
			ensureNotExecuted();
			if (!context().isRemoved()) {
				context().fireExceptionCaught(cause);
				setState(EventState.EXECUTED);
			} else {
				drop();
			}
		}

		@Override
		public void drop() {
			ensureNotExecuted();
			setState(EventState.DROPPED);
		}

		@Override
		public String description() {
			return super.description() + ": " + firstLine(causeString);
		}
		
		private String firstLine(String s) {
			int cr = s.indexOf('\n');
			if (cr > 0)
				return s.substring(0, cr);
			return s;
		}
	}

	class DefaultUserEventTriggeredEvent extends DefaultChannelEvent implements UserEventTriggeredEvent {
		
		private final Object evt;
		private final String evtString;

		DefaultUserEventTriggeredEvent(String channelId, EventState state, Date eventTime,
				Date executionTime, String evt) {
			super(ChannelEventType.USER_EVENT_TRIGGERED, channelId, state, eventTime, executionTime);
			this.evt = null;
			this.evtString = evt;
		}

		DefaultUserEventTriggeredEvent(ChannelHandlerContext ctx, Object evt) {
			super(ChannelEventType.USER_EVENT_TRIGGERED, ctx, null);
			this.evt = evt;
			this.evtString = evt.toString();
		}

		@Override
		public Object userEvent() {
			return evt != null ? evt : evtString;
		}

		@Override
		public void execute() {
			ensureNotExecuted();
			context().fireUserEventTriggered(evt);
			setState(EventState.EXECUTED);
		}

		@Override
		public void drop() {
			ensureNotExecuted();
			setState(EventState.DROPPED);
		}

		@Override
		public String description() {
			return super.description() + ": " + evtString;
		}
	}
}
