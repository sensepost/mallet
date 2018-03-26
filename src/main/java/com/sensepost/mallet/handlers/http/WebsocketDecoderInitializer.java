package com.sensepost.mallet.handlers.http;

/**
 * Simple channelInitializer to set up a WebsocketFrameDecoder for local persistence
 */

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;

public class WebsocketDecoderInitializer extends ChannelInitializer<Channel> {

	@Override
	protected void initChannel(Channel ch) throws Exception {
		ChannelPipeline p = ch.pipeline();
		String me = p.context(this).name();
		p.addAfter(me, null, new WebSocket13FrameDecoder(true, true, Integer.MAX_VALUE, true));
	}

}
