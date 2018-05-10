package com.sensepost.mallet.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.LinkedList;

public class ComplexBinaryModificationHandler extends ChannelDuplexHandler {

	// NB match and replace MUST be the same length!
	private byte[] match = "These bytes".getBytes(), replace = "Those bytes"
			.getBytes();

	private boolean modifyRead = true, modifyWrite = true;

	private Status readStatus = new Status(match), writeStatus = new Status(
			match);

	@Override
	public void write(ChannelHandlerContext ctx, Object msg,
			ChannelPromise promise) throws Exception {
		if (modifyWrite && msg instanceof ByteBuf) {
			writeStatus.bufs.add(new BufAndPromise((ByteBuf) msg, promise));
			while (find(writeStatus)) {
				replace(writeStatus, replace);
			}
			while (writeStatus.completeBufs > 0) {
				BufAndPromise bap = writeStatus.bufs.removeFirst();
				super.write(ctx, bap.b, bap.p);
				writeStatus.completeBufs--;
			}
		} else
			super.write(ctx, msg, promise);
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg)
			throws Exception {
		if (modifyRead && msg instanceof ByteBuf) {
			readStatus.bufs.add(new BufAndPromise((ByteBuf) msg, null));
			while (find(readStatus)) {
				replace(readStatus, replace);
			}
			while (readStatus.completeBufs > 0) {
				BufAndPromise bap = readStatus.bufs.removeFirst();
				super.channelRead(ctx, bap.b);
				readStatus.completeBufs--;
			}
		} else
			super.channelRead(ctx, msg);
	}

	int bufIndex(Status status) {
		int offset = status.matched;
		for (int i = status.completeBufs; i < status.bufs.size(); i++) {
			offset -= (status.bufs.get(i).b.readableBytes() - (i == 0 ? status.start
					: 0));
			if (offset < 0)
				return i;
			if (offset == 0)
				return i + 1;
		}
		if (offset == 0)
			return -1;
		throw new ArrayIndexOutOfBoundsException(
				"Ran out of bufs with offset = " + offset + "\nStatus: " + status);
	}

	boolean find(Status status) {
		do {
			// Find the ByteBuffer we should be looking in
			int bufIndex = bufIndex(status);
			if (bufIndex < 0)
				return false;
			ByteBuf bb = status.bufs.get(bufIndex).b;

			// if it is the first buffer, we should start at the offset
			// that we started matching at, otherwise, at the beginning of the
			// buffer
			int start = (bufIndex == 0 ? status.start : 0);
			int end = bb.writerIndex();
			for (int i = start; i < end; i++) {
				if (bb.getByte(i) != status.match[status.matched]) { // no match
					status.matched = 0;
					// Restart matching at the next index
					status.start++;
					// if we are only working in a single buffer
					// reset to the point we first matched at, and restart
					if (status.matched > 0 && bufIndex == 0) {
						i = status.start;
					}
					// if we have reached the end of a buffer, with NO matches
					// mark the buffer as complete, it can be passed along the
					// pipeline
					int firstBufSize = status.bufs.get(status.completeBufs).b.readableBytes();
					if (status.start == firstBufSize) {
						status.start -= firstBufSize;
						status.completeBufs++;
						break;
					}
				} else {
					status.matched++;
					if (status.matched == status.match.length)
						return true;
				}
			}
		} while (status.completeBufs < status.bufs.size() && status.matched == 0);
		return false;
	}

	void replace(Status status, byte[] replace) {
		int off = 0;
		for (int i = status.completeBufs; i<status.bufs.size(); i++) {
			ByteBuf bb = status.bufs.get(i).b;
			int start = (off == 0 ? status.start : 0);
			int len = Math.min(replace.length - off, bb.readableBytes() - start);
			bb.setBytes(start, replace, off, len);
			if (start + len == bb.readableBytes()) {
				status.completeBufs++;
				status.start = 0;
			} else
				status.start = start + len;
			off += len;
			if (off == replace.length)
				break;
		}
		status.matched = 0;
	}

	static class BufAndPromise {
		ByteBuf b;
		private ChannelPromise p;

		public BufAndPromise(ByteBuf b, ChannelPromise p) {
			this.b = b;
			this.p = p;
		}
	}

	static class Status {
		LinkedList<BufAndPromise> bufs = new LinkedList<>();
		byte[] match;
		int start = 0;
		int matched = 0;
		int completeBufs = 0;

		public Status(byte[] match) {
			this.match = match;
		}

		public String toString() {
			return "Bufsize(" + bufs.size() + "), start=" + start
					+ ", matched=" + matched + ", pending=" + completeBufs;
		}
	}
}
