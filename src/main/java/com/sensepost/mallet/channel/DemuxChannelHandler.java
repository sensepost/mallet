package com.sensepost.mallet.channel;

import java.util.HashMap;
import java.util.Map;

import com.sensepost.mallet.InterceptController;
import com.sensepost.mallet.InterceptHandler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;

public class DemuxChannelHandler extends ChannelDuplexHandler {

	public final static AttributeKey<String> ID = AttributeKey.valueOf("DemuxChannelIdentifier");

	private static Map<String, EmbeddedChannel> channelMap = new HashMap<>();

	public static DemuxOptions ONLY_READS = DemuxOptions.ONLY_READS;
	public static DemuxOptions ONLY_WRITES = DemuxOptions.ONLY_WRITES;
	public static DemuxOptions READ_WRITE = DemuxOptions.READ_WRITE;
	
	public static enum DemuxOptions {
		ONLY_READS,
		ONLY_WRITES,
		READ_WRITE
	}
	
	private InterceptController controller;

	private DemuxOptions options;

	public DemuxChannelHandler(InterceptController controller) {
		this.controller = controller;
		this.options = DemuxOptions.READ_WRITE;
	}

	public DemuxChannelHandler(InterceptController controller, DemuxOptions options) {
		this.controller = controller;
		this.options = options;
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg, final ChannelPromise promise) throws Exception {
		switch (options) {
		case ONLY_WRITES:
		case READ_WRITE: {
			EmbeddedChannel channel = getChannel(ctx);
			ReferenceCountUtil.retain(msg);
			channel.writeOutbound(msg);
			ChannelPromise newPromise = null;
			while ((msg = channel.readOutbound()) != null) {
				System.out.println("Read an outbound message from the mux channel: " + msg.hashCode());
				ReferenceCountUtil.retain(msg);
				ctx.write(msg, newPromise = ctx.channel().newPromise());
			}
			if (newPromise == null)
				promise.setSuccess();
			else
				newPromise.addListener(new ChannelFutureListener() {
					
					@Override
					public void operationComplete(ChannelFuture future) throws Exception {
						if (future.isSuccess()) {
							System.out.println("Written!");
							promise.setSuccess();
						} else if (future.cause() != null) {
							promise.setFailure(future.cause());
						}
					}
				});
			break;
		}
		case ONLY_READS:
			ctx.write(msg, promise);
		}
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		switch (options) {
		case ONLY_READS:
		case READ_WRITE: {
			EmbeddedChannel channel = getChannel(ctx);
			channel.writeInbound(msg);
			while ((msg = channel.readInbound()) != null) {
				ReferenceCountUtil.retain(msg);
				ctx.fireChannelRead(msg);
			}
			break;
		}
		case ONLY_WRITES:
			ReferenceCountUtil.retain(msg);
			ctx.fireChannelRead(msg);
			break;
		}
	}

	private EmbeddedChannel getChannel(ChannelHandlerContext ctx) {
		final String id = ctx.channel().attr(ID).get();
		if (id == null)
			throw new NullPointerException("AttributeKey(DemuxChannelHandler.ID) must be set!");
		EmbeddedChannel channel;
		synchronized(channelMap) {
			channel = channelMap.get(id);
			if (channel == null) {
				channel = new EmbeddedChannel(new DemuxChannelId(id), new ChannelInitializer<Channel>() {

					@Override
					protected void initChannel(Channel ch) throws Exception {
						controller.addChannel(ch.id().asLongText(), ch.localAddress(), ch.remoteAddress());
					}
					
				}, new InterceptHandler(controller));
				channelMap.put(id, channel);
			}
			return channel;
		}
	}
	
	private static class DemuxChannelId implements ChannelId {

		/**
		 * 
		 */
		private static final long serialVersionUID = 2177254735832618983L;

		private final String id;
		
		public DemuxChannelId(String id) {
			this.id = id;
		}

		@Override
		public int compareTo(ChannelId o) {
			return this.asLongText().compareTo(o.asLongText());
		}

		@Override
		public String asShortText() {
			return id;
		}

		@Override
		public String asLongText() {
			return id;
		}
		
	}
	
	private static class ReferenceCountTracker implements ChannelFutureListener {

		private Object msg;
		private int refCnt;
		
		public ReferenceCountTracker(Object msg) {
			this.msg = msg;
			this.refCnt = ReferenceCountUtil.refCnt(msg);
		}

		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			int refCnt = ReferenceCountUtil.refCnt(msg);
			if (this.refCnt != refCnt) {
				System.out.println("Reference Count for " + msg + " was originally " + this.refCnt + ", is now " + refCnt);
				if (msg instanceof ByteBuf)
					System.out.println(ByteBufUtil.prettyHexDump((ByteBuf)msg));
			}
		}
		
	}
}
