package com.sensepost.mallet.ssl;

import java.lang.ref.WeakReference;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;

public class ProtocolReporter extends ChannelInboundHandlerAdapter {

	private WeakReference<SSLEngine> engine;

	public ProtocolReporter(SSLEngine engine) {
		this.engine = new WeakReference<SSLEngine>(engine);
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		super.userEventTriggered(ctx, evt);
		if (evt instanceof SslHandshakeCompletionEvent) {
			SSLEngine engine = this.engine.get();
			if (engine != null) {
				SSLSession session = engine.getSession();
				if (session != null) {
					if (SslHandshakeCompletionEvent.SUCCESS.equals(evt)) {
						String msg = "Negotiated " + session.getProtocol() + ": " + session.getCipherSuite();
						super.userEventTriggered(ctx, msg);
					} else {
						System.out.println(session);
					}
				}
			}
			this.engine.clear();
			ctx.pipeline().remove(this);
		}
	}

}
