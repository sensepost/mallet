package com.sensepost.mallet.handlers.messagepack;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.CharsetUtil;

class MessagePackEncoder extends MessageToByteEncoder<Object> {

    public MessagePackEncoder() {
    }
    
    private void encodeByte(Byte v, ByteBuf out) {
        if (v < 0) { 
            if(v > -(1 << 5)) { // int5
                out.writeByte(0xe0 | (int)(-v & 0x1F));
            } else {
                out.writeByte(0xd0);
                out.writeByte((int)(v));
            }
        } else {
            out.writeByte(v);
        }
    }
    
    private void encodeShort(Short v, ByteBuf out) {
        if (v >= Byte.MIN_VALUE && v <= Byte.MAX_VALUE) {
            encodeByte(v.byteValue(), out);
        } else if (v < Byte.MIN_VALUE) {
            out.writeByte(0xd1);
            out.writeShort(v);
        } else if (v < (Byte.MAX_VALUE+1) * 2) {
            out.writeByte(0xcc);
            out.writeByte(v & 0xFF);
        } else {
            out.writeByte(0xcd);
            out.writeShort(v);
        }
    }
    
    private void encodeInt(Integer v, ByteBuf out) {
        if (v >= Short.MIN_VALUE && v <= Short.MAX_VALUE) {
            encodeShort(v.shortValue(), out);
        } else if (v < Short.MIN_VALUE) {
            out.writeByte(0xd2);
            out.writeInt(v);
        } else if (v < (Short.MAX_VALUE+1) * 2) {
            out.writeByte(0xcd);
            out.writeShort(v & 0xFFFF);
        } else {
            out.writeByte(0xce);
            out.writeInt(v);
        }
    }
    
    private void encodeLong(Long v, ByteBuf out) {
        if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) {
            encodeInt(v.intValue(), out);
        } else if (v < Integer.MIN_VALUE) {
            out.writeByte(0xd3);
            out.writeLong(v);
        } else if (v < (Integer.MAX_VALUE+1) * 2) {
            out.writeByte(0xce);
            out.writeInt((int)(v & 0xFFFFFFFF));
        } else {
            out.writeByte(0xcf);
            out.writeLong(v);
        }
    }
    
    private void encodeCharSequence(CharSequence cs, ByteBuf out) {
        int len = cs.length();
        if (len < Math.pow(2, 5)) {
            out.writeByte(0xa0 | (len & 0x1F));
            out.writeCharSequence(cs, CharsetUtil.UTF_8);
        } else if (len < Math.pow(2, 8)) {
            out.writeByte(0xd9);
            out.writeByte((int)(len & 0xFF));
            out.writeCharSequence(cs, CharsetUtil.UTF_8);
        } else if (len < Math.pow(2, 16)) {
            out.writeByte(0xda);
            out.writeShort((int)(len & 0xFFFF));
            out.writeCharSequence(cs, CharsetUtil.UTF_8);
        } else if (len < Math.pow(2, 32)) {
            out.writeByte(0xdb);
            out.writeInt((int)(len & 0xFFFFFFFF));
            out.writeCharSequence(cs, CharsetUtil.UTF_8);
        }
    }

    private void encodeList(List<Object> list, ByteBuf out) {
        int len = list.size();
        if (len < Math.pow(2,  4)) {
            out.writeByte(0x90 | (int)(len & 0x0F));
        } else if (len < Math.pow(2, 16)) {
            out.writeByte(0xdc);
            out.writeShort((int)(len & 0xFFFF));
        } else if (len < Math.pow(2, 32)) {
            out.writeByte(0xdd);
            out.writeInt((int)(len & 0xFFFFFFFF));
        }
        Iterator<Object> it = list.iterator();
        while (it.hasNext()) {
            encode(it.next(), out);
        }
    }
    
    private void encodeMap(Map<Object, Object> map, ByteBuf out) {
        int len = map.size();
        if (len < Math.pow(2,  4)) {
            out.writeByte(0x80 | (int)(len & 0x0F));
        } else if (len < Math.pow(2, 16)) {
            out.writeByte(0xde);
            out.writeShort((int)(len & 0xFFFF));
        } else if (len < Math.pow(2, 32)) {
            out.writeByte(0xdf);
            out.writeInt((int)(len & 0xFFFFFFFF));
        }
        Iterator<Entry<Object, Object>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Entry<Object, Object> e = it.next();
            encode(e.getKey(), out);
            encode(e.getValue(), out);
        }
    }
    
    private void encodeBytes(byte[] bytes, ByteBuf out) {
        int len = bytes.length;
        if (len <= 0xFF) {
            out.writeByte(0xc4);
            out.writeByte(len & 0xFF);
        } else if (len <= 0xFFFF) {
            out.writeByte(0xc5);
            out.writeShort(len & 0xFFFF);
        } else if (len <= 0xFFFFFFFF) {
            out.writeByte(0xc6);
            out.writeInt(len);
        }
        out.writeBytes(bytes);
    }
    
    private void encode(Object msg, ByteBuf out) {
        if (msg == null) {
            out.writeByte(0xc0);
        } else if (msg instanceof Byte) {
            encodeByte((Byte) msg, out);
        } else if (msg instanceof Short) {
            encodeShort((Short) msg, out);
        } else if (msg instanceof Integer) {
            encodeInt((Integer) msg, out);
        } else if (msg instanceof Long) {
            encodeLong((Long) msg, out);
        } else if (CharSequence.class.isAssignableFrom(msg.getClass())) {
            encodeCharSequence((CharSequence) msg, out);
        } else if (List.class.isAssignableFrom(msg.getClass())) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) msg;
            encodeList(list, out);
        } else if (Map.class.isAssignableFrom(msg.getClass())) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) msg;
            encodeMap(map, out);
        } else if (msg instanceof byte[]) {
            encodeBytes((byte[]) msg, out);
        } else if (msg instanceof Float) {
            Float f = (Float) msg;
            out.writeByte(0xca);
            out.writeFloat(f);
        } else if (msg instanceof Double) {
            Double d = (Double) msg;
            out.writeByte(0xcb);
            out.writeDouble(d);
        } else throw new UnsupportedOperationException("Encoding of " + msg.getClass() + " objects is not yet implemented");
    }
    
    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        encode(msg, out);
    }
    
}