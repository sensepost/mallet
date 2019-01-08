package com.sensepost.mallet.handlers.iso8583;

/**
 * This class attempts to decode for ISO8583 messages.
 * 
 * Importantly, since the parser is not comprehensive, and fails in some cases,
 * it validates that it can re-encode the ISO8583 message back to the original 
 * String, and if it fails to do so, it simply forwards the received String up the 
 * pipeline. In this way, we ensure that the stream is not broken by forwarding
 * messages that we know will not be correctly re-encoded.
 * 
 * A User Event is generated for each message that is not correctly decoded, to 
 * facilitate debugging of why it was not correctly decoded or encoded.
 */

import java.util.List;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

public class Iso8583Decoder extends MessageToMessageDecoder<String> {

	@Override
	protected void decode(ChannelHandlerContext ctx, String msg, List<Object> out) throws Exception {
		try {
			Iso8583Message m = Iso8583Message.decode(msg);
			if (!msg.equals(m.encodeAsString())) {
				ctx.fireUserEventTriggered("Validation of decoded IsoMessage failed, forwarding as String\n"
						+ "Original : " + msg + "\nParsed   : " + m.encodeAsString());
				out.add(msg);
			} else
				out.add(m);
		} catch (Exception e) {
			e.printStackTrace();
			ctx.fireUserEventTriggered("Failed to decode IsoMessage, forwarding as String\n" + e.getMessage());
			out.add(msg);
		}

	}
}