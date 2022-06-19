package com.sensepost.mallet.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

public class StringCodec extends ChannelDuplexHandler {
    public StringEncoder encoder;
    public StringDecoder decoder;

    public StringCodec(java.nio.charset.Charset charset) {
        this.encoder = new StringEncoder(charset);
        this.decoder = new StringDecoder(charset);
    }

    public StringCodec() {
        this.encoder = new StringEncoder();
        this.decoder = new StringDecoder();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg,
                      ChannelPromise promise) throws Exception {
        if (msg instanceof java.lang.CharSequence) {
            encoder.write(ctx, msg, promise);
        } else {
            super.write(ctx, msg, promise);
        }

    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg)
            throws Exception {
        if (msg instanceof ByteBuf) {
            decoder.channelRead(ctx, msg);
        } else {
            super.channelRead(ctx, msg);
        }
    }
}
