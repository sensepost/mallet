package com.sensepost.mallet;

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
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.LinkedList;
import java.util.Queue;

import com.sensepost.mallet.graph.GraphLookup;

@Sharable
public class RelayHandler extends ChannelInboundHandlerAdapter {

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

		logger.info("Connecting " + ctx.channel().remoteAddress() + " -> " + target);
		ChannelInitializer<SocketChannel> initializer = new ChannelInitializer<SocketChannel>() {

			@Override
			protected void initChannel(SocketChannel ch) throws Exception {
				controller.linkChannels(ctx.channel().id().asLongText(), ch.id().asLongText());

				ch.attr(ChannelAttributes.GRAPH).set(gl);
				ch.attr(ChannelAttributes.CHANNEL).set(ctx.channel());
				ctx.channel().attr(ChannelAttributes.CHANNEL).set(ch);
				if (!target.getConnectPromise().isDone())
					target.getConnectPromise().setSuccess(ch);

				ChannelHandler[] handlers = gl.getClientChannelInitializer(RelayHandler.this);
				ch.pipeline().addLast(handlers);
			}
		};

		try {
			Bootstrap bootstrap = new Bootstrap().channel(NioSocketChannel.class).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
					.option(ChannelOption.SO_KEEPALIVE, true).option(ChannelOption.ALLOW_HALF_CLOSURE, true);

			connectFuture = bootstrap.group(ctx.channel().eventLoop()).handler(initializer).connect(target.getTarget());
			connectFuture.addListener(new ChannelFutureListener() {
				
				@Override
				public void operationComplete(ChannelFuture future) throws Exception {
					if (future.isSuccess()) {
						while(!queue.isEmpty())
							future.channel().writeAndFlush(queue.remove());
					} else {
						future.cause().printStackTrace();
					}
				}
			});
		} catch (Exception e) {
			logger.error("Failed connecting to " + ctx.channel().remoteAddress() + " -> " + target, e);
			ctx.close();
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		Channel other = ctx.channel().attr(ChannelAttributes.CHANNEL).get();
		if (other != null && other.isOpen()) {
			other.close();
		}
		super.channelInactive(ctx);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		Channel other = ctx.channel().attr(ChannelAttributes.CHANNEL).get();
		if (other != null && other.isOpen()) {
			other.close();
		}
		ctx.channel().close();
		cause.printStackTrace();
	}

	@Override
	public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
		if (connectFuture != null && !connectFuture.isDone()) {
			queue.add(msg);
			return;
		} else {
			Channel channel = ctx.channel().attr(ChannelAttributes.CHANNEL).get();
			if (channel == null) {
				throw new NullPointerException("Channel is null!");
			}
			ChannelFuture cf = channel.writeAndFlush(msg);
			cf.addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture future) {
					if (!future.isSuccess()) {
						System.out.println("Write failed!");
						if (future.channel().isOpen())
							future.channel().close();
						future.cause().printStackTrace();
					}
				}
			});
		}
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {

		if (evt instanceof ChannelInputShutdownEvent) {
			Channel other = ctx.channel().attr(ChannelAttributes.CHANNEL).get();
			if (other instanceof SocketChannel && !((SocketChannel)other).isOutputShutdown()) {
				((SocketChannel) other).shutdownOutput();
			} else {
				ctx.channel().close();
				other.close();
			}
		} else if (evt instanceof ChannelOutputShutdownEvent) {
		} else if (evt instanceof ChannelInputShutdownReadComplete) {
			ctx.channel().config().setAutoRead(false);
			Channel other = ctx.channel().attr(ChannelAttributes.CHANNEL).get();
			if (((SocketChannel)other).isInputShutdown()) {
				other.close();
				ctx.channel().close();
			}
//			if (other.isOpen())
//				other.close();
		} else if (evt instanceof ConnectRequest) {
			added = true;
			setupOutboundChannel(ctx);
		}
		super.userEventTriggered(ctx, evt);
	}

}
