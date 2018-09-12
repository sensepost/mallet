package com.sensepost.mallet.handlers.http;

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
		ChannelHandler[] handlers = gl.getNextHandlers(this, option);
		String name = ctx.name();
		List<String> names = ctx.pipeline().names();
		int pos = names.indexOf(name);
		ChannelHandler exceptionCatcher = null;
		if (pos > -1 && pos < names.size() - 1) {
			String next = names.get(pos + 1);
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
