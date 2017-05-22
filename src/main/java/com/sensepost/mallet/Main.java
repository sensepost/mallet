package com.sensepost.mallet;

import java.net.InetSocketAddress;

import com.sensepost.mallet.swing.InterceptFrame;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class Main {

	private static final int PORT = Integer.parseInt(System.getProperty("port", "8887"));
	private static final String INTERFACE = System.getProperty("interface", "0.0.0.0");
	private static final String dst = System.getProperty("target", "localhost:8888");

	private static InetSocketAddress parseAddress(String address) {
		int c = address.indexOf(':');
		if (c < 1)
			throw new RuntimeException("Can't parse " + address);
		String hostname = address.substring(0, c);
		int port = Integer.parseInt(address.substring(c + 1));
		System.out.println("Parsed '" + address + "' as '" + hostname + "' and " + port);
		return new InetSocketAddress(hostname, port);
	}

	public static void main(String[] args) throws Exception {
		InetSocketAddress target = parseAddress(dst);

		InterceptFrame ui = new InterceptFrame();
		ui.setSize(800, 600);
		// InterceptFrame ui = null;
		InetSocketAddress listenAddr = new InetSocketAddress(INTERFACE, PORT);
		EventLoopGroup bossGroup = new NioEventLoopGroup(1);
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		InterceptHandler interceptHandler = new InterceptHandler(ui);
		Connector conn = new Connector(interceptHandler);
		try {
			ServerBootstrap b = new ServerBootstrap();
			Channel c = b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).childHandler(conn)
					.childOption(ChannelOption.AUTO_READ, false).childOption(ChannelOption.ALLOW_HALF_CLOSURE, true)
					.bind(listenAddr).sync().channel();
			c.attr(ChannelAttributes.TARGET).set(target);
			System.out.println("Listening on " + listenAddr + "\nPress Enter to shutdown");
			if (ui != null)
				ui.setVisible(true);
			System.in.read();
			System.out.print("Exiting...");
			ChannelFuture f = c.closeFuture();
			c.close();
			f.sync();
			System.out.println("Done");
			System.exit(0);
		} finally {
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
		}

	}
}
