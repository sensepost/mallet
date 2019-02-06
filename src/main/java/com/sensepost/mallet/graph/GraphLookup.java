package com.sensepost.mallet.graph;

import java.net.SocketAddress;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;

public interface GraphLookup {

	void startServers() throws Exception;
	
	ChannelInitializer<Channel> getNextHandlers(ChannelHandler handler, String option);
	
	ChannelInitializer<Channel> getClientChannelInitializer(ChannelHandler handler);
	
	ChannelInitializer<Channel> getClientChannelInitializer(ChannelHandler handler,
			boolean retain);
	
	ChannelHandler getProxyHandler(SocketAddress target);
	
	void shutdownServers() throws Exception;

	
}
