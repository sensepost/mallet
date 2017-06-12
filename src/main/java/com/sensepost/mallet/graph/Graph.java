package com.sensepost.mallet.graph;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.WeakHashMap;

import com.sensepost.mallet.ChannelAttributes;
import com.sensepost.mallet.ConnectTargetHandler;
import com.sensepost.mallet.InterceptController;
import com.sensepost.mallet.InterceptHandler;
import com.sensepost.mallet.ScriptHandler;
import com.sensepost.mallet.SocksServerConnectHandler;
import com.sensepost.mallet.SocksServerHandler;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.util.Mapping;

public class Graph implements GraphLookup {

	private static final int PORT = Integer.parseInt(System.getProperty("port", "1089"));
	private static final String INTERFACE = System.getProperty("interface", "0.0.0.0");

	private InterceptController ic;
	private Mapping<? super String, ? extends SslContext> serverCertMapping;
	private SslContext clientContext;

	private WeakHashMap<ChannelHandler, ChannelHandler[]> lookup = new WeakHashMap<>();
	private boolean direct = true, socks = false;

	private InetSocketAddress listenAddr = new InetSocketAddress(INTERFACE, PORT);
	private EventLoopGroup bossGroup = new NioEventLoopGroup(1);
	private EventLoopGroup workerGroup = new NioEventLoopGroup();
	private Channel serverChannel = null;

	public Graph(Mapping<? super String, ? extends SslContext> serverCertMapping, SslContext clientContext) {
		this.serverCertMapping = serverCertMapping;
		this.clientContext = clientContext;
	}

	public void setInterceptController(InterceptController ic) {
		this.ic = ic;
	}

	@Override
	public void startServers() throws Exception {
		serverChannel = new ServerBootstrap().group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
				.handler(new LoggingHandler(LogLevel.DEBUG)).attr(ChannelAttributes.GRAPH, this)
				.childHandler(new GraphChannelInitializer()).childOption(ChannelOption.AUTO_READ, true)
				.childOption(ChannelOption.ALLOW_HALF_CLOSURE, true).bind(listenAddr).sync().channel();
	}

	synchronized public ChannelHandler[] getServerChannelInitializer(InetSocketAddress server) {
		return new ChannelHandler[] { new SocksPortUnificationServerHandler(), new SocksServerHandler(),
				new SocksServerConnectHandler(), new ConnectTargetHandler(), new TargetSpecificChannelHandler() };
	}

	@Override
	synchronized public ChannelHandler[] getNextHandlers(ChannelHandler handler, String option) {
		if (handler instanceof TargetSpecificChannelHandler) {
			if (option.endsWith(":80") || option.endsWith(":8000")) {
				InterceptHandler ih = new InterceptHandler(ic);
				lookup.put(ih,
						new ChannelHandler[] { new HttpClientCodec(), new HttpObjectAggregator(10 * 1024 * 1024), ih });
				return new ChannelHandler[] { new HttpServerCodec(), new HttpObjectAggregator(10 * 1024 * 1024),
						new ScriptHandler(), ih };
			} else if (option.endsWith(":443")) {
				InterceptHandler ih = new InterceptHandler(ic);
				lookup.put(ih, new ChannelHandler[] { clientContext.newHandler(PooledByteBufAllocator.DEFAULT),
						new HttpClientCodec(), new HttpObjectAggregator(10 * 1024 * 1024), ih });
				return new ChannelHandler[] { new SniHandler(serverCertMapping), new HttpServerCodec(),
						new HttpObjectAggregator(10 * 1024 * 1024), ih };
			} else {
				InterceptHandler ih = new InterceptHandler(ic);
				lookup.put(ih, new ChannelHandler[] { ih });
				return new ChannelHandler[] { ih };
			}
		} else {
			return lookup.remove(handler);
		}
	}

	@Override
	synchronized public ChannelHandler[] getClientChannelInitializer(ChannelHandler handler) {
		ChannelHandler[] handlers = lookup.remove(handler);
		if (handlers == null) {
			System.exit(1);
			throw new NullPointerException("Couldn't find handlers for " + handler);
		}
		return handlers;
	}

	@Override
	synchronized public ChannelHandler[] getProxyInitializer(ChannelHandler handler, SocketAddress target) {
		if (direct)
			return new ChannelHandler[] { handler };
		else if (socks)
			return new ChannelHandler[] { new Socks5ProxyHandler(new InetSocketAddress("127.0.0.1", 1081)), handler };
		else
			return new ChannelHandler[] { new HttpProxyHandler(new InetSocketAddress("127.0.0.1", 8080)), handler };
	}

	@Override
	public void shutdownServers() throws Exception {
		if (serverChannel != null)
			serverChannel.close().sync();
		bossGroup.shutdownGracefully();
		workerGroup.shutdownGracefully();
	}

	private class GraphChannelInitializer extends ChannelInitializer<SocketChannel> {

		@Override
		protected void initChannel(SocketChannel ch) throws Exception {
			GraphLookup gl = ch.parent().attr(ChannelAttributes.GRAPH).get();
			ch.attr(ChannelAttributes.GRAPH).set(gl);
			ChannelHandler[] handlers = getServerChannelInitializer(ch.parent().localAddress());
			ch.pipeline().addFirst(new ConnectionNumberChannelHandler());
			ch.pipeline().addLast(handlers);
		}

	}
}
