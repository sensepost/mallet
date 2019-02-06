package com.sensepost.mallet.handlers.http;

import java.util.List;

import com.sensepost.mallet.ChannelAttributes;
import com.sensepost.mallet.graph.GraphLookup;
import com.sensepost.mallet.graph.IndeterminateChannelHandler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.ByteToMessageDecoder;

public class HttpSniffHandler extends ByteToMessageDecoder implements IndeterminateChannelHandler {
	private static final String[] METHODS = new String[] { "GET ", "HEAD", "POST", "OPTIONS", "PUT ", "PATCH", "DELETE",
			"TRACE" };
	private static final String HTTP = "HTTP";
	private static final String DEFAULT = "";
	private static final String[] OPTIONS = new String[] { HTTP, DEFAULT };

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
		ctx.pipeline().replace(this, null, initializer);
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
		// Will use the first four bytes to detect a protocol.
		if (in.readableBytes() < 4) {
			return;
		}
		byte[] bytes = new byte[4];
		in.getBytes(in.readerIndex(), bytes);
		String method = new String(bytes);
		boolean http = false;
		for (int i = 0; i < METHODS.length; i++)
			if (method.equals(METHODS[i].substring(0, 4))) {
				http = true;
				break;
			}
		if (http) {
			optionSelected(ctx, HTTP);
		} else {
			optionSelected(ctx, DEFAULT);
		}
	}

}
