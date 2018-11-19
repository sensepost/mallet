package com.sensepost.mallet.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

public class SimpleBinaryModificationHandler extends ChannelDuplexHandler {

	// NB match and replace MUST be the same length!
	private byte[] match, replace;

	private boolean modifyRead, modifyWrite;

	public SimpleBinaryModificationHandler(String match, String replace) {
		this(match, replace, true, false);
	}

	public SimpleBinaryModificationHandler(String match, String replace, boolean matchOnRead, boolean matchOnWrite) {
		this(match == null ? null : match.getBytes(), replace == null ? null : replace.getBytes(), matchOnRead, matchOnWrite);
	}

	public SimpleBinaryModificationHandler(byte[] match, byte[] replace, boolean matchOnRead, boolean matchOnWrite) {
		if (match == null)
			throw new NullPointerException("match");
		if (replace == null)
			throw new NullPointerException("replace");
		if (match.length != replace.length)
			throw new IllegalArgumentException("'match' and 'replace' must be the same length: " + match.length + " != " + replace.length);
		this.match = match;
		this.replace = replace;
		this.modifyRead = matchOnRead;
		this.modifyWrite = matchOnWrite;
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg,
			ChannelPromise promise) throws Exception {
		if (modifyWrite && msg instanceof ByteBuf) {
			findAndReplace(ctx, (ByteBuf)msg, match, replace);
		}
		super.write(ctx, msg, promise);
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg)
			throws Exception {
		if (modifyRead && msg instanceof ByteBuf) {
			findAndReplace(ctx, (ByteBuf)msg, match, replace);
		}
		super.channelRead(ctx, msg);
	}

	void findAndReplace(ChannelHandlerContext ctx, ByteBuf bb, byte[] match, byte[] replace) {
		int start = bb.readerIndex();
		int end = bb.writerIndex();
		int index;
		while (start < end && (index = bb.indexOf(start, end, match[0])) >= start && index + match.length <= end) {
			for (int i = 0; i < match.length; i++) {
				if (bb.getByte(index + i) != match[i]) {
					start++;
					break;
				} else if (i == match.length - 1) {
					if (ctx != null)
						ctx.fireUserEventTriggered("Replaced '" + new String(match) + "' with '" + new String(replace) + "'");
					else
						System.err.println("Replaced '" + new String(match) + "' with '" + new String(replace) + "'");
					bb.setBytes(index, replace, 0, replace.length);
					start += replace.length;
				}
			}
		};

	}
}
