package com.sensepost.mallet.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

public class SimpleBinaryModificationHandler extends ChannelDuplexHandler {

	// NB match and replace MUST be the same length!
	private byte[] match = "These bytes".getBytes(), replace = "Those bytes".getBytes();

	private boolean modifyRead = false, modifyWrite = false;

	public SimpleBinaryModificationHandler() {}
	
	public SimpleBinaryModificationHandler(String match, String replace) {
		this.match = match.getBytes();
		this.replace = replace.getBytes();
	}
	
	@Override
	public void write(ChannelHandlerContext ctx, Object msg,
			ChannelPromise promise) throws Exception {
		if (modifyWrite && msg instanceof ByteBuf) {
			findAndReplace((ByteBuf)msg, match, replace);
		}
		super.write(ctx, msg, promise);
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg)
			throws Exception {
		if (modifyRead && msg instanceof ByteBuf) {
			findAndReplace((ByteBuf)msg, match, replace);
		}
		super.channelRead(ctx, msg);
	}

	void findAndReplace(ByteBuf bb, byte[] match, byte[] replace) {
		int start = bb.readerIndex();
		int end = bb.writerIndex();
		int index;
		while (start < end && (index = bb.indexOf(start, end, match[0])) >= start && index + match.length <= end) {
			for (int i = 0; i < match.length; i++) {
				if (bb.getByte(index + i) != match[i]) {
					start++;
					break;
				} else if (i == match.length - 1) {
					bb.setBytes(index, replace, 0, replace.length);
					start += replace.length;
				}
			}
		};

	}
}
