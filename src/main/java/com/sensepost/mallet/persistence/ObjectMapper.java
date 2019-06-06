package com.sensepost.mallet.persistence;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

import com.sensepost.mallet.ognl.OgnlExpression;
import com.sensepost.mallet.ognl.OgnlExpressions;
import com.sensepost.mallet.ognl.OgnlExpressions.OgnlFunction;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ObjectUtil;
import ognl.OgnlException;

public class ObjectMapper {

    public ObjectMapper() {
    }

    private ChannelHandler[] getHandlers(OgnlExpression expression) throws OgnlException {
        Object result = expression.getValue(null);
        ObjectUtil.checkNotNull(result, "result");
        if (result instanceof ChannelHandler) {
            return new ChannelHandler[] { (ChannelHandler) result };
        } else if (result.getClass().isArray()) {
            Object[] results = (Object[]) result;
            ChannelHandler[] handlers = new ChannelHandler[results.length];
            for (int i = 0; i < results.length; i++) {
                if (results[i] instanceof ChannelHandler) {
                    handlers[i] = (ChannelHandler) results[i];
                } else {
                    throw new ClassCastException(
                            "Error casting a " + results[i].getClass().getName() + " to ChannelHandler");
                }
            }
            return handlers;
        } else if (List.class.isAssignableFrom(result.getClass())) {
            List<?> results = (List<?>) result;
            ChannelHandler[] handlers = new ChannelHandler[results.size()];
            for (int i = 0; i < results.size(); i++) {
                if (results.get(i) instanceof ChannelHandler) {
                    handlers[i] = (ChannelHandler) results.get(i);
                } else {
                    throw new ClassCastException(
                            "Error casting a " + results.get(i).getClass().getName() + " to ChannelHandler");
                }
            }
            return handlers;
        }
        throw new ClassCastException("Error casting a " + result.getClass().getName() + " to ChannelHandler");
    }

    private ChannelHandler[] getHandlersForClass(Class<?> c, OgnlFunction function) {
        OgnlExpression expression = OgnlExpressions.INSTANCE.getOgnlExpression(c, function);
        if (expression == null) {
            return null;
        }
        try {
            return getHandlers(expression);
        } catch (OgnlException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String toString(Object obj) {
        if (obj == null) {
            return String.valueOf(obj);
        }
        OgnlExpression expression = OgnlExpressions.INSTANCE.getOgnlExpression(obj.getClass(), OgnlFunction.TOSTRING);
        if (expression == null) {
            return String.valueOf(obj);
        }
        try {
            return String.valueOf(expression.getValue(obj));
        } catch (OgnlException e) {
            System.err.println("Exception evaluating toString() " + expression);
            return String.valueOf(obj);
        }
    }

    private EmbeddedChannel createEmbeddedChannel(ChannelHandler[] handlers) {
        EmbeddedChannel ec = new EmbeddedChannel();
        ec.pipeline().addLast(handlers);
        return ec;
    }

    public byte[] convertToByte(Object object) throws HandlerNotFoundException {
        if (object == null)
            throw new NullPointerException("object");

        Class<?> objectClass = object.getClass();

        if (objectClass.equals(byte[].class)) {
            return (byte[]) object;
        } else if (object instanceof ByteBuf) {
            // if it is a ByteBuf, extract the bytes directly
            ByteBuf b = (ByteBuf) object;
            byte[] bytes = new byte[b.readableBytes()];
            // don't update the indices
            b.getBytes(b.readerIndex(), bytes);
            return bytes;
        } else if (object instanceof ByteBufHolder) {
            // make a deep copy of the object and its ByteBuf
            object = ((ByteBufHolder) object).copy();
        } else {
            // otherwise, retain the object to prevent it being reclaimed
            // after we write it to the EmbeddedChannel
            ReferenceCountUtil.retain(object);
        }

        ChannelHandler[] handlers = getHandlersForClass(objectClass, OgnlFunction.ENCODE);
        if (handlers == null)
            throw new HandlerNotFoundException("Could not find handlers for " + objectClass);

        EmbeddedChannel ec = createEmbeddedChannel(handlers);

        Object o;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ec.writeOutbound(object);
        ec.flushOutbound();
        do {
            o = ec.readOutbound();
            if (o == null)
                throw new NullPointerException("Read null from mapping");
            else if (o instanceof ByteBuf) {
                ByteBuf b = (ByteBuf) o;
                byte[] bytes = new byte[b.readableBytes()];
                b.readBytes(bytes);
                b.release();
                try {
                    baos.write(bytes);
                    baos.flush();
                } catch (Exception e) {
                    // this should never happen
                    throw new RuntimeException(e);
                }
            } else
                throw new RuntimeException("Object returned from mapping was not a ByteBuf, but a " + o.getClass());
        } while (!ec.outboundMessages().isEmpty());
        ec.close();
        if (baos.size() == 0)
            throw new RuntimeException("Mapping from " + object + " to bytes resulted in 0 bytes written!");
        return baos.toByteArray();
    }

    public Object convertToObject(Class<?> objectClass, byte[] bytes) throws HandlerNotFoundException {
        if (objectClass == byte[].class)
            return bytes;
        if (ByteBuf.class.isAssignableFrom(objectClass))
            return Unpooled.wrappedBuffer(bytes);
        Object o;

        ChannelHandler[] handlers = getHandlersForClass(objectClass, OgnlFunction.DECODE);
        if (handlers == null)
            throw new HandlerNotFoundException("Handlers not found for " + objectClass);

        EmbeddedChannel ec = createEmbeddedChannel(handlers);

        if (!ec.inboundMessages().isEmpty())
            throw new IllegalStateException(
                    "Stray objects left in the codec channel before writing: " + ec.inboundMessages());
        ec.writeInbound(Unpooled.wrappedBuffer(bytes));
        ec.flushInbound();
        o = ec.readInbound();
        if (!ec.inboundMessages().isEmpty())
            throw new IllegalStateException(
                    "Stray objects left in the codec channel after reading: " + ec.inboundMessages());

        ec.close();
        if (o == null)
            throw new NullPointerException("No object read decoding " + Arrays.toString(bytes));
//        if (!mapClass.isAssignableFrom(o.getClass()))
//            throw new ClassCastException("Expected " + mapClass + ", but got incompatible " + o.getClass());
        return o;
    }

}
