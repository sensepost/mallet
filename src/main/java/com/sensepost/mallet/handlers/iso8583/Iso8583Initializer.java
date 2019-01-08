package com.sensepost.mallet.handlers.iso8583;

/**
 * This class sets up a encoder/decoder pair for ISO8583 messages.
 * 
 * Importantly, since the parser is not comprehensive, and fails in some cases,
 * it uses a fall-back mechanism of converting the incoming bytes into a String
 * before attempting to parse that String into an ISO8583Message.
 * 
 * It also validates that it can re-encode the ISO8583 message back to the original 
 * String, and if it fails to do so, it simply forwards the received String up the 
 * pipeline. In this way, we ensure that the stream is not broken by forwarding
 * messages that we know will not be correctly re-encoded.
 * 
 * A User Event is generated for each message that is not correctly decoded, to 
 * facilitate debugging of why it was not correctly decoded or encoded.
 */

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

public class Iso8583Initializer extends ChannelInitializer<Channel> {

	@Override
	protected void initChannel(Channel ch) throws Exception {
		ChannelPipeline pipeline = ch.pipeline();
		String name = pipeline.context(this).name();
		pipeline.addBefore(name, null, new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 2, 0, 2));
		pipeline.addBefore(name, null, new LengthFieldPrepender(2));
		pipeline.addBefore(name, null, new StringEncoder(CharsetUtil.US_ASCII));
		pipeline.addBefore(name, null, new StringDecoder(CharsetUtil.US_ASCII));
		pipeline.addAfter(name, null, new Iso8583Encoder());
		pipeline.addAfter(name, null, new Iso8583Decoder());
	}

}
