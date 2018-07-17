package com.sensepost.mallet;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.net.SocketAddress;

import com.sensepost.mallet.InterceptController.ChannelActiveEvent;
import com.sensepost.mallet.InterceptController.ChannelEvent;
import com.sensepost.mallet.InterceptController.ChannelExceptionEvent;
import com.sensepost.mallet.InterceptController.ChannelInactiveEvent;
import com.sensepost.mallet.InterceptController.ChannelReadEvent;
import com.sensepost.mallet.InterceptController.ChannelUserEvent;
import com.sensepost.mallet.InterceptController.ChannelWriteEvent;
import com.sensepost.mallet.InterceptController.Direction;

@Sharable
public class InterceptHandler extends ChannelDuplexHandler {

	private InterceptController controller;

	public InterceptHandler(InterceptController controller) {
		if (controller == null)
			throw new NullPointerException("controller");
		this.controller = controller;
	}

	private void submitEvent(ChannelHandlerContext ctx, final ChannelEvent evt) throws Exception {
		controller.addChannelEvent(evt);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		submitEvent(ctx, createChannelExceptionEvent(ctx, cause));
	}

	protected ChannelEvent createChannelExceptionEvent(final ChannelHandlerContext ctx, final Throwable cause) {
		String connection = ctx.channel().id().asLongText();
		Direction direction = Direction.Client_Server;
		if (ctx.channel().parent() == null)
			direction = Direction.Server_Client;
		return new ChannelExceptionEvent(ctx, connection, direction, cause) {
			@Override
			public void execute() throws Exception {
				super.execute();
				getChannelHandlerContext().fireExceptionCaught(cause);
			}
		};
	}

	@Override
	public void channelActive(final ChannelHandlerContext ctx) throws Exception {
		submitEvent(ctx, createChannelActiveEvent(ctx));
	}

	protected ChannelEvent createChannelActiveEvent(final ChannelHandlerContext ctx) {
		String connection = ctx.channel().id().asLongText();
		Direction direction = Direction.Client_Server;
		if (ctx.channel().parent() == null)
			direction = Direction.Server_Client;

		SocketAddress remote = ctx.channel().remoteAddress();
		SocketAddress local = ctx.channel().localAddress();
		return new ChannelActiveEvent(ctx, connection, direction, remote, local) {
			@Override
			public void execute() throws Exception {
				super.execute();
				getChannelHandlerContext().fireChannelActive();
			}
		};
	}

	@Override
	public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
		submitEvent(ctx, createChannelInactiveEvent(ctx));
	}

	protected ChannelEvent createChannelInactiveEvent(final ChannelHandlerContext ctx) {
		String connection = ctx.channel().id().asLongText();
		Direction direction = Direction.Client_Server;
		if (ctx.channel().parent() == null)
			direction = Direction.Server_Client;

		return new ChannelInactiveEvent(ctx, connection, direction) {
			@Override
			public void execute() throws Exception {
				super.execute();
				getChannelHandlerContext().fireChannelInactive();
			}
		};
	}

	@Override
	public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
		submitEvent(ctx, createChannelReadEvent(ctx, msg));
	}

	protected ChannelEvent createChannelReadEvent(final ChannelHandlerContext ctx, Object msg) {
		String connection = ctx.channel().id().asLongText();
		Direction direction = Direction.Client_Server;
		if (ctx.channel().parent() == null)
			direction = Direction.Server_Client;

		return new ChannelReadEvent(ctx, connection, direction, msg) {
			@Override
			public void execute() throws Exception {
				super.execute();
				getChannelHandlerContext().fireChannelRead(getMessage());
			}
		};
	}

	@Override
	public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {
		submitEvent(ctx, createChannelUserEvent(ctx, evt));
	}

	protected ChannelEvent createChannelUserEvent(final ChannelHandlerContext ctx, Object evt) {
		String connection = ctx.channel().id().asLongText();
		Direction direction = Direction.Client_Server;
		if (ctx.channel().parent() == null)
			direction = Direction.Server_Client;

		return new ChannelUserEvent(ctx, connection, direction, evt) {
			@Override
			public void execute() throws Exception {
				super.execute();
				getChannelHandlerContext().fireUserEventTriggered(getUserEvent());
			}
		};
	}

}
