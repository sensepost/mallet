package com.sensepost.mallet.handlers.messagepack;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.msgpack.jackson.dataformat.MessagePackFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.CharsetUtil;

class MessagePackDecoder extends ByteToMessageDecoder {
    
    private ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());

    public MessagePackDecoder() {
    }

/*
    private Object readValue(ByteBuf in) {
        int type = in.readUnsignedByte();
        if ((type & 0x80) == 0) { // positive fixint
            return Integer.valueOf(type & 0x7F);
        } else if ((type & 0xF0) == 0x80) { // fixmap
            int len = (type & 0xF);
            Map<Object, Object> map = new HashMap<>();
            for (int i=0; i<len; i++) {
                map.put(readValue(in), readValue(in));
            }
            return map;
        } else if ((type & 0xF0) == 0x90) { // fixarray
            int len = (type & 0xF);
            List<Object> list = new LinkedList<>();
            for (int i=0; i<len; i++) {
                list.add(readValue(in));
            }
            return list;
        } else if ((type & 0xE0) == 0xa0) { // fixstr
            int len = (type & 0x1F);
            return in.readCharSequence(len, CharsetUtil.UTF_8);
        } else if (type == 0xc0) { // nil
            return null;
        } else if (type == 0xc1) { // never used
            throw new UnsupportedOperationException();
        } else if (type == 0xc2) { // false
            return Boolean.FALSE;
        } else if (type == 0xc3) { // true
            return Boolean.TRUE;
        } else if (type == 0xc4) { // bin 8
            int len = in.readUnsignedByte();
            byte[] bytes = new byte[len];
            in.readBytes(bytes);
            return bytes;
        } else if (type == 0xc5) { // bin 16
            int len = in.readUnsignedShort();
            byte[] bytes = new byte[len];
            in.readBytes(bytes);
            return bytes;
        } else if (type == 0xc6) { // bin 32
            long len = in.readUnsignedInt();
            byte[] bytes = new byte[(int)len];
            in.readBytes(bytes);
            return bytes;
        } else if (type == 0xc7) { // ext 8
            throw new UnsupportedOperationException();
        } else if (type == 0xc8) { // ext 16
            throw new UnsupportedOperationException();
        } else if (type == 0xc9) { // ext 32
            throw new UnsupportedOperationException();
        } else if (type == 0xca) { // float 32
            return in.readFloat();
        } else if (type == 0xcb) { // float 64
            return in.readDouble();
        } else if (type == 0xcc) { // uint8
            return in.readUnsignedByte();
        } else if (type == 0xcd) { // uint16
            return in.readUnsignedShort();
        } else if (type == 0xce) { // uint32
            return in.readUnsignedInt();
        } else if (type == 0xcf) { // uint64
            throw new UnsupportedOperationException();
        } else if (type == 0xd0) { // int8
            return in.readByte();
        } else if (type == 0xd1) { // int16
            return in.readShort();
        } else if (type == 0xd2) { // int32
            return in.readInt();
        } else if (type == 0xd3) { // int64
            throw new UnsupportedOperationException();
        } else if (type == 0xd4) { // fixext1
            throw new UnsupportedOperationException();
        } else if (type == 0xd5) { // fixext2
            throw new UnsupportedOperationException();
        } else if (type == 0xd6) { // fixext4
            throw new UnsupportedOperationException();
        } else if (type == 0xd7) { // fixext8
            throw new UnsupportedOperationException();
        } else if (type == 0xd8) { // fixext16
            throw new UnsupportedOperationException();
        } else if (type == 0xd9) { // str8
            int len = in.readUnsignedByte();
            return in.readCharSequence(len, CharsetUtil.UTF_8).toString();
        } else if (type == 0xda) { // str16
            int len = in.readUnsignedShort();
            return in.readCharSequence(len, CharsetUtil.UTF_8).toString();
        } else if (type == 0xdb) { // str32
            long len = in.readUnsignedInt();
            return in.readCharSequence((int) len, CharsetUtil.UTF_8).toString();
        } else if (type == 0xdc) { // array16
            int len = in.readUnsignedShort();
            List<Object> list = new LinkedList<>();
            for (int i=0; i<len; i++) {
                list.add(readValue(in));
            }
            return list;
        } else if (type == 0xdd) { // array32
            long len = in.readUnsignedInt();
            List<Object> list = new LinkedList<>();
            for (int i=0; i<len; i++) {
                list.add(readValue(in));
            }
            return list;
        } else if (type == 0xde) { // map16
            int len = in.readUnsignedShort();
            Map<Object, Object> map = new HashMap<>();
            for (int i=0; i<len; i++) {
                map.put(readValue(in), readValue(in));
            }
            return map;
        } else if (type == 0xdf) { // map32
            long len = in.readUnsignedInt();
            Map<Object, Object> map = new HashMap<>();
            for (int i=0; i<len; i++) {
                map.put(readValue(in), readValue(in));
            }
            return map;
        } else if ((type & 0xe0) == 0xe0) { // negative fixint
            return - (32 - Integer.valueOf(type & 0x1F));
        }
        throw new UnsupportedOperationException();
    }
    */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() >  0) {
            InputStream is = new ByteBufInputStream(in);
            while (in.readableBytes() > 0) {
                out.add(objectMapper.readValue(is, Object.class));
//            out.add(readValue(in));
            }
        }
    }
}