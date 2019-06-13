package com.sensepost.mallet;

import java.net.SocketAddress;

import com.sensepost.mallet.model.ChannelEvent;
import com.sensepost.mallet.model.DataModel;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

public interface InterceptController {

    static final InternalLogger logger = InternalLoggerFactory.getInstance(InterceptController.class);

	void setDataModel(DataModel dm);

	void addChannel(String id, SocketAddress local, SocketAddress remote);

	void processChannelEvent(ChannelEvent evt);

	void linkChannels(String channel1, String channel2, SocketAddress localAddress2, SocketAddress remoteAddress2);

}
