package com.sensepost.mallet.graph;

import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;

import com.sensepost.mallet.ChannelAttributes;

public class ConnectionNumberChannelHandler extends ChannelHandlerAdapter {

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		ctx.channel().attr(ChannelAttributes.CONNECTION_IDENTIFIER).set(ctx.channel().id().asLongText());
		ctx.pipeline().remove(this);
	}

}
