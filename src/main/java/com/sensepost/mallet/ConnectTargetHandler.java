package com.sensepost.mallet;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * Handler to receive a Socks ConnectRequest, and save the requested address as
 * a Channel Attribute.
 * 
 * It confirms that the connection has been successfully made, even though no
 * such thing has happened. This is done in order to allow data to be read from
 * the client. Care should be taken to address the case where the client waits
 * for the server to write data first, though.
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
			ctx.channel().attr(ChannelAttributes.TARGET).set(cr);
			// We set the promise successful here in case of
			// indeterminate handlers, as the event will run off
			// the end of the pipeline and not be seen by the RelayHandler
			if (!cr.getConnectPromise().isDone())
				cr.getConnectPromise().setSuccess(ctx.channel());
			ctx.pipeline().remove(this);
		}
		super.userEventTriggered(ctx, evt);
	}

}
