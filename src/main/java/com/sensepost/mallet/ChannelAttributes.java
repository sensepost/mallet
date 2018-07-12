package com.sensepost.mallet;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.net.SocketAddress;

import javax.script.Bindings;

import com.sensepost.mallet.graph.GraphLookup;

public class ChannelAttributes {

	public static final AttributeKey<Channel> CHANNEL = AttributeKey.valueOf("channel");

	public static final AttributeKey<Throwable> CAUSE = AttributeKey.valueOf("cause");

	public static final AttributeKey<Object> CAUSE_EVENT = AttributeKey.valueOf("cause_EVENT");

	public static final AttributeKey<ConnectRequest> TARGET = AttributeKey.valueOf("target");

	public static final AttributeKey<GraphLookup> GRAPH = AttributeKey.valueOf("graph");

	public static final AttributeKey<String> CONNECTION_IDENTIFIER = AttributeKey.valueOf("connection_identifier");
	
	public static final AttributeKey<SocketAddress> REMOTE_ADDRESS = AttributeKey.valueOf("remote_address");
	
	public static final AttributeKey<Bindings> SCRIPT_CONTEXT = AttributeKey.valueOf("script_context");
	
	
}
