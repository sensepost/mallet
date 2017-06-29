package com.sensepost.mallet;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;

public class SocksInitializer extends ChannelInitializer<Channel> {

	@Override
	protected void initChannel(Channel ch) throws Exception {
		ChannelPipeline p = ch.pipeline();
		String baseName = p.context(this).name();
		p.addAfter(baseName, null, new ConnectTargetHandler());
		p.addAfter(baseName, null, new SocksServerConnectHandler());
		p.addAfter(baseName, null, new SocksServerHandler());
		p.addAfter(baseName, null, new SocksPortUnificationServerHandler());
	}

}
