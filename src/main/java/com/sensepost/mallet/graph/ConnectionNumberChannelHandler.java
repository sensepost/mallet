package com.sensepost.mallet.graph;

import java.util.concurrent.atomic.AtomicInteger;

import com.sensepost.mallet.ChannelAttributes;

import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;

public class ConnectionNumberChannelHandler extends ChannelHandlerAdapter {

	private static AtomicInteger counter = new AtomicInteger(0);
	
	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		ctx.channel().attr(ChannelAttributes.CONNECTION_IDENTIFIER).set(counter.incrementAndGet());
		ctx.pipeline().remove(this);
	}

}
