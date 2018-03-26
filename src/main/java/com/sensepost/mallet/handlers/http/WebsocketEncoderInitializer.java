package com.sensepost.mallet.handlers.http;

/**
 * Simple channelInitializer to set up a WebsocketFrameEncoder for local persistence
 */

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameEncoder;

public class WebsocketEncoderInitializer extends ChannelInitializer<Channel> {

	@Override
	protected void initChannel(Channel ch) throws Exception {
		ChannelPipeline p = ch.pipeline();
		String me = p.context(this).name();
		p.addAfter(me, null, new WebSocket13FrameEncoder(false));
	}

}
