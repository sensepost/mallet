package com.sensepost.mallet;

import java.net.SocketAddress;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.Promise;

public class FixedTargetHandler extends ChannelInboundHandlerAdapter {

	private SocketAddress target;
	
	public FixedTargetHandler(SocketAddress target) {
		this.target = target;
	}
	
	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		Promise<Channel> connectPromise = ctx.executor().newPromise();
		ConnectRequest cr = new ConnectRequest(target, connectPromise);
		ctx.channel().attr(ChannelAttributes.TARGET).set(cr);
		super.channelActive(ctx);
	}
	
}
