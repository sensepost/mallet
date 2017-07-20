/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.sensepost.mallet;

import java.net.InetSocketAddress;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.AbstractSocksMessage;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import io.netty.handler.codec.socksx.v4.Socks4ServerDecoder;
import io.netty.handler.codec.socksx.v4.Socks4ServerEncoder;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

@ChannelHandler.Sharable
public final class SocksServerConnectHandler extends SimpleChannelInboundHandler<SocksMessage> {

	private static final InternalLogger logger = InternalLoggerFactory.getInstance(SocksServerConnectHandler.class);

	private final DefaultSocks4CommandResponse socks4Success = new DefaultSocks4CommandResponse(
			Socks4CommandStatus.SUCCESS);
	private final DefaultSocks4CommandResponse socks4Failure = new DefaultSocks4CommandResponse(
			Socks4CommandStatus.REJECTED_OR_FAILED);

	@Override
	public void channelRead0(final ChannelHandlerContext ctx, final SocksMessage message) throws Exception {
		final AbstractSocksMessage success, failure;

		String host = null;
		int port = 0;
		Class<? extends ChannelHandler> socksEncoder = null;

		if (message instanceof Socks4CommandRequest) {
			Socks4CommandRequest request = (Socks4CommandRequest) message;

			success = socks4Success;
			failure = socks4Failure;

			host = request.dstAddr();
			port = request.dstPort();

			// we're not going to need this anymore
			ctx.pipeline().remove(Socks4ServerDecoder.class);
			// and we can remove this once the response message has been written
			socksEncoder = Socks4ServerEncoder.class;

		} else if (message instanceof Socks5CommandRequest) {
			Socks5CommandRequest request = (Socks5CommandRequest) message;

			success = new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, request.dstAddrType());
			failure = new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, request.dstAddrType());

			host = request.dstAddr();
			port = request.dstPort();

			// we're not going to need this anymore
			ctx.pipeline().remove(Socks5CommandRequestDecoder.class);
			// and we can remove this once the response message has been written
			socksEncoder = Socks5ServerEncoder.class;

		} else {
			logger.info("Unknown SOCKS message received: " + message);
			ctx.close();
			return;
		}

		final Promise<Channel> connectPromise = ctx.executor().newPromise();
		// set up a method to notify the inbound channel if/when the outbound
		// channel is connected, or if the connection should be closed.
		connectPromise.addListener(new SocksConnectionResponseSender(ctx, success, failure, socksEncoder));

		ConnectRequest tp = new ConnectRequest(InetSocketAddress.createUnresolved(host, port), connectPromise);
		ctx.fireUserEventTriggered(tp);

		ctx.pipeline().remove(this);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		logger.info(cause);
		ctx.close();
	}

	private class SocksConnectionResponseSender implements FutureListener<Channel> {

		private ChannelHandlerContext ctx;
		private AbstractSocksMessage success, failure;
		private Class<? extends ChannelHandler> socksEncoder;

		public SocksConnectionResponseSender(ChannelHandlerContext ctx, AbstractSocksMessage success,
				AbstractSocksMessage failure, Class<? extends ChannelHandler> socksEncoder) {
			this.ctx = ctx;
			this.success = success;
			this.failure = failure;
			this.socksEncoder = socksEncoder;
		}

		@Override
		public void operationComplete(final Future<Channel> future) throws Exception {
			// we have been told by upstream that the connection is open
			if (future.isSuccess()) {
				// send the socks client a message to confirm that the connect
				// was successful
				ctx.writeAndFlush(success).addListener(new ChannelFutureListener() {
					@Override
					public void operationComplete(ChannelFuture future) {
						// once the success message is written, remove the
						// encoder
						if (future.isSuccess()) {
							ctx.pipeline().remove(socksEncoder);
						} else {
							SocksServerConnectHandler.logger.error("Error writing SOCKS success message",
									future.cause());
							ctx.channel().close();
						}
					}
				});
			} else {
				// send a failure message, and close the connection
				ctx.writeAndFlush(failure).addListener(ChannelFutureListener.CLOSE);
				logger.error("Failed to connect to server", future.cause());
			}
		}
	}

}