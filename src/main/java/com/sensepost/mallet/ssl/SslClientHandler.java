package com.sensepost.mallet.ssl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.Security;

import javax.net.ssl.X509KeyManager;

public class SslClientHandler extends ChannelOutboundHandlerAdapter {

	private SslContextBuilder builder = null;
	
	public SslClientHandler() {
		this(null, null, null);
	}

	public SslClientHandler(X509KeyManager km, String alias) {
		this(null, km, alias);
	}

	public SslClientHandler(String provider, X509KeyManager km, String alias) {
		builder = SslContextBuilder.forClient().trustManager(
				InsecureTrustManagerFactory.INSTANCE);
		if (provider != null)
			builder.sslContextProvider(Security.getProvider(provider));
		if (km != null && alias != null)
			builder.keyManager(km.getPrivateKey(alias),
					km.getCertificateChain(alias));
		builder.protocols(new String[] { "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2"});
	}

	public SslClientHandler(SslContextBuilder builder) {
		this.builder = builder;
	}
	
	@Override
	public void connect(ChannelHandlerContext ctx, final SocketAddress remoteAddress,
			SocketAddress localAddress, ChannelPromise promise)
			throws Exception {
		ChannelPipeline p = ctx.channel().pipeline();
		String me = ctx.name();
		SslContext clientContext = builder.build();
		final SslHandler s;
		if (remoteAddress instanceof InetSocketAddress) {
			InetSocketAddress remote = (InetSocketAddress) remoteAddress;
			s = clientContext.newHandler(ctx.alloc(), remote.getHostString(), remote.getPort());
		} else {
			s = clientContext.newHandler(ctx.alloc());
		}

		p.addAfter(me, null, s);
		p.remove(this);
		super.connect(ctx, remoteAddress, localAddress, promise);
	}

}
