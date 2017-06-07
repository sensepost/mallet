package com.sensepost.mallet;

import java.net.SocketAddress;

import com.sensepost.mallet.events.ChannelActiveEvent;
import com.sensepost.mallet.events.ChannelEvent;
import com.sensepost.mallet.events.ChannelEvent.Direction;
import com.sensepost.mallet.events.ChannelInactiveEvent;
import com.sensepost.mallet.events.ChannelReadEvent;
import com.sensepost.mallet.events.ChannelUserEvent;
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
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

@Sharable
public class InterceptHandler extends ChannelInboundHandlerAdapter {

	private InterceptController controller;

	private Bootstrap b = new Bootstrap();
	private EventLoopGroup eventLoop = new NioEventLoopGroup();
	private ChannelPromise promise = null;

	public InterceptHandler(InterceptController controller) {
		if (controller == null)
			throw new NullPointerException("controller");
		this.controller = controller;

		b.group(eventLoop).channel(NioSocketChannel.class).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
				.option(ChannelOption.SO_KEEPALIVE, true);
	}

	private void setupOutboundChannel(final ChannelHandlerContext ctx, final ChannelPromise promise) throws Exception {
		final GraphLookup gl = ctx.channel().attr(ChannelAttributes.GRAPH).get();
		final SocketAddress target = ctx.channel().attr(ChannelAttributes.TARGET).get();

		final ChannelHandler[] handlers = gl.getClientChannelInitializer(this);
		ChannelInitializer<SocketChannel> initializer = new ChannelInitializer<SocketChannel>() {
			@Override
			protected void initChannel(SocketChannel ch) throws Exception {
				ch.attr(ChannelAttributes.TARGET).set(target);
				ch.attr(ChannelAttributes.GRAPH).set(gl);
				ch.attr(ChannelAttributes.CHANNEL).set(ctx.channel());
				ctx.channel().attr(ChannelAttributes.CHANNEL).set(ch);

				ch.pipeline().addLast(handlers);
			}
		};

		ChannelFuture cf = b.handler(initializer).connect(target);
		cf.addListener(new ChannelFutureListener() {

			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				if (future.isSuccess()) {
					promise.setSuccess();
				} else {
					promise.setFailure(future.cause());
					ctx.close();
				}
			}
		});
	}

	@Override
	public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
		if (promise == null) {
			promise = ctx.newPromise();
			setupOutboundChannel(ctx, promise);
			promise.addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture future) throws Exception {
					if (!future.isSuccess())
						throw new Exception(future.cause());
				}
			});
		}
		if (ctx.channel().isActive())
			ensureUpstreamConnectedAndFire(ctx, createChannelActiveEvent(ctx));
	}

	protected boolean ignoreException(Throwable cause) {
		if (cause.getMessage().equals("Connection reset by peer")) {
			return true;
		}
		return false;
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		if (!ignoreException(cause))
			super.exceptionCaught(ctx, cause);
	}

	protected void ensureUpstreamConnectedAndFire(ChannelHandlerContext ctx, final ChannelEvent evt) throws Exception {
		if (promise == null) {
			promise = ctx.newPromise();
			setupOutboundChannel(ctx, promise);
		}
		if (!promise.isDone()) {
			promise.addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture future) throws Exception {
					if (future.isSuccess()) {
						controller.addChannelEvent(evt);
					}
				}
			});
		} else {
			controller.addChannelEvent(evt);
		}
	}

	@Override
	public void channelActive(final ChannelHandlerContext ctx) throws Exception {
		ensureUpstreamConnectedAndFire(ctx, createChannelActiveEvent(ctx));
	}

	protected ChannelEvent createChannelActiveEvent(final ChannelHandlerContext ctx) {
		SocketAddress src, dst;
		Integer connection = ctx.channel().attr(ChannelAttributes.CONNECTION_IDENTIFIER).get();
		Channel ch = ctx.channel().attr(ChannelAttributes.CHANNEL).get();
		Direction direction = Direction.Client_Server;
		if (connection != null) {
			src = ctx.channel().remoteAddress();
			if (ch != null && ch.remoteAddress() != null)
				dst = ch.remoteAddress();
			else
				dst = ctx.channel().attr(ChannelAttributes.TARGET).get();
		} else {
			connection = ch.attr(ChannelAttributes.CONNECTION_IDENTIFIER).get();
			direction = Direction.Server_Client;
			src = ch.remoteAddress();
			dst = ctx.channel().remoteAddress();
		}
		return new ChannelActiveEvent(connection, direction, src, dst) {
			@Override
			public void execute() throws Exception {
				doChannelActive(ctx);
			}
		};
	}

	protected void doChannelActive(ChannelHandlerContext ctx) throws Exception {
		super.channelActive(ctx);
	}

	@Override
	public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
		controller.addChannelEvent(createChannelInactiveEvent(ctx));
	}

	protected ChannelEvent createChannelInactiveEvent(final ChannelHandlerContext ctx) {
		Integer connection = ctx.channel().attr(ChannelAttributes.CONNECTION_IDENTIFIER).get();
		Channel ch = ctx.channel().attr(ChannelAttributes.CHANNEL).get();
		Direction direction = Direction.Client_Server;
		if (connection == null) {
			connection = ch.attr(ChannelAttributes.CONNECTION_IDENTIFIER).get();
			direction = Direction.Server_Client;
		}
		return new ChannelInactiveEvent(connection, direction) {
			@Override
			public void execute() throws Exception {
				doChannelInactive(ctx);
			}
		};
	}

	protected void doChannelInactive(ChannelHandlerContext ctx) throws Exception {
		Channel channel = ctx.channel().attr(ChannelAttributes.CHANNEL).get();
		if (channel.isOpen()) {
			channel.close();
		}
		super.channelInactive(ctx);
	}

	@Override
	public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
		ensureUpstreamConnectedAndFire(ctx, createChannelReadEvent(ctx, msg));
	}

	protected ChannelEvent createChannelReadEvent(final ChannelHandlerContext ctx, Object msg) {
		Integer connection = ctx.channel().attr(ChannelAttributes.CONNECTION_IDENTIFIER).get();
		Channel ch = ctx.channel().attr(ChannelAttributes.CHANNEL).get();
		Direction direction = Direction.Client_Server;
		if (connection == null) {
			connection = ch.attr(ChannelAttributes.CONNECTION_IDENTIFIER).get();
			direction = Direction.Server_Client;
		}
		return new ChannelReadEvent(connection, direction, msg) {
			@Override
			public void execute() throws Exception {
				doChannelRead(ctx, getMessage());
			}
		};
	}

	private void doChannelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
		Channel channel = ctx.channel().attr(ChannelAttributes.CHANNEL).get();
		ChannelFuture cf = channel.writeAndFlush(msg);
		cf.addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) {
				if (future.isSuccess()) {
					ctx.channel().read();
				} else {
					future.channel().close();
				}
			}
		});
	}

	@Override
	public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {
		if (evt instanceof ChannelInputShutdownReadComplete) {
			// ignore
		} else 
			ensureUpstreamConnectedAndFire(ctx, createChannelUserEvent(ctx, evt));
	}

	protected ChannelEvent createChannelUserEvent(final ChannelHandlerContext ctx, Object evt) {
		Integer connection = ctx.channel().attr(ChannelAttributes.CONNECTION_IDENTIFIER).get();
		Channel ch = ctx.channel().attr(ChannelAttributes.CHANNEL).get();
		Direction direction = Direction.Client_Server;
		if (connection == null) {
			connection = ch.attr(ChannelAttributes.CONNECTION_IDENTIFIER).get();
			direction = Direction.Server_Client;
		}
		return new ChannelUserEvent(connection, direction, evt) {
			@Override
			public void execute() throws Exception {
				doUserEventTriggered(ctx, getUserEvent());
			}
		};
	}

	public void doUserEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt == ChannelInputShutdownEvent.INSTANCE) {
			Channel channel = ctx.channel().attr(ChannelAttributes.CHANNEL).get();
			if (channel instanceof SocketChannel) {
				SocketChannel sch = (SocketChannel) channel;
				if (!sch.isOutputShutdown())
					((SocketChannel) channel).shutdownOutput();
			} else {
				channel.pipeline().fireUserEventTriggered(evt);
			}
		} else
			super.userEventTriggered(ctx, evt);
	}

}
