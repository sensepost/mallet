package com.sensepost.mallet.handlers.http;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocket08FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocket08FrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrameEncoder;

public class WebsocketServerUpgradeHandler extends ChannelDuplexHandler {

	@Override
	public void write(final ChannelHandlerContext ctx, Object msg,
			ChannelPromise promise) throws Exception {
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

				promise.addListener(new ChannelFutureListener() {

					@Override
					public void operationComplete(ChannelFuture future)
							throws Exception {
						if (future.isSuccess()) {
							ChannelPipeline p = future.channel().pipeline();
							HttpServerCodec http = (HttpServerCodec) p.context(HttpServerCodec.class).handler();
							HttpObjectAggregator agg = (HttpObjectAggregator) p.context(HttpObjectAggregator.class).handler();
							http.upgradeFrom(ctx);
							ctx.pipeline().remove(agg);
							if (decoder != null && encoder != null) {
								ctx.pipeline().addAfter(ctx.name(), null,
										decoder);
								ctx.pipeline().addAfter(ctx.name(), null,
										encoder);
							}
							ctx.pipeline().remove(WebsocketServerUpgradeHandler.this);
						}
					}
				});
			}
		}
		super.write(ctx, msg, promise);
	}

}