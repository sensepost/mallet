package com.sensepost.mallet;

import java.net.SocketAddress;

import io.netty.channel.Channel;
import io.netty.util.concurrent.Promise;

public class ConnectRequest {

	private SocketAddress target;
	
	private Promise<Channel> connectPromise;

	public ConnectRequest(SocketAddress target, Promise<Channel> connectPromise) {
		this.target = target;
		this.connectPromise = connectPromise;
	}

	public SocketAddress getTarget() {
		return target;
	}
	
	public Promise<Channel> getConnectPromise() {
		return connectPromise;
	}

	@Override
	public String toString() {
		return "Connect " + target;
	}
}
