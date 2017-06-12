package com.sensepost.mallet.graph;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

import com.sensepost.mallet.ChannelAttributes;

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
		targets.add(new InetSocketAddress("*", 8000));
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
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		GraphLookup gl = ctx.channel().attr(ChannelAttributes.GRAPH).get();
		
		SocketAddress target = ctx.channel().attr(ChannelAttributes.TARGET).get();
		
		if (gl != null) {
			String option = DEFAULT;
			for (InetSocketAddress sa : targets) {
				String hs = sa.getHostString();
				if ((hs.equals("*") || hs.equals(((InetSocketAddress)target).getHostString())) && (sa.getPort() == ((InetSocketAddress)target).getPort())) {
					option = targetToString(sa);
					break;
				}
			}
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
		super.channelRead(ctx, msg);
	}

}
