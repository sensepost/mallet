package com.sensepost.mallet;

import java.net.InetSocketAddress;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

@Sharable
public class Connector extends ChannelInitializer<SocketChannel> {

	private InterceptHandler interceptHandler;
	private Bootstrap b;
	private NioEventLoopGroup workerGroup = new NioEventLoopGroup();

	public Connector(InterceptHandler interceptHandler) {
		this.interceptHandler = interceptHandler;
		b = new Bootstrap().group(workerGroup).channel(NioSocketChannel.class)
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000).option(ChannelOption.SO_KEEPALIVE, true)
				.option(ChannelOption.ALLOW_HALF_CLOSURE, true).option(ChannelOption.AUTO_READ, false);
	}

	@Override
	protected void initChannel(final SocketChannel serverChannel) throws Exception {
		InetSocketAddress target = serverChannel.parent().attr(ChannelAttributes.TARGET).get();
		if (target == null)
			throw new NullPointerException("target");
		b.handler(interceptHandler);
		ChannelFuture cf = b.remoteAddress(target).connect();
		cf.addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) {
				if (future.isSuccess()) {
					Channel clientChannel = future.channel();
					serverChannel.attr(ChannelAttributes.CHANNEL).set(clientChannel);
					clientChannel.attr(ChannelAttributes.CHANNEL).set(serverChannel);

					/**
					 * NB: The client channel should be read "backwards", due to
					 * it being a "write" channel from the perspective of the
					 * InterceptHandler.
					 * 
					 * In other words, the InterceptHandler is shown as the last
					 * item in the pipeline, then the second last handler is
					 * called with any objects written, then the third last,
					 * etc, until the first handler sends it to the server.
					 * 
					 * The first handler in the client pipeline then reads the
					 * response from the server, passes it to the second and
					 * subsequent handlers, etc until the final result is passed
					 * back to the InterceptHandler.
					 * 
					 * As a result, any handlers added to the client pipeline
					 * should be done using addFirst(), and any added to the
					 * server pipeline should be done using addLast().
					 * 
					 * e.g.
					 * 
					 * clientChannel.pipeline().addFirst(new HttpClientCodec());
					 * serverChannel.pipeline().addLast(new HttpServerCodec());
					 */
					
					clientChannel.pipeline().addFirst(new HttpClientCodec(), new HttpObjectAggregator(1024*1024));
					serverChannel.pipeline().addLast(new HttpServerCodec(), new HttpObjectAggregator(1024*1024));

					serverChannel.pipeline().addLast(interceptHandler);

					serverChannel.config().setAutoRead(true);
					clientChannel.config().setAutoRead(true);
				} else {
					serverChannel.close();
				}
			}
		});
	}

}
