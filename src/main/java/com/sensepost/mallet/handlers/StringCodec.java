package com.sensepost.mallet.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

public class StringCodec extends CombinedChannelDuplexHandler<StringDecoder, StringEncoder> {
    public StringCodec(java.nio.charset.Charset charset) {
        init(new StringDecoder(charset), new StringEncoder(charset));
    }

    public StringCodec() {
        init(new StringDecoder(), new StringEncoder());
    }
}
