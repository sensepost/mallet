package com.sensepost.mallet.ssl;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import javax.net.ssl.SSLEngine;

import com.sensepost.mallet.ChannelAttributes;
import com.sensepost.mallet.ConnectRequest;
import com.sensepost.mallet.util.PcapWriterInitializer;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.Mapping;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
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
        if (hostname != null)
            ctx.channel().attr(ChannelAttributes.SERVER_NAME_INDICATION).set(hostname);
        return super.lookup(ctx, hostname);
    }

    @Override
    protected void replaceHandler(ChannelHandlerContext ctx, String hostname, SslContext sslContext) throws Exception {
        SslHandler sslHandler = null;
        try {
            sslHandler = newSslHandler(sslContext, ctx.alloc());
            SSLEngine engine = sslHandler.engine();
            ctx.pipeline().replace(this, SslHandler.class.getName(), sslHandler);
            sslHandler = null;
            PcapWriterInitializer sslPcap = ctx.channel().attr(ChannelAttributes.PCAP_SSL_INITIALIZER).get();
            if (sslPcap != null) {
                ctx.pipeline().addAfter(SslHandler.class.getName(), null, sslPcap);
            }
            ProtocolReporter protocolReporter = new ProtocolReporter(engine);
            ctx.pipeline().addAfter(SslHandler.class.getName(), null, protocolReporter);
        } finally {
            // Since the SslHandler was not inserted into the pipeline the ownership of the SSLEngine was not
            // transferred to the SslHandler.
            // See https://github.com/netty/netty/issues/5678
            if (sslHandler != null) {
                ReferenceCountUtil.safeRelease(sslHandler.engine());
            }
        }
    }

    @Override
    protected SslHandler newSslHandler(SslContext context, ByteBufAllocator allocator) {
        SslHandler sslHandler = context.newHandler(allocator);
        sslHandler.setHandshakeTimeoutMillis(handshakeTimeoutMillis);
        SSLEngine engine = sslHandler.engine();
        String[] protocols = engine.getSupportedProtocols();
        engine.setEnabledProtocols(protocols);
        String[] ciphers = engine.getSupportedCipherSuites();
        engine.setEnabledCipherSuites(ciphers);
        return sslHandler;
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
