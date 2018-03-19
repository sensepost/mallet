package com.sensepost.mallet.ssl;

/**
 * Mallet-specific SSL Server handler.
 * 
 * This is actually a Channelinitializer that adds both a SniHandler, and a fallback handler, 
 * in case the SSL Client does not include an SNI message in the handshake. If it cannot
 * read the SNI message, SniHandler simply tries to get a default (null) context from the 
 * mapping. If it fails (as it should!), SniHandler throws an SniCompletionEvent with the 
 * Exception as a property. 
 * 
 * The fallback handler catches this Event, and then catches the subsequent DecoderException
 * that is fired, before replacing the SniHandler with an SslHandler configured according to
 * the hostname/address in the SOCKS ConnectRequest that was previously seen.
 */

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.SniCompletionEvent;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.Mapping;
import io.netty.util.ReferenceCountUtil;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import com.sensepost.mallet.ChannelAttributes;
import com.sensepost.mallet.ConnectRequest;

public class SslServerHandler extends ChannelInitializer<Channel> {

	private Mapping<? super String, ? extends SslContext> mapping;
	private String hostname = null;
	private SniHandler sniHandler;
	
	public SslServerHandler(Mapping<? super String, ? extends SslContext> mapping) {
		this.mapping = mapping;
	}

	public SslServerHandler(Mapping<? super String, ? extends SslContext> mapping, String hostname) {
		this(mapping);
		this.hostname = hostname;
	}

	@Override
	protected void initChannel(Channel ch) throws Exception {
		ChannelPipeline p = ch.pipeline();
		String me = p.context(this).name();
		p.addAfter(me, null, new TargetSslHandler());
		sniHandler = new SniHandler(mapping);
		p.addAfter(me, null, sniHandler);
	}


	private class TargetSslHandler extends ChannelInboundHandlerAdapter {

		private boolean sniException = false;

		@Override
		public void userEventTriggered(ChannelHandlerContext ctx, Object evt)
				throws Exception {
			if (evt instanceof SniCompletionEvent) {
				SniCompletionEvent sce = (SniCompletionEvent) evt;
				sniException = sce.cause() != null;
				if (!sniException)
					ctx.pipeline().remove(this);
			}
			super.userEventTriggered(ctx, evt);
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
				throws Exception {
			if (sniException) {
				if (cause instanceof DecoderException) {
					ConnectRequest cr = ctx.channel().attr(ChannelAttributes.TARGET).get();
					if (cr != null) {
						SocketAddress sa = cr.getTarget();
						if (sa instanceof InetSocketAddress) {
							InetSocketAddress isa = (InetSocketAddress) sa;
							String target = isa.getHostString();
							SslContext context = mapping.map(target);
							if (context != null) {
								replaceHandler(ctx, target, context);
								return;
							}
						} else {
							throw new RuntimeException("Can't deal with non-InetSocketAddress targets: " + sa);
						}
					} else {
						// no target specified, let's use the default
						SslContext context = mapping.map(hostname);
						if (context != null) {
							replaceHandler(ctx, hostname, context);
							return;
						}
					}
				}
			}
			super.exceptionCaught(ctx, cause);
		}
		
	    protected void replaceHandler(ChannelHandlerContext ctx, String hostname, SslContext sslContext) throws Exception {
	    	SslHandler sslHandler = null;
	        try {
	            sslHandler = sslContext.newHandler(ctx.alloc());
	            ctx.pipeline().replace(this, SslHandler.class.getName(), sslHandler);
	            sslHandler = null;
	        } finally {
	            // Since the SslHandler was not inserted into the pipeline the ownership of the SSLEngine was not
	            // transferred to the SslHandler.
	            // See https://github.com/netty/netty/issues/5678
	            if (sslHandler != null) {
	                ReferenceCountUtil.safeRelease(sslHandler.engine());
	            }
	        }
	        // remove this last, otherwise it triggers a read event for any pending bytes, which gets
	        // handled by the next handler, before we can add our sslHandler!
	        ctx.pipeline().remove(sniHandler);
	    }

	}
}
