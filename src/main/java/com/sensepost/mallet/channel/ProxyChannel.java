package com.sensepost.mallet.channel;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.DuplexChannel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * Base class for {@link Channel} implementations that are used in an embedded
 * fashion.
 */
public class ProxyChannel extends AbstractChannel {

	public static final AttributeKey<Exception> SHORT_CIRCUIT = AttributeKey.valueOf("ProxyChannel.short_circuit_prevention");
	
	private static final AttributeKey<ProxyChannel> PROXY_CHANNEL = AttributeKey.valueOf("ProxyChannel");

	private static final ChannelHandler[] EMPTY_HANDLERS = new ChannelHandler[0];

	private static final InternalLogger logger = InternalLoggerFactory.getInstance(ProxyChannel.class);

	private Channel proxyChannel = null;

	public static ProxyChannel get(Channel channel) {
		return channel.attr(PROXY_CHANNEL).get();
	}

	/**
	 * Create a new instance with an {@link ProxyChannelId} and an empty pipeline.
	 */
	public ProxyChannel(Channel proxyChannel) {
		this(proxyChannel, EMPTY_HANDLERS);
	}

	/**
	 * Create a new instance with the pipeline initialized with the specified
	 * handlers.
	 *
	 * @param handlers the {@link ChannelHandler}s which will be add in the
	 *                 {@link ChannelPipeline}
	 */
	public ProxyChannel(Channel proxyChannel, ChannelHandler... handlers) {
		this(proxyChannel, proxyChannel.id(), handlers);
	}

	/**
	 * Create a new instance with the channel ID set to the given ID and the
	 * pipeline initialized with the specified handlers.
	 *
	 * @param channelId     the {@link ChannelId} that will be used to identify this
	 *                      channel
	 * @param handlers      the {@link ChannelHandler}s which will be add in the
	 *                      {@link ChannelPipeline}
	 */
	public ProxyChannel(Channel proxyChannel, ChannelId channelId, final ChannelHandler... handlers) {
		super(null, channelId);
		ObjectUtil.checkNotNull(proxyChannel, "proxyChannel");
		ObjectUtil.checkNotNull(handlers, "handlers");
		this.proxyChannel = proxyChannel;
		proxyChannel.attr(PROXY_CHANNEL).set(this);
		proxyChannel.pipeline().addLast(new ProxyChannelRedirectHandler(), new InboundEventForwarderHandler());
		proxyChannel.closeFuture().addListener(new CloseListener());
		pipeline().addLast(handlers);
	}

	private void setNotShortCircuit() {
		Exception prev = proxyChannel.attr(SHORT_CIRCUIT).get();
		if (prev != null)
			throw new IllegalStateException("Re-entering proxyChannel with SHORT CIRCUIT enabled by previous call", prev);
		StackTraceElement[] stackTrace = new Exception().getStackTrace();
		String method = stackTrace[1].getMethodName();
		proxyChannel.attr(SHORT_CIRCUIT).set(new Exception(method));
	}

	
	@Override
	public <T> Attribute<T> attr(AttributeKey<T> key) {
		return proxyChannel.attr(key);
	}

	@Override
	public <T> boolean hasAttr(AttributeKey<T> key) {
		return proxyChannel.hasAttr(key);
	}

	@Override
	public Channel parent() {
		return proxyChannel.parent();
	}

//	@Override
//	public Channel flush() {
//		setNotShortCircuit();
//		return super.flush();
//	}

//	@Override
//	public ChannelFuture writeAndFlush(Object msg) {
//		setNotShortCircuit();
//		ChannelFuture cf = super.write(msg);
//		flush();
//		return cf;
//	}
//
//	@Override
//	public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
//		setNotShortCircuit();
//		ChannelFuture cf = super.writeAndFlush(msg, promise);
//		flush();
//		return cf;
//	}
//
//	@Override
//	public ChannelFuture write(Object msg) {
//		setNotShortCircuit();
//		return super.write(msg);
//	}
//
//	@Override
//	public ChannelFuture write(Object msg, ChannelPromise promise) {
//		setNotShortCircuit();
//		return super.write(msg, promise);
//	}
//
	public Channel proxyChannel() {
		return proxyChannel;
	}

	/**
	 * Register this {@code Channel} on its {@link EventLoop}.
	 */
	public void register() throws Exception {
		ChannelFuture future = eventLoop().register(this);
		assert future.isDone();
		Throwable cause = future.cause();
		if (cause != null) {
			PlatformDependent.throwException(cause);
		}
	}

	@Override
	protected final DefaultChannelPipeline newChannelPipeline() {
		return new ProxyChannelPipeline(this);
	}

	@Override
	public ChannelMetadata metadata() {
		return proxyChannel.metadata();
	}

	@Override
	public ChannelConfig config() {
		return proxyChannel.config();
	}

	@Override
	public boolean isOpen() {
		return proxyChannel.isOpen();
	}

	@Override
	public boolean isActive() {
		return proxyChannel.isActive();
	}

    @Override
    public boolean isWritable() {
        return proxyChannel.isWritable();
    }

	@Override
	protected boolean isCompatible(EventLoop loop) {
		return true;
	}

	@Override
	protected SocketAddress localAddress0() {
		return proxyChannel.localAddress();
	}

	@Override
	protected SocketAddress remoteAddress0() {
		return proxyChannel.remoteAddress();
	}

	@Override
	protected void doBeginRead() throws Exception {
		setNotShortCircuit();
		proxyChannel.read();
	}
	
	@Override
	protected void doBind(SocketAddress localAddress) throws Exception {
		setNotShortCircuit();
		proxyChannel.bind(localAddress);
	}

	@Override
	protected void doClose() throws Exception {
		if (proxyChannel.isOpen()) {
			setNotShortCircuit();
			proxyChannel.close();
		} else {
			throw new ClosedChannelException();
		}
	}

	@Override
	protected void doDisconnect() throws Exception {
		if (proxyChannel.metadata().hasDisconnect()) {
			setNotShortCircuit();
			proxyChannel.disconnect();
		} else {
			setNotShortCircuit();
			proxyChannel.close();
		}
	}
	
	@Override
	protected void doRegister() throws Exception {
		// NOOP
	}

	@Override
	protected AbstractUnsafe newUnsafe() {
		return new ProxyUnsafe();
	}

//	@Override
//	public ProxyUnsafe unsafe() {
//		return (ProxyUnsafe) super.unsafe();
//	}

	@Override
	protected void doWrite(final ChannelOutboundBuffer in) {
		final Object msg = in.current();
		if (msg == null) {
			return;
		}

		ReferenceCountUtil.retain(msg);
		// FIXME: Flushing here seems incorrect, but it is the only way to 
		// get the promise to fire, so that we can deal with our own promise relay
		// via remove() or remove(cause)
		proxyChannel.eventLoop().execute(new Runnable() {
			@Override 
			public void run() {
				setNotShortCircuit();
				proxyChannel.write(msg).addListener(new ChannelFutureListener() {
					
					@Override
					public void operationComplete(ChannelFuture future) throws Exception {
						if (future.isSuccess()) {
							in.remove();
						} else if (future.cause() != null)
							in.remove(future.cause());
						eventLoop().execute(new Runnable() {
							public void run() {
								doWrite(in);
							}
						});
					}
	
				});
				setNotShortCircuit();
				proxyChannel.flush();
			}
		});
	}

	/**
	 * Called for each inbound message.
	 */
	protected void handleInboundMessage(Object msg) {
		try {
			logger.debug("Discarded inbound message {} that reached at the tail of the pipeline. "
					+ "Please check your pipeline configuration.", msg);
		} finally {
			ReferenceCountUtil.release(msg);
		}
	}

	protected void handleInboundException(Throwable cause) {
		try {
			logger.info("Discarded inbound exception {} that reached at the tail of the pipeline. "
					+ "Please check your pipeline configuration.", cause);
		} finally {
			ReferenceCountUtil.release(cause);
		}
	}

	protected void handleInboundUserEvent(Object evt) {
		try {
			logger.debug("Discarded inbound user event {} that reached at the tail of the pipeline. "
					+ "Please check your pipeline configuration.", evt);
		} finally {
			ReferenceCountUtil.release(evt);
		}
	}

	final class ProxyUnsafe extends AbstractUnsafe {

//		@Override
//		protected void flush0() {
//			setNotShortCircuit();
//			super.flush0();
//		}

		@Override
		public void connect(SocketAddress remoteAddress, SocketAddress localAddress, final ChannelPromise promise) {
			setNotShortCircuit();
			proxyChannel.connect(remoteAddress, localAddress).addListener(new PromiseRelay(promise));
		}

	}

	public static class DuplexProxyChannel extends ProxyChannel implements DuplexChannel {

		private DuplexChannel proxyChannel;
		
		public DuplexProxyChannel(DuplexChannel proxyChannel) {
			super(proxyChannel);
			this.proxyChannel = proxyChannel;
		}

		@Override
		public boolean isInputShutdown() {
			return proxyChannel.isInputShutdown();
		}

		@Override
		public ChannelFuture shutdownInput() {
			return proxyChannel.shutdownOutput();
		}

		@Override
		public ChannelFuture shutdownInput(ChannelPromise promise) {
			return proxyChannel.shutdownInput().addListener(new PromiseRelay(promise));
		}

		@Override
		public boolean isOutputShutdown() {
			return proxyChannel.isOutputShutdown();
		}

		@Override
		public ChannelFuture shutdownOutput() {
			return proxyChannel.shutdownOutput();
		}

		@Override
		public ChannelFuture shutdownOutput(ChannelPromise promise) {
			return proxyChannel.shutdownOutput().addListener(new PromiseRelay(promise));
		}

		@Override
		public boolean isShutdown() {
			return proxyChannel.isShutdown();
		}

		@Override
		public ChannelFuture shutdown() {
			return proxyChannel.shutdown();
		}

		@Override
		public ChannelFuture shutdown(ChannelPromise promise) {
			return proxyChannel.shutdown().addListener(new PromiseRelay(promise));
		}
		
	}
	private final class ProxyChannelPipeline extends DefaultChannelPipeline {
		ProxyChannelPipeline(ProxyChannel channel) {
			super(channel);
		}

		@Override
		protected void onUnhandledInboundException(Throwable cause) {
			handleInboundException(cause);
		}

		@Override
		protected void onUnhandledInboundMessage(Object msg) {
			handleInboundMessage(msg);
		}

		@Override
		protected void onUnhandledInboundUserEventTriggered(Object evt) {
			handleInboundUserEvent(evt);
		}
	}

	private class CloseListener implements ChannelFutureListener {

		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			if (ProxyChannel.this.isOpen())
				ProxyChannel.this.close();
		}

	}

	private class InboundEventForwarderHandler extends ChannelInboundHandlerAdapter {

		@Override
		public void channelRead(ChannelHandlerContext ctx, final Object msg) throws Exception {
			ProxyChannel.this.eventLoop().execute(new Runnable() {
				@Override
				public void run() {
					ProxyChannel.this.pipeline().fireChannelRead(msg);
				}
			});
		}

		@Override
		public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
			ProxyChannel.this.eventLoop().execute(new Runnable() {
				@Override
				public void run() {
					ProxyChannel.this.pipeline().fireChannelReadComplete();
				}
			});
		}

		@Override
		public void userEventTriggered(ChannelHandlerContext ctx, final Object evt) throws Exception {
			ProxyChannel.this.eventLoop().execute(new Runnable() {
				@Override
				public void run() {
					ProxyChannel.this.pipeline().fireUserEventTriggered(evt);
				}
			});
		}

		@Override
		public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
			ProxyChannel.this.eventLoop().execute(new Runnable() {
				@Override
				public void run() {
					ProxyChannel.this.pipeline().fireChannelWritabilityChanged();
				}
			});
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, final Throwable cause) throws Exception {
			ProxyChannel.this.eventLoop().execute(new Runnable() {
				@Override
				public void run() {
					ProxyChannel.this.pipeline().fireExceptionCaught(cause);
				}
			});
		}

	}

	private class ProxyChannelRedirectHandler extends ChannelOutboundHandlerAdapter {

		private String getMethodName(StackTraceElement[] stack) {
			return stack[1].getMethodName();
		}

		private boolean calledViaProxyChannel(ChannelHandlerContext ctx) {
			Throwable t = new Throwable();
			StackTraceElement[] stackTrace = t.getStackTrace();
			Exception prev = ctx.channel().attr(SHORT_CIRCUIT).get();
//			System.err.println((prev != null ? "+" + getMethodName(prev.getStackTrace()) : "-") + ": " + getMethodName(stackTrace));
			ctx.channel().attr(ProxyChannel.SHORT_CIRCUIT).set(null);
			return prev != null;
		}

		@Override
		public void bind(ChannelHandlerContext ctx, final SocketAddress localAddress, final ChannelPromise promise)
				throws Exception {
			if (calledViaProxyChannel(ctx))
				super.bind(ctx, localAddress, promise);
			else
				ProxyChannel.this.eventLoop().execute(new Runnable() {
					@Override
					public void run() {
						ProxyChannel.this.bind(localAddress).addListener(new PromiseRelay(promise));
					}
				});
		}

		@Override
		public void connect(ChannelHandlerContext ctx, final SocketAddress remoteAddress, final SocketAddress localAddress,
				final ChannelPromise promise) throws Exception {
			if (calledViaProxyChannel(ctx))
				super.connect(ctx, remoteAddress, localAddress, promise);
			else
				ProxyChannel.this.eventLoop().execute(new Runnable() {
					@Override
					public void run() {
						ProxyChannel.this.connect(remoteAddress, localAddress).addListener(new PromiseRelay(promise));
					}
				});
		}

		@Override
		public void disconnect(ChannelHandlerContext ctx, final ChannelPromise promise) throws Exception {
			if (calledViaProxyChannel(ctx))
				super.disconnect(ctx, promise);
			else
				ProxyChannel.this.eventLoop().execute(new Runnable() {
					@Override
					public void run() {
						ProxyChannel.this.disconnect().addListener(new PromiseRelay(promise));
					}
				});
		}

		@Override
		public void close(ChannelHandlerContext ctx, final ChannelPromise promise) throws Exception {
			if (calledViaProxyChannel(ctx))
				super.close(ctx, promise);
			else
				ProxyChannel.this.eventLoop().execute(new Runnable() {
					@Override
					public void run() {
						ProxyChannel.this.close().addListener(new PromiseRelay(promise));
					}
				});
		}

		@Override
		public void deregister(ChannelHandlerContext ctx, final ChannelPromise promise) throws Exception {
			if (calledViaProxyChannel(ctx))
				super.deregister(ctx, promise);
			else
				ProxyChannel.this.eventLoop().execute(new Runnable() {
					@Override
					public void run() {
						ProxyChannel.this.deregister().addListener(new PromiseRelay(promise));
					}
				});
		}

		@Override
		public void read(ChannelHandlerContext ctx) throws Exception {
			if (calledViaProxyChannel(ctx))
				super.read(ctx);
			else
				ProxyChannel.this.eventLoop().execute(new Runnable() {
					@Override
					public void run() {
						ProxyChannel.this.read();
					}
				});
		}

		@Override
		public void write(ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) throws Exception {
			if (calledViaProxyChannel(ctx))
				super.write(ctx, msg, promise);
			else
				ProxyChannel.this.eventLoop().execute(new Runnable() {
					@Override
					public void run() {
						ProxyChannel.this.write(msg).addListener(new PromiseRelay(promise));
					}
				});
		}

		@Override
		public void flush(ChannelHandlerContext ctx) throws Exception {
			if (calledViaProxyChannel(ctx))
				super.flush(ctx);
			else
				ProxyChannel.this.eventLoop().execute(new Runnable() {
					@Override
					public void run() {
						ProxyChannel.this.flush();
					}
				});
		}
		
	}
	
	private static class PromiseRelay implements ChannelFutureListener {
		private ChannelPromise promise;

		public PromiseRelay(ChannelPromise promise) {
			this.promise = promise;
		}

		@Override
		public void operationComplete(final ChannelFuture future) throws Exception {
			promise.channel().eventLoop().execute(new Runnable() {
				@Override
				public void run() {
					if (future.isSuccess()) {
						promise.setSuccess();
					} else if (future.isCancelled()) {
						promise.cancel(true);
					} else {
						promise.setFailure(future.cause());
						future.cause().printStackTrace();
					}
				}
			});
		}
	}
}
