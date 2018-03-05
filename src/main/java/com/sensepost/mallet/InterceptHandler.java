package com.sensepost.mallet;

import java.net.SocketAddress;

import com.sensepost.mallet.InterceptController.ChannelActiveEvent;
import com.sensepost.mallet.InterceptController.ChannelEvent;
import com.sensepost.mallet.InterceptController.ChannelExceptionEvent;
import com.sensepost.mallet.InterceptController.ChannelInactiveEvent;
import com.sensepost.mallet.InterceptController.ChannelReadEvent;
import com.sensepost.mallet.InterceptController.ChannelUserEvent;
import com.sensepost.mallet.InterceptController.Direction;
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
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

@Sharable
public class InterceptHandler extends ChannelInboundHandlerAdapter {

	private InterceptController controller;

	private Bootstrap bootstrap;
	private ChannelPromise upstreamPromise = null;
	private boolean connectInProgress = false;

	public InterceptHandler(InterceptController controller) {
		if (controller == null)
			throw new NullPointerException("controller");
		this.controller = controller;
		bootstrap = new Bootstrap().channel(NioSocketChannel.class).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
				.option(ChannelOption.SO_KEEPALIVE, true);
	}

	synchronized private void setupOutboundChannel(final ChannelHandlerContext ctx) throws Exception {
		if (connectInProgress)
			return;

		final ConnectRequest target = ctx.channel().attr(ChannelAttributes.TARGET).get();
		if (target == null)
			return;

		connectInProgress = true;

		final GraphLookup gl = ctx.channel().attr(ChannelAttributes.GRAPH).get();
		final ChannelHandler[] handlers = gl.getClientChannelInitializer(this);

		ChannelInitializer<SocketChannel> initializer = new ChannelInitializer<SocketChannel>() {
			@Override
			protected void initChannel(SocketChannel ch) throws Exception {
				ch.attr(ChannelAttributes.GRAPH).set(gl);
				ch.attr(ChannelAttributes.CHANNEL).set(ctx.channel());
				ctx.channel().attr(ChannelAttributes.CHANNEL).set(ch);
				if (!target.getConnectPromise().isDone())
					target.getConnectPromise().setSuccess(ch);

				try {
					ch.pipeline().addLast(handlers);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};

		ChannelFuture cf = bootstrap.group(ctx.channel().eventLoop()).handler(initializer).connect(target.getTarget());
		cf.addListener(new ChannelFutureListener() {

			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				if (future.isSuccess()) {
					upstreamPromise.setSuccess();
				} else {
					upstreamPromise.setFailure(future.cause());
					exceptionCaught(ctx, future.cause());
					ctx.close();
				}
			}
		});
	}

	@Override
	public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
		if (upstreamPromise == null) {
			upstreamPromise = ctx.newPromise();
		}

		if (ctx.channel().isActive() && ctx.channel().attr(ChannelAttributes.TARGET).get() != null)
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
		cause.printStackTrace();
		if (!ignoreException(cause))
			ensureUpstreamConnectedAndFire(ctx, createChannelExceptionEvent(ctx, cause));
		else {
			Channel other = ctx.channel().attr(ChannelAttributes.CHANNEL).get();
			if (other != null && other.isOpen()) {
				other.close();
			}
			ctx.channel().close();
		}
	}

	protected ChannelEvent createChannelExceptionEvent(final ChannelHandlerContext ctx, final Throwable cause) {
		Integer connection = ctx.channel().attr(ChannelAttributes.CONNECTION_IDENTIFIER).get();
		Channel ch = ctx.channel().attr(ChannelAttributes.CHANNEL).get();
		Direction direction = Direction.Client_Server;
		if (connection == null) {
			connection = ch.attr(ChannelAttributes.CONNECTION_IDENTIFIER).get();
			direction = Direction.Server_Client;
		}
		return new ChannelExceptionEvent(connection, direction, cause) {
			@Override
			public void execute() throws Exception {
				super.execute();
				doExceptionCaught(ctx, cause);
			}
		};
	}

	public void doExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		Channel channel = ctx.channel().attr(ChannelAttributes.CHANNEL).get();
		ctx.close();
		channel.close();
	}

	protected void ensureUpstreamConnectedAndFire(ChannelHandlerContext ctx, final ChannelEvent evt) throws Exception {
		// Assumes that the downstream channel is a ServerChannel, and has a
		// parent!
		if (ctx.channel().parent() != null)
			setupOutboundChannel(ctx);

		upstreamPromise.addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				if (future.isSuccess()) {
					controller.addChannelEvent(evt);
				}
			}
		});
	}

	@Override
	public void channelActive(final ChannelHandlerContext ctx) throws Exception {
		ensureUpstreamConnectedAndFire(ctx, createChannelActiveEvent(ctx));
	}

	protected ChannelEvent createChannelActiveEvent(final ChannelHandlerContext ctx) {
		SocketAddress remote, local;
		if (ctx == null)
			throw new NullPointerException("ctx");
		if (ctx.channel() == null)
			throw new NullPointerException("ctx.channel()");
		if (ctx.channel().attr(ChannelAttributes.CONNECTION_IDENTIFIER) == null)
			throw new NullPointerException("ctx.channel().attr(ChannelAttributes.CONNECTION_IDENTIFIER)");
		Integer connection = ctx.channel().attr(ChannelAttributes.CONNECTION_IDENTIFIER).get();
		Direction direction = connection != null ? Direction.Client_Server : Direction.Server_Client;
		if (connection == null)
			connection = ctx.channel().attr(ChannelAttributes.CHANNEL).get().attr(ChannelAttributes.CONNECTION_IDENTIFIER).get();
		remote = ctx.channel().remoteAddress();
		local = ctx.channel().localAddress();
		if (remote == null)
			throw new NullPointerException("remote");
		if (local == null)
			throw new NullPointerException("local");
		return new ChannelActiveEvent(connection, direction, remote, local) {
			@Override
			public void execute() throws Exception {
				super.execute();
				doChannelActive(ctx);
			}
		};
	}

	protected void doChannelActive(ChannelHandlerContext ctx) throws Exception {
		super.channelActive(ctx);
	}

	@Override
	public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
		ensureUpstreamConnectedAndFire(ctx, createChannelInactiveEvent(ctx));
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
				super.execute();
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
				super.execute();
				doChannelRead(ctx, getMessage());
			}
		};
	}

	private void doChannelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
		Channel channel = ctx.channel().attr(ChannelAttributes.CHANNEL).get();
		if (channel == null) {
			System.out.println("Channel: " + ctx.channel().attr(ChannelAttributes.CHANNEL).get());
			System.out.println("Target: " + ctx.channel().attr(ChannelAttributes.TARGET).get());
			System.out.println("Graph: " + ctx.channel().attr(ChannelAttributes.GRAPH).get());
			System.out.println("Connection: " + ctx.channel().attr(ChannelAttributes.CONNECTION_IDENTIFIER).get());
			throw new NullPointerException("Channel is null!");
		}
		ChannelFuture cf = channel.writeAndFlush(msg);
		cf.addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) {
				if (future.isSuccess()) {
					ctx.channel().read();
				} else {
					try {
						exceptionCaught(ctx, future.cause());
					} catch (Exception e) {}
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
				super.execute();
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
