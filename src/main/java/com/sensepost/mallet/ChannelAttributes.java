package com.sensepost.mallet;

import com.sensepost.mallet.graph.GraphLookup;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

public class ChannelAttributes {

	public static final AttributeKey<Channel> CHANNEL = AttributeKey.valueOf("channel");

	public static final AttributeKey<ConnectRequest> TARGET = AttributeKey.valueOf("target");

	public static final AttributeKey<GraphLookup> GRAPH = AttributeKey.valueOf("graph");

	public static final AttributeKey<Integer> CONNECTION_IDENTIFIER = AttributeKey.valueOf("connection_identifier");
	
}
