package com.sensepost.mallet.handlers.iso8583;

import java.util.List;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

@ChannelHandler.Sharable
public class Iso8583Encoder extends MessageToMessageEncoder<Iso8583Message> {

	@Override
	protected void encode(ChannelHandlerContext ctx, Iso8583Message msg, List<Object> out) throws Exception {
		out.add(msg.encodeAsString());
	}
}