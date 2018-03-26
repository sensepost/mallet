package com.sensepost.mallet.handlers.http;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.WebSocket08FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocket08FrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrameEncoder;

public class WebsocketClientUpgradeHandler extends ChannelDuplexHandler {

	
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg)
			throws Exception {
		if (msg instanceof FullHttpResponse) {
			FullHttpResponse response = (FullHttpResponse) msg;
			if (response.status().equals(
					HttpResponseStatus.SWITCHING_PROTOCOLS)) {
				CharSequence version = response.headers().get(
						HttpHeaderNames.SEC_WEBSOCKET_VERSION);
				final WebSocketFrameDecoder decoder;
				final WebSocketFrameEncoder encoder;
				if ("13".equals(version)) {
					decoder = new WebSocket13FrameDecoder(false, true,
							Integer.MAX_VALUE, true);
					encoder = new WebSocket13FrameEncoder(true);
				} else if ("08".equals(version)) {
					decoder = new WebSocket08FrameDecoder(false, true,
							Integer.MAX_VALUE, true);
					encoder = new WebSocket08FrameEncoder(true);
				} else { // FIXME: handle the other options
					decoder = null;
					encoder = null;
				}

				ChannelPipeline p = ctx.pipeline();
				System.out.println("Pipeline is : " + p);
				ChannelHandlerContext httpCtx = p.context(HttpClientCodec.class);
				HttpClientCodec http = (HttpClientCodec) httpCtx.handler();
				ChannelHandlerContext aggCtx = p.context(HttpObjectAggregator.class);
				HttpObjectAggregator agg = (HttpObjectAggregator) aggCtx.handler();
				http.prepareUpgradeFrom(httpCtx);
				http.upgradeFrom(httpCtx);
				ctx.pipeline().remove(agg);
				
				if (decoder != null && encoder != null) {
					ctx.pipeline().addAfter(ctx.name(), null,
							decoder);
					ctx.pipeline().addAfter(ctx.name(), null,
							encoder);
				}
				System.out.println("Pipeline is now : " + p);
				ctx.pipeline().remove(this);
			}
		}
		super.channelRead(ctx, msg);
	}

}