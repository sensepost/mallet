package com.sensepost.mallet.handlers.messagepack;

import org.msgpack.jackson.dataformat.MessagePackFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

class MessagePackEncoder extends MessageToByteEncoder<Object> {

    private ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());
    
    public MessagePackEncoder() {
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        out.writeBytes(objectMapper.writeValueAsBytes(msg));
    }
    
}