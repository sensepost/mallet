package com.sensepost.mallet.graph;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import com.sensepost.mallet.ChannelAttributes;
import com.sensepost.mallet.ConnectRequest;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class TargetSpecificChannelHandler extends ChannelInboundHandlerAdapter implements IndeterminateChannelHandler {

	private static final String DEFAULT = "DEFAULT";

	private List<InetSocketAddress> targets = new ArrayList<>();

	public TargetSpecificChannelHandler() {
		targets.add(new InetSocketAddress("*", 80));
		targets.add(new InetSocketAddress("*", 443));
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
			
			String option = DEFAULT;
			for (InetSocketAddress sa : targets) {
				String hs = sa.getHostString();
				if ((hs.equals("*") || hs.equals(((InetSocketAddress) target).getHostString()))
						&& (sa.getPort() == ((InetSocketAddress) target).getPort())) {
					option = targetToString(sa);
					break;
				}
			}
			optionSelected(ctx, option);
		}
		super.userEventTriggered(ctx, evt);
	}


	@Override
	public String[] getOutboundOptions() {
		String[] ret = new String[targets.size() + 1];
		int i = 0;
		for (InetSocketAddress sa : targets) {
			ret[i++] = targetToString(sa);
		}
		ret[i] = DEFAULT;
		return ret;
	}

	private String targetToString(InetSocketAddress sa) {
		return sa.getHostString() + ":" + sa.getPort();
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
