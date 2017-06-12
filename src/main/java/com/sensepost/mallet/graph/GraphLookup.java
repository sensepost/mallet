package com.sensepost.mallet.graph;

import java.net.SocketAddress;

import io.netty.channel.ChannelHandler;

public interface GraphLookup {

	void startServers() throws Exception;
	
	ChannelHandler[] getNextHandlers(ChannelHandler handler, String option);
	
	ChannelHandler[] getClientChannelInitializer(ChannelHandler handler);
	
	ChannelHandler[] getProxyInitializer(ChannelHandler handler, SocketAddress target);
	
	void shutdownServers() throws Exception;
	
}
