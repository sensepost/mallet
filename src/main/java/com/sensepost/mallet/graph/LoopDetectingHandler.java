package com.sensepost.mallet.graph;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.WeakHashMap;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

@Sharable
public class LoopDetectingHandler extends ChannelDuplexHandler {

	private WeakHashMap<SocketAddress, ChannelHandlerContext> cache = new WeakHashMap<>();

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		SocketAddress addr = ctx.channel().localAddress();
		if (addr != null)
			cache.remove(addr);
		super.handlerRemoved(ctx);
	}

	@Override
	public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
		super.bind(ctx, localAddress, promise);
	}

	@Override
	public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
			ChannelPromise promise) throws Exception {
		System.err.println("LOOP> " + remoteAddress);
		cache.put(remoteAddress, ctx);
		super.connect(ctx, remoteAddress, localAddress, promise);
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (msg instanceof Channel) {
			Channel ch = (Channel) msg;
			SocketAddress addr = ch.localAddress();
			System.err.println("LOOP< " + cache.keySet());
			System.err.println("LOOP< " + addr);
			ChannelHandlerContext ctx2 = cache.get(addr);
			if (ctx2 != null) {
				ctx2.fireExceptionCaught(new IOException("Loop detected connecting to " + ctx2.channel().remoteAddress()));
				ch.close();
				ctx2.close();
				return;
			}
		}
		super.channelRead(ctx, msg);
	}

}
