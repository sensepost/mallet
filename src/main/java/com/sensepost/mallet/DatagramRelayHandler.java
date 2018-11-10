package com.sensepost.mallet;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.script.Bindings;

import com.sensepost.mallet.graph.GraphLookup;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

@Sharable
public class DatagramRelayHandler
		extends
		SimpleChannelInboundHandler<AddressedEnvelope<Object, InetSocketAddress>> {

	private static final InternalLogger logger = InternalLoggerFactory
			.getInstance(DatagramRelayHandler.class);

	private Channel local = null;

	private Map<InetSocketAddress, Channel> channelMap = new HashMap<>();
	private Map<InetSocketAddress, InetSocketAddress> endPointMap = new HashMap<>();

	private ChannelHandler[] clientInitializer;

	public DatagramRelayHandler() {
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		if (local == null)
			local = ctx.channel();
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		if (local.equals(ctx.channel())) {
			Iterator<Entry<InetSocketAddress, Channel>> it = channelMap
					.entrySet().iterator();
			while (it.hasNext()) {
				Entry<InetSocketAddress, Channel> e = it.next();
				e.getValue().close();
				endPointMap.remove(e.getKey());
				it.remove();
			}
		} else {
			synchronized (channelMap) {
				InetSocketAddress remote = (InetSocketAddress) ctx.channel()
						.attr(ChannelAttributes.REMOTE_ADDRESS).get();
// 				InetSocketAddress sender = endPointMap.remove(remote); // FIXME Clean up properly
				Channel c = channelMap.remove(remote);
				c.close();
			}
		}
		Channel other = ctx.channel().attr(ChannelAttributes.CHANNEL).get();
		if (other != null && other.isOpen()) {
			other.close();
		}
		super.channelInactive(ctx);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
			throws Exception {
		System.err
				.println("Caught an exception under context " + ctx.channel());
		cause.printStackTrace();
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt)
			throws Exception {
		if (evt instanceof IdleStateEvent) {
			ctx.channel().closeFuture().addListener(new ChannelFutureListener() {
				
				@Override
				public void operationComplete(ChannelFuture future) throws Exception {
					if (!future.isSuccess()) {
						future.cause().printStackTrace();
					}
				}
			});
			ctx.channel().close();
		}
	}

	protected AddressedEnvelope<Object, InetSocketAddress> forward(
			AddressedEnvelope<Object, InetSocketAddress> msg,
			InetSocketAddress sender, InetSocketAddress recipient) {
		Object content = msg.content();
		return new DefaultAddressedEnvelope<Object, InetSocketAddress>(content,
				recipient, sender);
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx,
			AddressedEnvelope<Object, InetSocketAddress> msg) throws Exception {
		ReferenceCountUtil.retain(msg);
		if (local.equals(ctx.channel())) {
			ConnectRequest target = ctx.channel()
					.attr(ChannelAttributes.TARGET).get();
			if (target == null)
				throw new NullPointerException("target");

			GraphLookup gl = ctx.channel().attr(ChannelAttributes.GRAPH).get();

			// get the client initializer (once only)
			clientInitializer = gl
					.getClientChannelInitializer(DatagramRelayHandler.this, true);

			// inbound packet on listening address
			InetSocketAddress sender = msg.sender();
			InetSocketAddress recipient = (InetSocketAddress) target
					.getTarget();
			if (recipient.isUnresolved())
				recipient = new InetSocketAddress(recipient.getHostString(),
						recipient.getPort());
			Channel channel;
			synchronized (channelMap) {
				channel = channelMap.get(sender);
			}
			if (channel == null) {
				createOutboundChannel(ctx, sender, recipient).addListener(
						new BindListener(msg, sender, recipient));
			} else {
				channel.writeAndFlush(forward(msg, sender, recipient))
						.addListener(new WriteListener());
			}
		} else {
			// response to a forwarded packet
			InetSocketAddress sender = (InetSocketAddress) local.localAddress();
			InetSocketAddress recipient;
			synchronized (endPointMap) {
				recipient = endPointMap.get(msg.sender());
			}
			local.writeAndFlush(forward(msg, sender, recipient)).addListener(
					new WriteListener());
		}
	}

	protected ChannelFuture createOutboundChannel(
			final ChannelHandlerContext ctx, final SocketAddress remote,
			final SocketAddress target) {
		final GraphLookup gl = ctx.channel().attr(ChannelAttributes.GRAPH)
				.get();
		ChannelInitializer<Channel> initializer = new ChannelInitializer<Channel>() {

			@Override
			protected void initChannel(Channel ch) throws Exception {
				ch.attr(ChannelAttributes.GRAPH).set(gl);
				ch.attr(ChannelAttributes.REMOTE_ADDRESS).set(remote);
				Bindings scriptContext = ctx.channel().attr(ChannelAttributes.SCRIPT_CONTEXT).get();
				ch.attr(ChannelAttributes.SCRIPT_CONTEXT).set(scriptContext);
				ch.pipeline().addLast(clientInitializer);
				
				InterceptController ic = (InterceptController) scriptContext.get("InterceptController");
				ic.addChannel(ch.id().asLongText(), ch.localAddress(), ch.remoteAddress());
			}
		};

		return new Bootstrap().channel(NioDatagramChannel.class)
				.group(ctx.channel().eventLoop()).handler(initializer).bind(0);
	}

	private class BindListener implements ChannelFutureListener {

		private AddressedEnvelope<Object, InetSocketAddress> msg;
		private InetSocketAddress sender, recipient;

		public BindListener(AddressedEnvelope<Object, InetSocketAddress> msg,
				InetSocketAddress sender, InetSocketAddress recipient) {
			this.msg = msg;
			this.sender = sender;
			this.recipient = recipient;
		}

		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			if (future.isSuccess()) {
				Channel channel = future.channel();
				synchronized (channelMap) {
					channelMap.put(sender, channel);
					endPointMap.put(recipient, sender);
				}
				channel.writeAndFlush(
						forward(msg,
								(InetSocketAddress) channel.localAddress(),
								recipient)).addListener(new WriteListener());
			}
		}

	}

	private class WriteListener implements ChannelFutureListener {

		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			if (!future.isSuccess()) {
				future.cause().printStackTrace();
			}
		}

	}
}
