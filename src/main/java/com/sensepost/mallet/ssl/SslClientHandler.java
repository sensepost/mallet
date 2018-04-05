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

	private X509KeyManager km = null;
	private String alias = null;
	private String provider = null;

	public SslClientHandler() {
	}

	public SslClientHandler(X509KeyManager km, String alias) {
		this.km = km;
		this.alias = alias;
	}

	public SslClientHandler(String provider, X509KeyManager km, String alias) {
		this.provider = provider;
		this.km = km;
		this.alias = alias;
	}

	@Override
	public void connect(ChannelHandlerContext ctx, final SocketAddress remoteAddress,
			SocketAddress localAddress, ChannelPromise promise)
			throws Exception {
		ChannelPipeline p = ctx.channel().pipeline();
		String me = ctx.name();
		SslContextBuilder builder = SslContextBuilder.forClient().trustManager(
				InsecureTrustManagerFactory.INSTANCE);
		if (km != null && alias != null)
			builder.keyManager(km.getPrivateKey(alias),
					km.getCertificateChain(alias));
		if (provider != null)
			builder.sslContextProvider(Security.getProvider(provider));
		builder.protocols(new String[] { "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2"});
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
