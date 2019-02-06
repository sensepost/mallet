package com.sensepost.mallet.channel;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.socket.DuplexChannel;

public class ProxyChannelInitializer extends ChannelInitializer<Channel> {

	private ChannelHandler[] handlers;

	public ProxyChannelInitializer(ChannelHandler... handlers) {
		if (handlers == null)
			throw new NullPointerException("handlers");
		this.handlers = handlers;
	}

	@Override
	protected void initChannel(Channel ch) throws Exception {
		ProxyChannel proxyChannel = newProxyChannel(ch);
		proxyChannel.pipeline().addLast(handlers);
		if (proxyChannel.isCompatible(ch.eventLoop()))
			ch.eventLoop().register(proxyChannel);
		else
			new DefaultEventLoop().register(proxyChannel);
	}

	protected ProxyChannel newProxyChannel(Channel ch) {
		return ch instanceof DuplexChannel ? new ProxyChannel.DuplexProxyChannel((DuplexChannel)ch) : new ProxyChannel(ch);
	}
}
