package com.sensepost.mallet.persistence;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.ReferenceCountUtil;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sensepost.mallet.handlers.http.MaxHttpObjectAggregator;
import com.sensepost.mallet.handlers.http.WebsocketDecoderInitializer;
import com.sensepost.mallet.handlers.http.WebsocketEncoderInitializer;

public class ObjectMapper {

	private Map<Class<?>, List<Class<? extends ChannelHandler>>> objectToByteMap = new HashMap<>(),
			byteToObjectMap = new HashMap<>();

	public ObjectMapper() {
		registerDefaultMappings();
	}

	protected void registerDefaultMappings() {
		registerMapping(
				FullHttpRequest.class,
				makeList(HttpRequestEncoder.class),
				makeList(HttpRequestDecoder.class,
						MaxHttpObjectAggregator.class));
		registerMapping(
				FullHttpResponse.class,
				makeList(HttpResponseEncoder.class),
				makeList(HttpResponseDecoder.class,
						MaxHttpObjectAggregator.class));
		registerMapping(WebSocketFrame.class,
				makeList(WebsocketEncoderInitializer.class),
				makeList(WebsocketDecoderInitializer.class));
	}

	private List<Class<? extends ChannelHandler>> makeList(
			Class<? extends ChannelHandler> handler) {
		List<Class<? extends ChannelHandler>> list = new ArrayList<>();
		list.add(handler);
		return list;
	}

	@SafeVarargs
	final private List<Class<? extends ChannelHandler>> makeList(
			Class<? extends ChannelHandler>... handlers) {
		List<Class<? extends ChannelHandler>> list = new ArrayList<>();
		for (Class<? extends ChannelHandler> c : handlers)
			list.add(c);
		return list;
	}

	public void registerMapping(Class<?> objectClass,
			Class<? extends ChannelHandler> objectToByteHandler,
			Class<? extends ChannelHandler> byteToObjectHandler) {

		registerMapping(objectClass, makeList(objectToByteHandler),
				makeList(byteToObjectHandler));
	}

	public void registerMapping(Class<?> objectClass,
			List<Class<? extends ChannelHandler>> objectToByteHandler,
			List<Class<? extends ChannelHandler>> byteToObjectHandler) {
		for (Class<?> clz : objectToByteHandler) {
			try {
				clz.getConstructor(new Class<?>[0]);
			} catch (NoSuchMethodException | SecurityException e) {
				throw new RuntimeException("No default constructor found for class " + clz, e);
			}
		}
		for (Class<?> clz : byteToObjectHandler) {
			try {
				clz.getConstructor(new Class<?>[0]);
			} catch (NoSuchMethodException | SecurityException e) {
				throw new RuntimeException("No default constructor found for class " + clz, e);
			}
		}
		objectToByteMap.put(objectClass, objectToByteHandler);
		byteToObjectMap.put(objectClass, byteToObjectHandler);
	}

	private EmbeddedChannel createEmbeddedChannel(
			List<Class<? extends ChannelHandler>> handlers) {
		EmbeddedChannel ec = new EmbeddedChannel();
		for (Class<? extends ChannelHandler> c : handlers) {
			try {
				ChannelHandler h = c.newInstance();
				ec.pipeline().addLast(h);
			} catch (InstantiationException | IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
		}
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

		Class<?> mapClass = getMappingClassForClass(objectClass,
				objectToByteMap.keySet());
		if (mapClass == null)
			throw new HandlerNotFoundException("Handler not found for "
					+ objectClass);

		EmbeddedChannel ec = createEmbeddedChannel(objectToByteMap
				.get(mapClass));

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
		ec.close();
		if (baos.size() == 0)
			throw new RuntimeException("Mapping from " + object
					+ " to bytes resulted in 0 bytes written!");
		return baos.toByteArray();
	}

	public Object convertToObject(Class<?> objectClass, byte[] bytes)
			throws HandlerNotFoundException {
		if (objectClass == byte[].class)
			return bytes;
		if (ByteBuf.class.isAssignableFrom(objectClass))
			return Unpooled.wrappedBuffer(bytes);
		Object o;

		Class<?> mapClass = getMappingClassForClass(objectClass,
				byteToObjectMap.keySet());
		if (mapClass == null)
			throw new HandlerNotFoundException("Handler not found for "
					+ objectClass);

		EmbeddedChannel ec = createEmbeddedChannel(byteToObjectMap
				.get(mapClass));

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

		ec.close();
		if (o == null)
			throw new NullPointerException("No object read decoding "
					+ Arrays.toString(bytes));
		if (!mapClass.isAssignableFrom(o.getClass()))
			throw new ClassCastException("Expected " + mapClass
					+ ", but got incompatible " + o.getClass());
		return o;
	}

	private Class<?> getMappingClassForClass(Class<?> objectClass,
			Set<Class<?>> classes) {
		Class<?> c = objectClass;
		// Find by class hierarchy
		while (c != Object.class) {
			if (classes.contains(c))
				return c;
			// or by interfaces
			for (Class<?> i : c.getInterfaces())
				if (classes.contains(i))
					return i;
			c = c.getSuperclass();
		}
		return null;
	}

}
