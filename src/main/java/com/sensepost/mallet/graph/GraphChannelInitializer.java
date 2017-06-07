package com.sensepost.mallet.graph;

import com.sensepost.mallet.ChannelAttributes;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;

public class GraphChannelInitializer extends ChannelInitializer<SocketChannel> {

	@Override
	protected void initChannel(SocketChannel ch) throws Exception {
		GraphLookup gl = ch.parent().attr(ChannelAttributes.GRAPH).get();
		ch.attr(ChannelAttributes.GRAPH).set(gl);
		ChannelHandler[] handlers = gl.getServerChannelInitializer(ch.parent().localAddress());
		ch.pipeline().addFirst(new ConnectionNumberChannelHandler());
		ch.pipeline().addLast(handlers);
	}

}
