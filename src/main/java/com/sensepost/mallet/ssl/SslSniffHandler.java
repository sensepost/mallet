package com.sensepost.mallet.ssl;

import java.util.List;

import com.sensepost.mallet.ChannelAttributes;
import com.sensepost.mallet.graph.ExceptionCatcher;
import com.sensepost.mallet.graph.GraphLookup;
import com.sensepost.mallet.graph.IndeterminateChannelHandler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.ssl.SslHandler;

public class SslSniffHandler extends ByteToMessageDecoder implements IndeterminateChannelHandler {


	private static final String SSL = "SSL";
	private static final String DEFAULT = "";
	private static final String[] OPTIONS = new String[] { SSL, DEFAULT };
	
	@Override
	public void setOutboundOptions(String[] options) {
	}

	@Override
	public String[] getOutboundOptions() {
		return OPTIONS;
	}

	@Override
	public void optionSelected(ChannelHandlerContext ctx, String option) throws Exception {
		GraphLookup gl = ctx.channel().attr(ChannelAttributes.GRAPH).get();
		if (gl == null)
			throw new NullPointerException("gl");
		ChannelHandler[] handlers = gl.getNextHandlers(this, option);
		String name = ctx.name();
		List<String> names = ctx.pipeline().names();
		int pos = names.indexOf(name);
		ChannelHandler exceptionCatcher = null;
		if (pos > -1 && pos < names.size()-1) {
			String next = names.get(pos+1);
			ChannelHandler nextHandler = ctx.pipeline().get(next);
			if (nextHandler instanceof ExceptionCatcher)
				exceptionCatcher = nextHandler;
		}
		for (int i = handlers.length - 1; i >= 0; i--) {
			try {
				ctx.pipeline().addAfter(name, null, handlers[i]);
			} catch (Exception e) {
				ctx.fireExceptionCaught(e);
				Channel ch = ctx.channel().attr(ChannelAttributes.CHANNEL).get();
				ctx.close();
				if (ch != null && ch.isOpen())
					ch.close();
			}
		}
		ctx.pipeline().remove(name);
		if (exceptionCatcher != null)
			ctx.pipeline().remove(exceptionCatcher);
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // Will use the first five bytes to detect a protocol.
        if (in.readableBytes() < 5) {
            return;
        }
        if (SslHandler.isEncrypted(in)) {
        	optionSelected(ctx, SSL);
        } else {
        	optionSelected(ctx, DEFAULT);
        }
	}

}
