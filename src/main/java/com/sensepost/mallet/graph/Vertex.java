package com.sensepost.mallet.graph;

import io.netty.channel.ChannelHandler;

public interface Vertex {

	String getVertexDescription();
	
	ChannelHandler[] getChannelHandlers();
	
	int getOutboundEdgeCount();
	
	String getOutboundEdgeDescription(int n);
	
	Vertex getOutboundVertex(int n);
	
}
