package com.sensepost.mallet.graph;

import java.net.InetSocketAddress;

import com.sensepost.mallet.ChannelAttributes;
import com.sensepost.mallet.ConnectRequest;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

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
			
			for (String option : options) {
				if (hs.matches(option)) {
					optionSelected(ctx, option);
					break;
				}
			}
		}
		super.userEventTriggered(ctx, evt);
	}

	@Override
	public void optionSelected(ChannelHandlerContext ctx, String option) throws Exception {
		GraphLookup gl = ctx.channel().attr(ChannelAttributes.GRAPH).get();
		if (gl == null)
			throw new NullPointerException("gl");
		ChannelHandler[] handlers = gl.getNextHandlers(this, option);
		String name = ctx.name();
		for (int i = handlers.length - 1; i >= 0; i--) {
			try {
				ctx.pipeline().addAfter(name, null, handlers[i]);
			} catch (Exception e) {
				Channel ch = ctx.channel().attr(ChannelAttributes.CHANNEL).get();
				ctx.close();
				if (ch != null && ch.isOpen())
					ch.close();
			}
		}
		ctx.pipeline().remove(name);
	}
}
