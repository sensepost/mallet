package com.sensepost.mallet.ssl;

import java.net.InetSocketAddress;

import com.sensepost.mallet.ChannelAttributes;
import com.sensepost.mallet.ConnectRequest;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.util.AsyncMapping;
import io.netty.util.Mapping;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

public class SslServerHandler extends SniHandler {

	private String hostname = null;
	
	public SslServerHandler(Mapping<? super String, ? extends SslContext> mapping) {
		super(mapping);
	}

	public SslServerHandler(Mapping<? super String, ? extends SslContext> mapping, String hostname) {
		super(mapping);
		this.hostname = hostname;
	}

    /**
     * The default implementation will simply call {@link AsyncMapping#map(Object, Promise)} but
     * users can override this method to implement custom behavior.
     *
     * @see AsyncMapping#map(Object, Promise)
     */
    @Override
    protected Future<SslContext> lookup(ChannelHandlerContext ctx, String hostname) throws Exception {
    	if (this.hostname != null)
    		return super.lookup(ctx, this.hostname);
    	if (hostname != null)
    		return super.lookup(ctx, hostname);
    	
    	ConnectRequest cr = ctx.channel().attr(ChannelAttributes.TARGET).get();
    	if (cr != null)
    		return super.lookup(ctx, ((InetSocketAddress)cr.getTarget()).getHostString());
    	
    	throw new RuntimeException("Can't find a server context for a null hostname");
    }

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof ConnectRequest) {
			ConnectRequest cr = (ConnectRequest) evt;
			hostname = ((InetSocketAddress)cr.getTarget()).getHostString();
		}
		super.userEventTriggered(ctx, evt);
	}


}
