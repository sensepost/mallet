package com.sensepost.mallet.ssl;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import com.sensepost.mallet.ChannelAttributes;
import com.sensepost.mallet.ConnectRequest;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.Mapping;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Future;

public class SslServerHandler extends SniHandler {

    private boolean userEventDelayed = false;

    private String hostname = null;

    public SslServerHandler(Mapping<? super String, ? extends SslContext> mapping) {
        this(mapping, null);
    }

    public SslServerHandler(Mapping<? super String, ? extends SslContext> mapping, String hostname) {
        super(mapping);
        this.hostname = hostname;
    }

    @Override
    protected Future<SslContext> lookup(ChannelHandlerContext ctx, String hostname) throws Exception {
        if (hostname == null)
            hostname = this.hostname;
        ConnectRequest cr = ctx.channel().attr(ChannelAttributes.TARGET).get();
        if (cr != null) {
            SocketAddress sa = cr.getTarget();
            if (sa instanceof InetSocketAddress) {
                InetSocketAddress isa = (InetSocketAddress) sa;
                String host = isa.getHostString();
                if ((NetUtil.isValidIpV4Address(host) || NetUtil.isValidIpV6Address(host))) {
                    if (hostname != null) {
                        byte[] addr = NetUtil.createByteArrayFromIpAddressString(host);
                        InetAddress ia = InetAddress.getByAddress(hostname, addr);
                        isa = new InetSocketAddress(ia, isa.getPort());
                        cr = new ConnectRequest(isa, cr.getConnectPromise());
                        ctx.channel().attr(ChannelAttributes.TARGET).set(cr);
                    }
                }
            }
            if (userEventDelayed)
                ctx.fireUserEventTriggered(cr);
        }
        return super.lookup(ctx, hostname);
    }

    @Override
    protected SslHandler newSslHandler(SslContext context, ByteBufAllocator allocator) {
        return super.newSslHandler(context, allocator);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof ConnectRequest) {
            ConnectRequest cr = (ConnectRequest) evt;
            SocketAddress sa = cr.getTarget();
            if (sa instanceof InetSocketAddress) {
                InetSocketAddress isa = ((InetSocketAddress)sa);
                String host = isa.getHostString();
                if (NetUtil.isValidIpV4Address(host) || NetUtil.isValidIpV6Address(host)) {
                    userEventDelayed = true;
                    ctx.fireUserEventTriggered("SslServerHandler delaying the ConnectRequest to IP " + host + " until after SNI");
                    return;
                }
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    
}
