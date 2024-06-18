package com.sensepost.mallet.channel;

import java.net.InetSocketAddress;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketChannelConfig;

public class SocketSubChannel extends DuplexSubChannel implements SocketChannel {

    SocketSubChannel(ChannelHandlerContext ctx) {
        super(ctx);
    }

    @Override
    public ServerSocketChannel parent() {
        Channel parent = super.parent();
        do {
            if (parent instanceof ServerSocketChannel)
                return (ServerSocketChannel) parent;
            else
                parent = parent.parent();
        } while (parent != null);
        return null;
    }

    @Override
    public SocketChannelConfig config() {
        return (SocketChannelConfig) super.parent().config();
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.parent().localAddress();
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.parent().remoteAddress();
    }

}
