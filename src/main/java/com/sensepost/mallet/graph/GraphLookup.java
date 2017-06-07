package com.sensepost.mallet.graph;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import io.netty.channel.ChannelHandler;

public interface GraphLookup {

	ChannelHandler[] getServerChannelInitializer(InetSocketAddress server);
	
	ChannelHandler[] getNextHandlers(ChannelHandler handler, String option);
	
	ChannelHandler[] getClientChannelInitializer(ChannelHandler handler);
	
	ChannelHandler[] getProxyInitializer(ChannelHandler handler, SocketAddress target);
	
}
