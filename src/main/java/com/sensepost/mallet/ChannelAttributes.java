package com.sensepost.mallet;

import java.net.InetSocketAddress;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

public class ChannelAttributes {

	public static final AttributeKey<Channel> CHANNEL = AttributeKey.valueOf("channel");

	public static final AttributeKey<InetSocketAddress> TARGET = AttributeKey.valueOf("target");

}
