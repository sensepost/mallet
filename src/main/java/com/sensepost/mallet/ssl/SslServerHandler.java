package com.sensepost.mallet.ssl;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.Mapping;
import io.netty.util.concurrent.Future;

public class SslServerHandler extends SniHandler {

    private Mapping<? super String, ? extends SslContext> mapping;
    private String hostname = null;

    public SslServerHandler(Mapping<? super String, ? extends SslContext> mapping) {
        this(mapping, null);
    }

    public SslServerHandler(Mapping<? super String, ? extends SslContext> mapping, String hostname) {
        super(mapping);
        this.mapping = mapping;
        this.hostname = hostname;
    }

    @Override
    protected Future<SslContext> lookup(ChannelHandlerContext ctx, String hostname) throws Exception {
        if (hostname == null)
            hostname = this.hostname;
        return super.lookup(ctx, hostname);
    }

    @Override
    protected SslHandler newSslHandler(SslContext context, ByteBufAllocator allocator) {
        return super.newSslHandler(context, allocator);
    }

    
}
