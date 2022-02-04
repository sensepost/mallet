package com.sensepost.mallet.handlers.messagepack;

import java.io.InputStream;
import java.util.List;

import org.msgpack.jackson.dataformat.MessagePackFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

class MessagePackDecoder extends ByteToMessageDecoder {
    
    private ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());

    public MessagePackDecoder() {
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() >  0) {
            InputStream is = new ByteBufInputStream(in);
            while (in.readableBytes() > 0) {
                out.add(objectMapper.readValue(is, Object.class));
            }
        }
    }
}