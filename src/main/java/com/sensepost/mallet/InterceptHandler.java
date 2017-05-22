package com.sensepost.mallet;

import com.sensepost.mallet.events.ChannelActiveEvent;
import com.sensepost.mallet.events.ChannelInactiveEvent;
import com.sensepost.mallet.events.ChannelReadEvent;
import com.sensepost.mallet.events.ChannelUserEvent;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.SocketChannel;

@Sharable
public class InterceptHandler extends ChannelHandlerAdapter {

	private InterceptController controller;

	public InterceptHandler(InterceptController controller) {
		if (controller == null)
			throw new NullPointerException("controller");
		this.controller = controller;
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

	@Override
	public void channelActive(final ChannelHandlerContext ctx) throws Exception {
		controller.addChannelEvent(new ChannelActiveEvent(
				ctx.channel().attr(ChannelAttributes.CHANNEL).get().remoteAddress(), ctx.channel().remoteAddress()) {
			@Override
			public void execute() throws Exception {
				doChannelActive(ctx);
			}
		});
	}

	protected void doChannelActive(ChannelHandlerContext ctx) throws Exception {
		super.channelActive(ctx);
	}

	@Override
	public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
		controller.addChannelEvent(new ChannelInactiveEvent(ctx.channel().remoteAddress(),
				ctx.channel().attr(ChannelAttributes.CHANNEL).get().remoteAddress()) {
			public void execute() throws Exception {
				doChannelInactive(ctx);
			}
		});
	}

	protected void doChannelInactive(ChannelHandlerContext ctx) throws Exception {
		Channel channel = ctx.attr(ChannelAttributes.CHANNEL).get();
		if (channel.isOpen()) {
			channel.close();
		}
		super.channelInactive(ctx);
	}

	@Override
	public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
		controller.addChannelEvent(new ChannelReadEvent(ctx.channel().remoteAddress(),
				ctx.channel().attr(ChannelAttributes.CHANNEL).get().remoteAddress(), msg) {
			public void execute() throws Exception {
				doChannelRead(ctx, getMessage());
			}
		});
	}

	private void doChannelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
		Channel channel = ctx.attr(ChannelAttributes.CHANNEL).get();
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
		controller.addChannelEvent(new ChannelUserEvent(ctx.channel().remoteAddress(),
				ctx.channel().attr(ChannelAttributes.CHANNEL).get().remoteAddress(), evt) {
			@Override
			public void execute() throws Exception {
				doUserEventTriggered(ctx, evt);
			}
		});
	}

	public void doUserEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof ChannelInputShutdownEvent) {
			Channel channel = ctx.attr(ChannelAttributes.CHANNEL).get();
			((SocketChannel) channel).shutdownOutput();
		}
		super.userEventTriggered(ctx, evt);
	}
}
