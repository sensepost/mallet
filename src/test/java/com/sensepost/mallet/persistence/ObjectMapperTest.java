package com.sensepost.mallet.persistence;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ObjectMapperTest {

	ObjectMapper mapper;

	@Before
	public void setUp() throws Exception {
		mapper = new ObjectMapper();
	}

	@After
	public void tearDown() throws Exception {
		mapper = null;
	}

	@Test
	public void testNoMapping() {
		try {
			mapper.convertToByte(Long.valueOf(1));
			fail("Exception expected");
		} catch (HandlerNotFoundException e) {
		}
		try {
			mapper.convertToObject(Long.class, new byte[] { 0 });
			fail("Exception expected");
		} catch (HandlerNotFoundException e) {
		}
	}

	@Test
	public void testByteBufMapping() {
		byte[] input = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
		Object o = mapper.convertToObject(ByteBuf.class, input);
		if (o instanceof ByteBuf) {
			ByteBuf b = (ByteBuf) o;
			assertEquals(input.length, b.readableBytes());
			byte[] c = new byte[b.readableBytes()];
			b.readBytes(c);
			assertArrayEquals(input, c);
		} else {
			fail("got a " + o.getClass());
		}
		byte[] output = mapper.convertToByte(Unpooled.wrappedBuffer(input));
		assertArrayEquals(input, output);
	}

	@Test
	public void testByteMapping() {
		byte[] input = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
		Object o = mapper.convertToObject(byte[].class, input);
		if (o instanceof byte[]) {
			byte[] b = (byte[]) o;
			assertArrayEquals(input, b);
		} else {
			fail("got a " + o.getClass());
		}
		byte[] output = mapper.convertToByte(input);
		assertArrayEquals(input, output);
	}

	@Test
	public void testHttpRequestMapping() {
		byte[] input = "GET / HTTP/1.0\r\n\r\n".getBytes();
		Object o = mapper.convertToObject(FullHttpRequest.class, input);
		if (o instanceof FullHttpRequest) {
			FullHttpRequest req = (FullHttpRequest) o;
			req.retain();
			assertEquals("GET", req.method().toString());
			assertEquals("/", req.uri());
			assertEquals("HTTP/1.0", req.protocolVersion().toString());

			byte[] output = mapper.convertToByte(req);
			assertArrayEquals(input, output);
		} else {
			System.out.println(o);
			fail("got a " + o.getClass());
		}
	}
	
	@Test
	public void testHttpResponseMapping() {
		byte[] input = "HTTP/1.0 200 Ok\r\nContent-Length: 10\r\n\r\n1234567890".getBytes();
		Object o = mapper.convertToObject(FullHttpResponse.class, input);
		if (o instanceof FullHttpResponse) {
			FullHttpResponse resp = (FullHttpResponse) o;
			resp.retain();
			assertEquals("HTTP/1.0", resp.protocolVersion().toString());
			assertEquals(200, resp.status().code());
			assertEquals(1, resp.headers().size());
			assertEquals(10, resp.content().readableBytes());
			byte[] content = new byte[resp.content().readableBytes()];
			resp.content().getBytes(resp.content().readerIndex(), content);
			assertArrayEquals("1234567890".getBytes(), content);

			byte[] output = mapper.convertToByte(resp);
			System.out.println(new String(output));
			assertArrayEquals(input, output);
		} else {
			System.out.println(o);
			fail("got a " + o.getClass());
		}
	}

	@Test
	public void testWebsocketUpgradeResponseMapping() {
		byte[] input = ("HTTP/1.1 101 Switching Protocols\r\n" +
				"Upgrade: websocket\r\n" +
				"Connection: Upgrade\r\n" +
				"Sec-WebSocket-Accept: q+piNpTUZqu6nRQNecL5qn1ZvuA=\r\n" +
				"Sec-WebSocket-Version: 13\r\n" +
				"WebSocket-Server: uWebSockets\r\n\r\n").getBytes();
		Object o = mapper.convertToObject(FullHttpResponse.class, input);
		if (o instanceof FullHttpResponse) {
			FullHttpResponse resp = (FullHttpResponse) o;
			resp.retain();
			assertEquals("HTTP/1.1", resp.protocolVersion().toString());
			assertEquals(101, resp.status().code());
			assertEquals(6, resp.headers().size());
			assertEquals(0, resp.content().readableBytes());

			byte[] output = mapper.convertToByte(resp);
			System.out.println(new String(output));
			assertArrayEquals(input, output);
		} else {
			System.out.println(o);
			fail("got a " + o.getClass());
		}
		// now do it again
		o = mapper.convertToObject(FullHttpResponse.class, input);
		if (o instanceof FullHttpResponse) {
			FullHttpResponse resp = (FullHttpResponse) o;
			resp.retain();
			assertEquals("HTTP/1.1", resp.protocolVersion().toString());
			assertEquals(101, resp.status().code());
			assertEquals(6, resp.headers().size());
			assertEquals(0, resp.content().readableBytes());

			byte[] output = mapper.convertToByte(resp);
			System.out.println(new String(output));
			assertArrayEquals(input, output);
		} else {
			System.out.println(o);
			fail("got a " + o.getClass());
		}
		

	}
	
	
}
