package com.sensepost.mallet;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandType;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequest;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequest;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthStatus;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

@ChannelHandler.Sharable
public final class SocksServerHandler extends SimpleChannelInboundHandler<SocksMessage> {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(SocksServerHandler.class);

	public SocksServerHandler() {
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx, SocksMessage socksRequest) throws Exception {
		switch (socksRequest.version()) {
		case SOCKS4a:
			Socks4CommandRequest socks4CmdRequest = (Socks4CommandRequest) socksRequest;
			if (socks4CmdRequest.type() == Socks4CommandType.CONNECT) {
				ctx.pipeline().remove(this);
				ctx.fireChannelRead(socks4CmdRequest);
			} else {
				logger.info("Unsupported Socks4a request type : " + socks4CmdRequest.type());
				ctx.close();
			}
			break;
		case SOCKS5:
			if (socksRequest instanceof Socks5InitialRequest) {
				/*
				 * auth support example 
				 * 
				 * ctx.pipeline().addFirst(new Socks5PasswordAuthRequestDecoder()); 
				 * ctx.write(new DefaultSocks5AuthMethodResponse(Socks5AuthMethod.PASSWORD));
				 */
				ctx.pipeline().addBefore(ctx.name(), "socks5commandrequestdecoder", new Socks5CommandRequestDecoder());
				ctx.write(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH));
			} else if (socksRequest instanceof Socks5PasswordAuthRequest) {
				ctx.pipeline().addBefore(ctx.name(), "socks5commandrequestdecoder", new Socks5CommandRequestDecoder());
				ctx.write(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));
			} else if (socksRequest instanceof Socks5CommandRequest) {
				Socks5CommandRequest socks5CmdRequest = (Socks5CommandRequest) socksRequest;
				if (socks5CmdRequest.type() == Socks5CommandType.CONNECT) {
					ctx.pipeline().remove(this);
					ctx.fireChannelRead(socks5CmdRequest);
				} else {
					logger.info("Unsupported Socks5 Command Request type : " + socks5CmdRequest.type());
					ctx.close();
				}
			} else {
				logger.info("Unsupported Socks5 Request type : " + socksRequest.toString());
				ctx.close();
			}
			break;
		case UNKNOWN:
			logger.info("Unsupported Socks version : " + socksRequest.version());
			ctx.close();
			break;
		}
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) {
		ctx.flush();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable throwable) {
		logger.debug("Exception caught", throwable);
		throwable.printStackTrace();
		ctx.channel().close();
	}
}