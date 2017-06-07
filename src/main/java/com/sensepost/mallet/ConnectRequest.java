package com.sensepost.mallet;

import io.netty.channel.Channel;
import io.netty.util.concurrent.Promise;

public class ConnectRequest {

	private String host;

	private int port;

	private Promise<Channel> connectPromise;

	public ConnectRequest(String host, int port, Promise<Channel> connectPromise) {
		this.host = host;
		this.port = port;
		this.connectPromise = connectPromise;
	}

	public String getHost() {
		return host;
	}
	
	public int getPort() {
		return port;
	}

	public Promise<Channel> getConnectPromise() {
		return connectPromise;
	}

}
