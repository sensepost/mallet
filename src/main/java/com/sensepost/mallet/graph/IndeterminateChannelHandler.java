package com.sensepost.mallet.graph;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

public interface IndeterminateChannelHandler extends ChannelHandler {

	String[] getOutboundOptions();
	
	void optionSelected(ChannelHandlerContext ctx, String option) throws Exception;
	
}
