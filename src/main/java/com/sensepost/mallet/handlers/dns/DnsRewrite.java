package com.sensepost.mallet.handlers.dns;

import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsSection;


public class DnsRewrite extends ChannelDuplexHandler {

	private DnsQuestion question = null;
	
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg)
        throws Exception {
        if (msg instanceof DatagramDnsQuery) {
            DatagramDnsQuery query = (DatagramDnsQuery) msg;
            int c = query.count(DnsSection.QUESTION);
            for (int i=0; i<c; i++) {
            	DnsRecord record = query.recordAt(DnsSection.QUESTION, i);
            	if (record instanceof DefaultDnsQuestion) {
            		DnsQuestion ddq = (DefaultDnsQuestion) record;
            		if (ddq.name().equals("google.com.")) {
            			question = ddq;
            			DefaultDnsQuestion replacement = new DefaultDnsQuestion("www.sensepost.com", ddq.type());
            			query.setRecord(DnsSection.QUESTION, i, replacement);
            		} else 
            			question = null;
            	}
            }
        }
        super.channelRead(ctx, msg);
    }

	@Override
	public void write(ChannelHandlerContext ctx, Object msg,
			ChannelPromise promise) throws Exception {
		if (msg instanceof AddressedEnvelope) {
			AddressedEnvelope e = (AddressedEnvelope) msg;
			Object content = e.content();
			if (content instanceof DatagramDnsResponse) {
				DatagramDnsResponse ddr = (DatagramDnsResponse) content;
				if (question != null) {
					if (ddr.count(DnsSection.QUESTION) == 1)
						ddr.setRecord(DnsSection.QUESTION, question);
					else
						System.out.println("More than one record in the question section! ");
				}
			}
		}
		super.write(ctx, msg, promise);
	}
}

