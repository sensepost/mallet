package com.sensepost.mallet.ssl;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.Security;
import java.security.cert.Certificate;

import javax.net.ssl.X509KeyManager;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

public class SslClientHandler extends ChannelOutboundHandlerAdapter {

    private SslContextBuilder builder = null;

    public SslClientHandler() {
        this(null, null, null);
    }

    public SslClientHandler(X509KeyManager km, String alias) {
        this(null, km, alias);
    }

    public SslClientHandler(String provider, X509KeyManager km, String alias) {
        builder = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE);
        if (provider != null)
            builder.sslContextProvider(Security.getProvider(provider));
        if (km != null && alias != null)
            builder.keyManager(km.getPrivateKey(alias), km.getCertificateChain(alias));
        builder.protocols(new String[] { /* "SSLv3", */ "TLSv1", "TLSv1.1", "TLSv1.2" });
    }

    public SslClientHandler(SslContextBuilder builder) {
        this.builder = builder;
    }

    @Override
    public void connect(ChannelHandlerContext ctx, final SocketAddress remoteAddress, SocketAddress localAddress,
            ChannelPromise promise) throws Exception {
        replaceSslHandler(ctx, remoteAddress).connect(ctx, remoteAddress, localAddress, promise);
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        replaceSslHandler(ctx, ctx.channel().remoteAddress()).read(ctx);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        replaceSslHandler(ctx, ctx.channel().remoteAddress()).write(ctx, msg, promise);
    }

    protected SslHandler replaceSslHandler(ChannelHandlerContext ctx, final SocketAddress remoteAddress)
            throws Exception {
        final ChannelPipeline p = ctx.channel().pipeline();
        String me = ctx.name();
        SslContext clientContext = builder.build();
        final SslHandler s;
        if (remoteAddress instanceof InetSocketAddress) {
            InetSocketAddress remote = (InetSocketAddress) remoteAddress;
            s = clientContext.newHandler(ctx.alloc(), remote.getHostString(), remote.getPort());
            ctx.fireUserEventTriggered("Adding SslClientHandler for " + remote.getHostString());
        } else {
            s = clientContext.newHandler(ctx.alloc());
            ctx.fireUserEventTriggered("Adding SslClientHandler for default client");
        }
        s.handshakeFuture().addListener(new FutureListener<Channel>() {
            @Override
            public void operationComplete(Future<Channel> future) throws Exception {
                if (future.isSuccess()) {
                    Certificate[] serverCerts = s.engine().getSession().getPeerCertificates();
                    ctx.fireUserEventTriggered(serverCerts);
                } else {
                    if (remoteAddress instanceof InetSocketAddress) {
                        InetSocketAddress remote = (InetSocketAddress) remoteAddress;
                        if (NetUtil.isValidIpV4Address(remote.getHostString())
                                || NetUtil.isValidIpV6Address(remote.getHostString())) {
                            ctx.fireUserEventTriggered("HandshakeException connecting to IP address "
                                    + remote.getHostString() + ". Remote end may be expecting a hostname via SNI.\n"
                                    + "Make sure that you have enabled remote DNS lookup (SOCKS4a or SOCKS5h) or similar.");
                        }
                    }
                }
            }
        });
        p.replace(me, null, s);
        return s;
    }

}
