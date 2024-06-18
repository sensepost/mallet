package com.sensepost.mallet.channel;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DuplexChannel;

class DuplexSubChannel extends SubChannel implements DuplexChannel {

	DuplexSubChannel(ChannelHandlerContext ctx) {
		super(ctx);
	}

	@Override
	public Channel parent() {
		return super.parent();
	}

	@Override
	public boolean isInputShutdown() {
		return ((DuplexChannel)parent()).isInputShutdown();
	}

	@Override
	public ChannelFuture shutdownInput() {
		return ((DuplexChannel)parent()).shutdownInput();
	}

	@Override
	public ChannelFuture shutdownInput(ChannelPromise promise) {
		ChannelFuture cf = ((DuplexChannel)parent()).shutdownInput();
		cf.addListener(new PromiseRelay(promise));
		return cf;
	}

	@Override
	public boolean isOutputShutdown() {
		return ((DuplexChannel)parent()).isOutputShutdown();
	}

	@Override
	public ChannelFuture shutdownOutput() {
		return ((DuplexChannel)parent()).shutdownOutput();
	}

	@Override
	public ChannelFuture shutdownOutput(ChannelPromise promise) {
		ChannelFuture cf = ((DuplexChannel)parent()).shutdownOutput();
		cf.addListener(new PromiseRelay(promise));
		return cf;
	}

	@Override
	public boolean isShutdown() {
		return ((DuplexChannel)parent()).isShutdown();
	}

	@Override
	public ChannelFuture shutdown() {
		return ((DuplexChannel)parent()).shutdown();
	}

	@Override
	public ChannelFuture shutdown(ChannelPromise promise) {
		ChannelFuture cf = ((DuplexChannel)parent()).shutdown();
		cf.addListener(new PromiseRelay(promise));
		return cf;
	}
}