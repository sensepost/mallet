package com.sensepost.mallet.handlers.http;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObjectAggregator;

public class MaxHttpObjectAggregator extends ChannelInitializer<Channel> {
	@Override
	protected void initChannel(Channel ch) throws Exception {
		String name = ch.pipeline().context(this).name();
		ch.pipeline().addAfter(name, null, newInstance());
	}

	public static HttpObjectAggregator newInstance() {
		return new HttpObjectAggregator(Integer.MAX_VALUE) {
			@Override
			protected void finishAggregation(FullHttpMessage aggregated)
					throws Exception {
				if (aggregated instanceof FullHttpResponse)
					super.finishAggregation(aggregated);
			}
		};
	}
}