package com.sensepost.mallet.handlers.http2;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapter;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;

public class Http2ToHttpServerInitializer extends ChannelInitializer<Channel> {

	private static final int MAX_CONTENT_LENGTH = 1024 * 100;

	@Override
	protected void initChannel(Channel ch) throws Exception {
		ChannelPipeline pipeline = ch.pipeline();
		ChannelHandlerContext ctx = pipeline.context(this);
		String name = ctx.name();

		DefaultHttp2Connection connection = new DefaultHttp2Connection(true);
		InboundHttp2ToHttpAdapter listener = new InboundHttp2ToHttpAdapterBuilder(connection).propagateSettings(false)
				.validateHttpHeaders(false).maxContentLength(MAX_CONTENT_LENGTH).build();

		HttpToHttp2ConnectionHandler http2Handler = new HttpToHttp2ConnectionHandlerBuilder().frameListener(listener)
				.connection(connection).build();

		pipeline.addAfter(name, null, http2Handler);
	}

}
