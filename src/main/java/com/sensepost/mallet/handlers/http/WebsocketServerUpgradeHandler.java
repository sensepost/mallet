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
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocket00FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocket00FrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocket07FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocket07FrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocket08FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocket08FrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;

public class WebsocketServerUpgradeHandler extends ChannelDuplexHandler {

    private CharSequence reqVersion = null;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) msg;
            reqVersion = req.headers().get(HttpHeaderNames.SEC_WEBSOCKET_VERSION);
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(final ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            if (response.status().equals(HttpResponseStatus.SWITCHING_PROTOCOLS)) {
                CharSequence version = response.headers().get(HttpHeaderNames.SEC_WEBSOCKET_VERSION);
                final WebSocketFrameDecoder decoder;
                final WebSocketFrameEncoder encoder;

                if (version == null)
                    version = reqVersion;

                if (version != null) {
                    if (version.equals(WebSocketVersion.V13.toHttpHeaderValue())) {
                        // Version 13 of the wire protocol - RFC 6455 (version 17 of the draft hybi
                        // specification).
                        decoder = new WebSocket13FrameDecoder(false, true, Integer.MAX_VALUE, true);
                        encoder = new WebSocket13FrameEncoder(true);
                    } else if (version.equals(WebSocketVersion.V08.toHttpHeaderValue())) {
                        // Version 8 of the wire protocol - version 10 of the draft hybi specification.
                        decoder = new WebSocket08FrameDecoder(false, true, Integer.MAX_VALUE, true);
                        encoder = new WebSocket08FrameEncoder(true);
                    } else if (version.equals(WebSocketVersion.V07.toHttpHeaderValue())) {
                        // Version 8 of the wire protocol - version 07 of the draft hybi specification.
                        decoder = new WebSocket07FrameDecoder(false, true, Integer.MAX_VALUE, true);
                        encoder = new WebSocket07FrameEncoder(true);
                    } else {
                        decoder = null;
                        encoder = null;
                    }
                } else {
                    // Assume version 00 where version header was not specified
                    decoder = new WebSocket00FrameDecoder(Integer.MAX_VALUE);
                    encoder = new WebSocket00FrameEncoder();
                }

                // if the upgrade was successful, we HAVE to remove the HTTP codecs
                // even if we didn't identify the correct websocket version
                promise.addListener(new ChannelFutureListener() {

                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            ChannelPipeline p = future.channel().pipeline();
                            HttpServerCodec http = (HttpServerCodec) p.context(HttpServerCodec.class).handler();
                            HttpObjectAggregator agg = (HttpObjectAggregator) p.context(HttpObjectAggregator.class)
                                    .handler();
                            http.upgradeFrom(ctx);
                            ctx.pipeline().remove(agg);

                            if (decoder != null && encoder != null) {
                                ctx.pipeline().addAfter(ctx.name(), null, decoder);
                                ctx.pipeline().addAfter(ctx.name(), null, encoder);
                            }
                            ctx.pipeline().remove(WebsocketServerUpgradeHandler.this);
                            ctx.fireUserEventTriggered("Removed WebSocketServerUpgradeHandler");
                            if (encoder != null)
                                ctx.fireUserEventTriggered(
                                        "WebSocket Encoder class is " + encoder.getClass().getSimpleName());
                        }
                    }
                });
            }
        }
        super.write(ctx, msg, promise);
    }

}