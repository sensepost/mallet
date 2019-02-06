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
import io.netty.channel.ChannelInitializer;
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
		ChannelInitializer<Channel> initializer = gl.getNextHandlers(this, option);
		String name = ctx.name();
		ctx.pipeline().addAfter(name, null, initializer);
		ctx.pipeline().remove(name);
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
