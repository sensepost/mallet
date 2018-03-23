package com.sensepost.mallet.ssl;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.security.Security;

import javax.net.ssl.X509KeyManager;

public class SslClientHandler extends ChannelInitializer<SocketChannel> {

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
	protected void initChannel(SocketChannel ch) throws Exception {
		ChannelPipeline p = ch.pipeline();
		String me = p.context(this).name();
		SslContextBuilder builder = SslContextBuilder.forClient().trustManager(
				InsecureTrustManagerFactory.INSTANCE);
		if (km != null && alias != null)
			builder.keyManager(km.getPrivateKey(alias),
					km.getCertificateChain(alias));
		if (provider != null)
			builder.sslContextProvider(Security.getProvider(provider));
		SslContext clientContext = builder.build();
		final SslHandler s = clientContext.newHandler(ch.alloc());
		// FIXME: Placeholder for future userEvent for debugging SSL handshaking
//		s.handshakeFuture().addListener(
//				new GenericFutureListener<Future<Channel>>() {
//					@Override
//					public void operationComplete(Future<Channel> future)
//							throws Exception {
//						System.out.println("Handshake complete");
//						System.out.println(s.engine().getSession()
//								.getCipherSuite());
//					}
//				});

		p.addAfter(me, null, s);
	}

}
