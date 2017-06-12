package com.sensepost.mallet.graph;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.WeakHashMap;

import com.sensepost.mallet.ConnectTargetHandler;
import com.sensepost.mallet.InterceptController;
import com.sensepost.mallet.InterceptHandler;
import com.sensepost.mallet.RelayHandler;
import com.sensepost.mallet.ScriptHandler;
import com.sensepost.mallet.SocksServerConnectHandler;
import com.sensepost.mallet.SocksServerHandler;

import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.util.Mapping;

public class Graph implements GraphLookup {

	private InterceptController ic;
	private Mapping<? super String, ? extends SslContext> serverCertMapping;
	private SslContext clientContext;

	private WeakHashMap<ChannelHandler, ChannelHandler[]> lookup = new WeakHashMap<>();
	private boolean direct = true, socks = false;

	public Graph(InterceptController ic, Mapping<? super String, ? extends SslContext> serverCertMapping,
			SslContext clientContext) {
		this.ic = ic;
		this.serverCertMapping = serverCertMapping;
		this.clientContext = clientContext;
	}

	@Override
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
}
