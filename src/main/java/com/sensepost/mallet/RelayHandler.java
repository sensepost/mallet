package com.sensepost.mallet;

import java.util.LinkedList;
import java.util.Queue;

import com.sensepost.mallet.graph.GraphLookup;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.ChannelOutputShutdownEvent;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

@Sharable
public class RelayHandler extends ChannelInboundHandlerAdapter {

	public static final AttributeKey<ChannelFuture> LAST_FUTURE = AttributeKey.valueOf("last_future");

	private static final InternalLogger logger = InternalLoggerFactory.getInstance(RelayHandler.class);

	private volatile boolean added = false;

	private InterceptController controller;

	private Queue<Object> queue = new LinkedList<>();

	private ChannelFuture connectFuture = null;

	public RelayHandler(InterceptController controller) {
		this.controller = controller;
	}

	@Override
	public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
		if (!added && ctx.channel().attr(ChannelAttributes.TARGET).get() != null) {
			added = true;
			setupOutboundChannel(ctx);
		}
	}

	private void setupOutboundChannel(final ChannelHandlerContext ctx) throws Exception {
		final GraphLookup gl = ctx.channel().attr(ChannelAttributes.GRAPH).get();
		final ConnectRequest target = ctx.channel().attr(ChannelAttributes.TARGET).get();

		ChannelInitializer<SocketChannel> initializer = new ChannelInitializer<SocketChannel>() {

			@Override
			protected void initChannel(SocketChannel ch) throws Exception {
				controller.linkChannels(ctx.channel().id().asLongText(), ch.id().asLongText(), ch.localAddress(),
						ch.remoteAddress());

				ch.attr(ChannelAttributes.GRAPH).set(gl);
				ch.attr(ChannelAttributes.CHANNEL).set(ctx.channel());
				ctx.channel().attr(ChannelAttributes.CHANNEL).set(ch);

				ChannelHandler[] handlers = gl.getClientChannelInitializer(RelayHandler.this);
				ch.pipeline().addLast(handlers);
			}
		};

		try {
			Bootstrap bootstrap = new Bootstrap().channel(NioSocketChannel.class)
					.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000).option(ChannelOption.SO_KEEPALIVE, true)
					.option(ChannelOption.ALLOW_HALF_CLOSURE, true);

			connectFuture = bootstrap.group(ctx.channel().eventLoop()).handler(initializer).connect(target.getTarget());
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
		System.out.println(ctx.channel().id() + " writable " + ctx.channel().isWritable());
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		closeBoth(ctx.channel());
		super.channelInactive(ctx);
	}

	private static void closeBoth(Channel channel) {
		if (channel.isOpen())
			channel.close();
		Channel other = channel.attr(ChannelAttributes.CHANNEL).get();
		if (other != null && other.isOpen())
			other.close();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		closeBoth(ctx.channel());
	}

	@Override
	public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
		if (!added && ctx.channel().attr(ChannelAttributes.TARGET).get() != null) {
			added = true;
			setupOutboundChannel(ctx);
		}
		if (connectFuture != null && !connectFuture.isDone()) {
			queue.add(msg);
			return;
		} else {
			Channel channel = ctx.channel().attr(ChannelAttributes.CHANNEL).get();
			if (channel == null) {
				throw new NullPointerException("Channel is null!");
			}
			ChannelFuture cf = channel.writeAndFlush(msg);
			cf.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
			channel.attr(LAST_FUTURE).set(cf);
		}
	}

	private ChannelFuture getLastFuture(Channel ch) {
		ChannelFuture cf = ch.attr(LAST_FUTURE).get();
		if (cf == null)
			cf = ch.newSucceededFuture();
		return cf;
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {

		if (evt instanceof ChannelInputShutdownEvent) {
			Channel other = ctx.channel().attr(ChannelAttributes.CHANNEL).get();
			if (other != null) {
				getLastFuture(other).addListener(ShutdownOutput.INSTANCE);
			}
		} else if (evt instanceof ChannelOutputShutdownEvent) {
			Channel other = ctx.channel().attr(ChannelAttributes.CHANNEL).get();
			if (other != null) {
				getLastFuture(other).addListener(ShutdownInput.INSTANCE);
			}
		} else if (evt instanceof ChannelInputShutdownReadComplete) {
			ctx.channel().config().setAutoRead(false);

			Channel other = ctx.channel().attr(ChannelAttributes.CHANNEL).get();
			if (other != null) {
				getLastFuture(other).addListener(CloseBoth.INSTANCE);
			}
		} else if (evt instanceof ConnectRequest && !added) {
			added = true;
			setupOutboundChannel(ctx);
		}
		super.userEventTriggered(ctx, evt);
	}

	private static class ShutdownOutput implements ChannelFutureListener {
		static ShutdownOutput INSTANCE = new ShutdownOutput();

		private ShutdownOutput() {
		}

		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			Channel ch = future.channel();
			if (future.isSuccess()) {
				if (ch instanceof SocketChannel) {
					SocketChannel sch = (SocketChannel) ch;
					if (!sch.isOutputShutdown()) {
						sch.shutdownOutput();
					} else {
						sch.close();
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

	private static class ShutdownInput implements ChannelFutureListener {
		static ShutdownInput INSTANCE = new ShutdownInput();

		private ShutdownInput() {
		}

		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			Channel ch = future.channel();
			if (future.isSuccess()) {
				if (ch instanceof SocketChannel) {
					SocketChannel sch = (SocketChannel) ch;
					if (!sch.isInputShutdown()) {
						sch.shutdownInput();
					} else {
						sch.close();
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

	private static class CloseBoth implements ChannelFutureListener {
		static CloseBoth INSTANCE = new CloseBoth();

		private CloseBoth() {
		}

		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			Channel ch = future.channel();
			closeBoth(ch);
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
