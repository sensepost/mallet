package com.sensepost.mallet.handlers;

import static org.junit.Assert.assertArrayEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

public class SimpleBinaryModificationHandlerTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test() {
		byte[] bytes = "abcdefg".getBytes();
		ByteBuf bb = Unpooled.wrappedBuffer(bytes);
		EmbeddedChannel ec = new EmbeddedChannel(new SimpleBinaryModificationHandler("cde", "CDE"));
		ec.writeInbound(bb);
		ByteBuf mod = ec.readInbound();
		bytes = mod.array();
		assertArrayEquals("abCDEfg".getBytes(), bytes);
	}

	@Test
	public void testStart() {
		byte[] bytes = "abcdefg".getBytes();
		ByteBuf bb = Unpooled.wrappedBuffer(bytes);
		EmbeddedChannel ec = new EmbeddedChannel(new SimpleBinaryModificationHandler("a", "A"));
		ec.writeInbound(bb);
		ByteBuf mod = ec.readInbound();
		bytes = mod.array();
		assertArrayEquals("Abcdefg".getBytes(), bytes);
	}

	@Test
	public void testEnd() {
		byte[] bytes = "abcdefg".getBytes();
		ByteBuf bb = Unpooled.wrappedBuffer(bytes);
		EmbeddedChannel ec = new EmbeddedChannel(new SimpleBinaryModificationHandler("g", "G"));
		ec.writeInbound(bb);
		ByteBuf mod = ec.readInbound();
		bytes = mod.array();
		assertArrayEquals("abcdefG".getBytes(), bytes);
	}

	@Test
	public void testMultiple() {
		byte[] bytes = "abababa".getBytes();
		ByteBuf bb = Unpooled.wrappedBuffer(bytes);
		EmbeddedChannel ec = new EmbeddedChannel(new SimpleBinaryModificationHandler("a", "A"));
		ec.writeInbound(bb);
		ByteBuf mod = ec.readInbound();
		bytes = mod.array();
		assertArrayEquals("AbAbAbA".getBytes(), bytes);
	}

	@Test
	public void testMultiple2() {
		byte[] bytes = "aaabaaabaaabaaa".getBytes();
		ByteBuf bb = Unpooled.wrappedBuffer(bytes);
		EmbeddedChannel ec = new EmbeddedChannel(new SimpleBinaryModificationHandler("aa", "AA"));
		ec.writeInbound(bb);
		ByteBuf mod = ec.readInbound();
		bytes = mod.array();
		assertArrayEquals("AAabAAabAAabAAa".getBytes(), bytes);
	}


}
