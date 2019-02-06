package com.sensepost.mallet;

import java.util.LinkedList;
import java.util.Queue;

import com.sensepost.mallet.InterceptController.ExceptionCaughtEvent;
import com.sensepost.mallet.channel.ProxyChannel;
import com.sensepost.mallet.channel.SubChannel;
import com.sensepost.mallet.graph.GraphLookup;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.ChannelOutputShutdownEvent;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DuplexChannel;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

@Sharable
public class RelayHandler extends ChannelInboundHandlerAdapter {

	public static final AttributeKey<ChannelFuture> LAST_FUTURE = AttributeKey.valueOf("last_future");

	private static final InternalLogger logger = InternalLoggerFactory.getInstance(RelayHandler.class);

	private ChannelHandlerContext ctx1 = null, ctx2 = null;

	private InterceptController controller;

	private Class<Channel> outboundChannelClass;

	private Queue<Object> queue = new LinkedList<>();

	private ChannelFuture connectFuture = null;

	public RelayHandler(InterceptController controller) {
		this.controller = controller;
	}

	public RelayHandler(InterceptController controller, Class<Channel> outboundChannelClass) {
		this.controller = controller;
		this.outboundChannelClass = outboundChannelClass;
	}

	@Override
	public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
		if (ctx1 == null) {
			ctx1 = ctx;
			if (ctx.channel().attr(ChannelAttributes.TARGET).get() != null) {
				setupOutboundChannel(ctx);
			}
		} else if (ctx2 == null) {
			ctx2 = ctx;
		} else
			throw new IllegalStateException("RelayHandler may only be added to two Channels!");
	}

	private ChannelHandlerContext other(ChannelHandlerContext ctx) {
		if (ctx == ctx1)
			return ctx2;
		else
			return ctx1;
	}

	private EventLoopGroup getOutboundEventLoop(Class<Channel> outboundChannelClass, Channel inbound) {
		if (inbound instanceof ProxyChannel)
			inbound = ((ProxyChannel)inbound).proxyChannel();
		if (inbound instanceof SubChannel)
			inbound = inbound.parent();
		if (outboundChannelClass.equals(inbound.getClass()))
			return inbound.eventLoop();
		throw new UnsupportedOperationException("Don't know how to find the event loop for a " + outboundChannelClass);
	}

	@SuppressWarnings("unchecked")
	private Class<Channel> getOutboundChannelClass(Channel inbound) {
		if (outboundChannelClass != null)
			return outboundChannelClass;
		else {
			if (inbound instanceof ProxyChannel)
				inbound = ((ProxyChannel)inbound).proxyChannel();
			else if (inbound instanceof SubChannel)
				inbound = inbound.parent();
			return (Class<Channel>)inbound.getClass();
		}
	}

	
	private void setupOutboundChannel(final ChannelHandlerContext ctx) throws Exception {
		if (connectFuture != null)
			return;

		// disable autoread until the connection is established
		ctx.channel().config().setAutoRead(false);
		final GraphLookup gl = ctx.channel().attr(ChannelAttributes.GRAPH).get();
		final ConnectRequest target = ctx.channel().attr(ChannelAttributes.TARGET).get();

		ChannelInitializer<Channel> initializer = new ChannelInitializer<Channel>() {

			@Override
			protected void initChannel(Channel ch) throws Exception {
				controller.linkChannels(ctx.channel().id().asLongText(), ch.id().asLongText(), ch.localAddress(),
						target.getTarget());

				ch.attr(ChannelAttributes.GRAPH).set(gl);
				ch.attr(ChannelAttributes.CHANNEL).set(ctx.channel());
				ctx.channel().attr(ChannelAttributes.CHANNEL).set(ch);

				ChannelInitializer<Channel> initializer = gl.getClientChannelInitializer(RelayHandler.this);
				ch.pipeline().addLast(initializer);
			}
		};

		try {
			Bootstrap bootstrap = new Bootstrap();
			Class<Channel> outboundChannelClass = getOutboundChannelClass(ctx.channel());
			bootstrap.channel(outboundChannelClass);
			bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000).option(ChannelOption.SO_KEEPALIVE, true)
					.option(ChannelOption.ALLOW_HALF_CLOSURE, true);

			EventLoopGroup outboundEventLoop = getOutboundEventLoop(outboundChannelClass, ctx.channel());
			bootstrap.group(outboundEventLoop).handler(initializer);
			if (DatagramChannel.class.isAssignableFrom(outboundChannelClass)) {
				connectFuture = bootstrap.bind(0);
			} else {
				connectFuture = bootstrap.connect(target.getTarget());
			}
			connectFuture.addListener(new ConnectRequestPromiseExecutor(target.getConnectPromise()));
			connectFuture.addListener(new ChannelFutureListener() {

				@Override
				public void operationComplete(ChannelFuture future) throws Exception {
					if (future.isSuccess()) {
						while (!queue.isEmpty()) {
							ChannelFuture cf = future.channel().writeAndFlush(queue.remove());
							cf.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
							future.channel().attr(LAST_FUTURE).set(cf);
						}
						// re-enable autoread now that the connection is established
						ctx.channel().config().setAutoRead(true);
					}
				}
			});
			connectFuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
		} catch (Exception e) {
			logger.error("Failed connecting to " + ctx.channel().remoteAddress() + " -> " + target, e);
			ctx.close();
		}
	}

	@Override
	public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
		ChannelHandlerContext other = other(ctx);
		if (other == null) 
			return;
		other.channel().config().setAutoRead(ctx.channel().isWritable());
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		closeBoth(ctx);
	}

	private void closeBoth(ChannelHandlerContext ctx) {
		if (ctx.channel().isOpen())
			ctx.channel().close();
		ChannelHandlerContext other = other(ctx);
		if (other.channel().isOpen())
			other.channel().close();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		ExceptionCaughtEvent evt = new ExceptionCaughtEvent(ctx, cause) {
			@Override
			public void execute0() {
				// do nothing
			}
		};
		controller.addChannelEvent(evt);
		closeBoth(ctx);
	}

	@Override
	public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
		if (connectFuture == null) {
			if (ctx.channel().attr(ChannelAttributes.TARGET).get() != null) {
				setupOutboundChannel(ctx);
			} else {
				throw new NullPointerException("No connected channel! Did you forget to use a SocksInitializer or a FixedTargetHandler?");
			}
		}
		if (!connectFuture.isDone()) {
			queue.add(msg);
			return;
		} else {
			// FIXME: To turn this into a functional "generic relay"
			// we need to be able to address Datagram packets to their destination
			// and match replies to their original sender(s).
			ChannelHandlerContext other = other(ctx);
			ChannelFuture cf = other.writeAndFlush(msg);
			cf.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
			other.channel().attr(LAST_FUTURE).set(cf);
		}
	}

	private ChannelFuture getLastFuture(ChannelHandlerContext ctx) {
		ChannelFuture cf = ctx.channel().attr(LAST_FUTURE).get();
		if (cf == null)
			cf = ctx.newSucceededFuture();
		return cf;
	}

	private void addOtherChannelFutureListener(ChannelHandlerContext ctx, ChannelFutureListener listener) {
		ChannelHandlerContext other = other(ctx);
		if (other == null)
			return;
		getLastFuture(other).addListener(listener);
	}

	private void closeIfShutdown(Channel ch) {
		if (ch instanceof DuplexChannel) {
			DuplexChannel dch = (DuplexChannel) ch;
			if (dch.isShutdown())
				dch.close();
		}
	}
	
	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof ChannelInputShutdownEvent) {
			// FIXME: If the InputShutdownEvent arrives before the queue
			// of pending messages has been flushed (or even before the outbound
			// channel has been established), the ShutdownOutput instance
			// cannot be sent because the LastFuture on the channel is null!
			// We work around this by disabling channel autoread while we set
			// up the outbound channel
			addOtherChannelFutureListener(ctx, ShutdownOutput.INSTANCE);
			closeIfShutdown(ctx.channel());
		} else if (evt instanceof ChannelOutputShutdownEvent) {
			closeIfShutdown(ctx.channel());
		} else if (evt instanceof ChannelInputShutdownReadComplete) {
			ctx.channel().config().setAutoRead(false);
			closeIfShutdown(ctx.channel());
		} else if (evt instanceof ConnectRequest) {
			setupOutboundChannel(ctx);
		}
	}

	@Override
	public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
		// Do not forward, this is the end of the road
	}

	@Override
	public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
		// Do not forward, this is the end of the road
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		// Do not forward, this is the end of the road
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		// Do not forward, this is the end of the road
	}

	private static class ShutdownOutput implements ChannelFutureListener {
		static ShutdownOutput INSTANCE = new ShutdownOutput();

		private ShutdownOutput() {
		}

		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			Channel ch = future.channel();
			if (future.isSuccess()) {
				if (ch instanceof DuplexChannel) {
					DuplexChannel dch = (DuplexChannel) ch;
					if (!dch.isOutputShutdown()) {
						dch.shutdownOutput();
					}
				} else if (ch.isOpen()) {
					ch.close();
				}
			} else {
				if (ch.isOpen())
					ch.close();
			}
		}

	}

	private class ConnectRequestPromiseExecutor implements ChannelFutureListener {
		private Promise<Channel> promise;

		public ConnectRequestPromiseExecutor(Promise<Channel> promise) {
			this.promise = promise;
		}

		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			if (!promise.isDone()) {
				if (future.isSuccess()) {
					promise.setSuccess(future.channel());
				} else {
					promise.setFailure(future.cause());
				}
			}
		}
	}
}
