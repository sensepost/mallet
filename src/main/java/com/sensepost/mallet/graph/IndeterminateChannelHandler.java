package com.sensepost.mallet.graph;

import io.netty.channel.ChannelHandler;

public interface IndeterminateChannelHandler extends ChannelHandler {

	String[] getOutboundOptions();
	
}
