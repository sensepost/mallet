package com.sensepost.mallet.ssl;

import java.util.List;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;

import com.sensepost.mallet.ChannelAttributes;
import com.sensepost.mallet.graph.ExceptionCatcher;
import com.sensepost.mallet.graph.GraphLookup;
import com.sensepost.mallet.graph.IndeterminateChannelHandler;

public class ALPNHandler extends ApplicationProtocolNegotiationHandler
		implements IndeterminateChannelHandler {

	private static final String[] OUTBOUND_OPTIONS = new String[] {
			ApplicationProtocolNames.HTTP_2, ApplicationProtocolNames.HTTP_1_1 };

	private String[] options = null;

	public ALPNHandler() {
		super("");
	}

	@Override
	protected void configurePipeline(ChannelHandlerContext ctx, String protocol)
			throws Exception {
		optionSelected(ctx, protocol);
	}

	@Override
	public void setOutboundOptions(String[] options) {
		this.options = options;
	}

	@Override
	public String[] getOutboundOptions() {
		return options == null ? OUTBOUND_OPTIONS : options;
	}

	@Override
	public void optionSelected(ChannelHandlerContext ctx, String option) throws Exception {
		GraphLookup gl = ctx.channel().attr(ChannelAttributes.GRAPH).get();
		if (gl == null)
			throw new NullPointerException("gl");
		ChannelInitializer<Channel> initializer = gl.getNextHandlers(this, option);
		ctx.pipeline().replace(this, null, initializer);
	}

}
