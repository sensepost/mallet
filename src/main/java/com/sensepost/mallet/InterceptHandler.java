package com.sensepost.mallet;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.net.SocketAddress;

import com.sensepost.mallet.InterceptController.BindEvent;
import com.sensepost.mallet.InterceptController.ChannelActiveEvent;
import com.sensepost.mallet.InterceptController.ChannelEvent;
import com.sensepost.mallet.InterceptController.ChannelInactiveEvent;
import com.sensepost.mallet.InterceptController.ChannelReadCompleteEvent;
import com.sensepost.mallet.InterceptController.ChannelReadEvent;
import com.sensepost.mallet.InterceptController.ChannelRegisteredEvent;
import com.sensepost.mallet.InterceptController.ChannelUnregisteredEvent;
import com.sensepost.mallet.InterceptController.ChannelWritabilityChangedEvent;
import com.sensepost.mallet.InterceptController.CloseEvent;
import com.sensepost.mallet.InterceptController.ConnectEvent;
import com.sensepost.mallet.InterceptController.DeregisterEvent;
import com.sensepost.mallet.InterceptController.DisconnectEvent;
import com.sensepost.mallet.InterceptController.ExceptionCaughtEvent;
import com.sensepost.mallet.InterceptController.FlushEvent;
import com.sensepost.mallet.InterceptController.ReadEvent;
import com.sensepost.mallet.InterceptController.UserEventTriggeredEvent;
import com.sensepost.mallet.InterceptController.WriteEvent;

@Sharable
public class InterceptHandler extends ChannelDuplexHandler {

	private InterceptController controller;

	public InterceptHandler(InterceptController controller) {
		if (controller == null)
			throw new NullPointerException("controller");
		this.controller = controller;
	}

	private void submitEvent(final ChannelEvent evt) throws Exception {
		controller.addChannelEvent(evt);
	}

	@Override
	public void bind(ChannelHandlerContext ctx, SocketAddress localAddress,
			ChannelPromise promise) throws Exception {
		submitEvent(new BindEvent(ctx, localAddress, promise));
	}

	@Override
	public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
			SocketAddress localAddress, ChannelPromise promise)
			throws Exception {
		submitEvent(new ConnectEvent(ctx, remoteAddress, localAddress, promise));
	}

	@Override
	public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise)
			throws Exception {
		submitEvent(new DisconnectEvent(ctx, promise));
	}

	@Override
	public void close(ChannelHandlerContext ctx, ChannelPromise promise)
			throws Exception {
		submitEvent(new CloseEvent(ctx, promise));
	}

	@Override
	public void deregister(ChannelHandlerContext ctx, ChannelPromise promise)
			throws Exception {
		submitEvent(new DeregisterEvent(ctx, promise));
	}

	@Override
	public void read(ChannelHandlerContext ctx) throws Exception {
		submitEvent(new ReadEvent(ctx));
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg,
			ChannelPromise promise) throws Exception {
		submitEvent(new WriteEvent(ctx, msg, promise));
	}

	@Override
	public void flush(ChannelHandlerContext ctx) throws Exception {
		submitEvent(new FlushEvent(ctx));
	}

	@Override
	public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
		submitEvent(new ChannelRegisteredEvent(ctx));
	}

	@Override
	public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
		submitEvent(new ChannelUnregisteredEvent(ctx));
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		submitEvent(new ChannelActiveEvent(ctx));
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		submitEvent(new ChannelInactiveEvent(ctx));
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg)
			throws Exception {
		submitEvent(new ChannelReadEvent(ctx, msg));
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		submitEvent(new ChannelReadCompleteEvent(ctx));
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt)
			throws Exception {
		submitEvent(new UserEventTriggeredEvent(ctx, evt));
	}

	@Override
	public void channelWritabilityChanged(ChannelHandlerContext ctx)
			throws Exception {
		submitEvent(new ChannelWritabilityChangedEvent(ctx, ctx.channel().isWritable()));
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
			throws Exception {
		submitEvent(new ExceptionCaughtEvent(ctx, cause));
	}

}
