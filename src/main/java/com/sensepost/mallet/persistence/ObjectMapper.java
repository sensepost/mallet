package com.sensepost.mallet.persistence;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.util.ReferenceCountUtil;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ObjectMapper {

	private Map<Class<?>, EmbeddedChannel> objectToByteMap = new HashMap<>(),
			byteToObjectMap = new HashMap<>();

	public ObjectMapper() {
		registerMapping(FullHttpRequest.class,
				new ChannelHandler[] { new HttpRequestEncoder() },
				new ChannelHandler[] { new HttpRequestDecoder(),
						new HttpObjectAggregator(Integer.MAX_VALUE) {
							/*
							 * Special implementation to prevent spurious
							 * addition of Content-Length header to requests
							 */
							@Override
							protected void finishAggregation(
									FullHttpMessage aggregated)
									throws Exception {
								if (aggregated instanceof FullHttpResponse)
									super.finishAggregation(aggregated);
							}
						} });
		registerMapping(FullHttpResponse.class,
				new ChannelHandler[] { new HttpResponseEncoder() },
				new ChannelHandler[] { new HttpResponseDecoder(),
						new HttpObjectAggregator(Integer.MAX_VALUE) });
	}

	public void registerMapping(Class<?> objectClass,
			ChannelHandler objectToByteHandler,
			ChannelHandler byteToObjectHandler) {
		registerMapping(objectClass,
				new ChannelHandler[] { objectToByteHandler },
				new ChannelHandler[] { byteToObjectHandler });
	}

	public void registerMapping(Class<?> objectClass,
			ChannelHandler[] objectToByteHandler,
			ChannelHandler[] byteToObjectHandler) {
		objectToByteMap.put(objectClass, new EmbeddedChannel(
				objectToByteHandler));
		byteToObjectMap.put(objectClass, new EmbeddedChannel(
				byteToObjectHandler));
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

		EmbeddedChannel ec = getMappingForClass(objectClass, objectToByteMap);
		Object o;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		synchronized (ec) {
			if (!ec.outboundMessages().isEmpty())
				throw new IllegalStateException(
						"Stray objects left in the codec channel before writing: "
								+ ec.outboundMessages());
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
					throw new RuntimeException(
						"Object returned from mapping was not a ByteBuf, but a "
								+ o.getClass());
			} while (!ec.outboundMessages().isEmpty());
		}
		if (baos.size() == 0)
			throw new RuntimeException("Mapping from " + object + " to bytes resulted in 0 bytes written!");
		return baos.toByteArray();
	}

	public Object convertToObject(Class<?> objectClass, byte[] bytes)
			throws HandlerNotFoundException {
		if (objectClass == byte[].class)
			return bytes;
		if (ByteBuf.class.isAssignableFrom(objectClass))
			return Unpooled.wrappedBuffer(bytes);
		Object o;
		EmbeddedChannel ec = getMappingForClass(objectClass, byteToObjectMap);
		synchronized (ec) {
			if (!ec.inboundMessages().isEmpty())
				throw new IllegalStateException(
						"Stray objects left in the codec channel before writing: "
								+ ec.inboundMessages());
			ec.writeInbound(Unpooled.wrappedBuffer(bytes));
			ec.flushInbound();
			o = ec.readInbound();
			if (!ec.inboundMessages().isEmpty())
				throw new IllegalStateException(
						"Stray objects left in the codec channel after reading: "
								+ ec.inboundMessages());
		}
		if (o == null)
			throw new NullPointerException("No object read decoding "
					+ Arrays.toString(bytes));
		if (!objectClass.isAssignableFrom(objectClass))
			throw new ClassCastException("Expected " + objectClass
					+ ", but got incompatible " + o.getClass());
		return o;
	}

	private EmbeddedChannel getMappingForClass(Class<?> objectClass,
			Map<Class<?>, EmbeddedChannel> map) throws HandlerNotFoundException {
		EmbeddedChannel ec = null;
		Class<?> c = objectClass;
		// Find by class hierarchy
		do {
			ec = map.get(c);
			c = c.getSuperclass();
		} while (ec == null && c != Object.class);
		if (ec != null)
			return ec;

		// Find by interfaces
		Class<?>[] interfaces = objectClass.getInterfaces();
		for (int i = 0; i < interfaces.length; i++) {
			ec = map.get(interfaces[i]);
			if (ec != null)
				return ec;
		}
		throw new HandlerNotFoundException("Handler for object class "
				+ objectClass + " not found");
	}
}
