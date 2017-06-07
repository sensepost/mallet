package com.sensepost.mallet.graph;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;

public class Listener {

	private EventLoopGroup boss, workerGroup;
	private Class<? extends ServerChannel> serverClass;
	private String inetHost;
	private int inetPort;
	
	public Listener(EventLoopGroup boss, EventLoopGroup workerGroup, Class <?extends ServerChannel> serverClass, String inetHost, int inetPort) {
		this.boss = boss;
		this.workerGroup = workerGroup;
		this.serverClass = serverClass;
		this.inetHost = inetHost;
		this.inetPort = inetPort;
	}

	public EventLoopGroup getBoss() {
		return boss;
	}

	public EventLoopGroup getWorkerGroup() {
		return workerGroup;
	}

	public Class<? extends ServerChannel> getServerClass() {
		return serverClass;
	}

	public String getInetHost() {
		return inetHost;
	}

	public int getInetPort() {
		return inetPort;
	}

}
