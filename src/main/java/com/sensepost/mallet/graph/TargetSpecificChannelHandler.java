package com.sensepost.mallet.graph;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

import com.sensepost.mallet.ChannelAttributes;
import com.sensepost.mallet.ConnectRequest;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;

public class TargetSpecificChannelHandler extends ChannelInboundHandlerAdapter implements IndeterminateChannelHandler {

	public TargetSpecificChannelHandler() {
	}

	private String[] options = new String[0];
	
	public void setOutboundOptions(String[] options) {
		this.options = options;
	}
	
	@Override
	public String[] getOutboundOptions() {
		return options;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		super.channelActive(ctx);
		ConnectRequest target = ctx.channel().attr(ChannelAttributes.TARGET).get();
		if (target != null) 
			this.userEventTriggered(ctx, target);
	}


	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof ConnectRequest) {
			ConnectRequest cr = (ConnectRequest) evt;
			InetSocketAddress target = (InetSocketAddress) cr.getTarget();
			String hs = target.getHostString() + ":" + target.getPort();
			
			String branch = null;
			boolean defaultOption = false;
			for (String option : options) {
				if ("".equals(option)) {
					defaultOption = true;
				} else if (hs.matches(option)) {
					branch = option;
					break;
				}
			}
			if (branch == null) {
				if (defaultOption)
					branch = "";
				else
					throw new RuntimeException("No match for " + hs + " in " + Arrays.asList(options));
			}
			optionSelected(ctx, branch);
		}
		super.userEventTriggered(ctx, evt);
	}

	@Override
	public void optionSelected(ChannelHandlerContext ctx, String option) throws Exception {
		GraphLookup gl = ctx.channel().attr(ChannelAttributes.GRAPH).get();
		if (gl == null)
			throw new NullPointerException("gl");
		ChannelInitializer<Channel> initializer = gl.getNextHandlers(this, option);
		String name = ctx.name();
		ctx.pipeline().addAfter(name, null, initializer);
		ctx.pipeline().remove(name);
	}
}
