package com.sensepost.mallet;

import java.net.InetSocketAddress;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * Handler to receive a Socks ConnectRequest, and save the requested address as
 * a Channel Attribute.
 * 
 * It confirms that the connection has been successfully made, even though no
 * such thing has happened. This is done in order to allow data to be read from
 * the client.
 * 
 * Subsequent handlers must retrieve the TARGET Channel Attribute, and make the
 * outbound connection accordingly, before any data can be written.
 * 
 * @author rogan
 *
 */
public class ConnectTargetHandler extends ChannelInboundHandlerAdapter {

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof ConnectRequest) {
			ConnectRequest cr = (ConnectRequest) evt;
			ctx.channel().attr(ChannelAttributes.TARGET)
					.set(InetSocketAddress.createUnresolved(cr.getHost(), cr.getPort()));
			cr.getConnectPromise().setSuccess(ctx.channel());
			ctx.pipeline().remove(this);
		} else
			super.userEventTriggered(ctx, evt);
	}

}
