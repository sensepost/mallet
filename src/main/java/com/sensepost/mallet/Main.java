package com.sensepost.mallet;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.Certificate;

import javax.security.auth.x500.X500Principal;

import com.sensepost.mallet.graph.Graph;
import com.sensepost.mallet.graph.GraphChannelInitializer;
import com.sensepost.mallet.ssl.AutoGeneratingContextSelector;
import com.sensepost.mallet.swing.InterceptFrame;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

public class Main {

	private static final int PORT = Integer.parseInt(System.getProperty("port", "1089"));
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

	private static boolean initFromP12(KeyStore keyStore, File ca, char[] password, char[] keyPassword) {
		if (!ca.exists())
			return false;
		try {
			KeyStore p12 = KeyStore.getInstance("PKCS12");
			InputStream in = new FileInputStream(ca);
			try {
				p12.load(in, password);
			} finally {
				try {
					in.close();
				} catch (IOException ioe) {
				}
			}
			Key caKey = p12.getKey("cacert", password);
			Certificate[] caCerts = p12.getCertificateChain("cacert");
			if (caKey == null || caCerts == null)
				return false;
			keyStore.load(null, password);
			keyStore.setKeyEntry(AutoGeneratingContextSelector.CA_ALIAS, caKey, password, caCerts);
			return true;
		} catch (IOException ioe) {
			return false;
		} catch (GeneralSecurityException gse) {
			return false;
		}
	}

	private static AutoGeneratingContextSelector getServerSslContextSelector()
			throws GeneralSecurityException, IOException {
		File ks = new File("keystore.jks");
		char[] password = "password".toCharArray();
		KeyStore keyStore = KeyStore.getInstance("JKS");
		if (!ks.exists()) {
			AutoGeneratingContextSelector selector = null;
			if (!initFromP12(keyStore, new File("ca.p12"), password, password)) {
				keyStore.load(null, password);
				System.err.println("Generating a new CA");
				X500Principal ca = new X500Principal(
						"cn=OWASP Custom CA for " + java.net.InetAddress.getLocalHost().getHostName()
								+ ",ou=OWASP Custom CA,o=OWASP,l=OWASP,st=OWASP,c=OWASP");
				selector = new AutoGeneratingContextSelector(ca, keyStore, password);
			} else {
				selector = new AutoGeneratingContextSelector(keyStore, password);
			}
			OutputStream out = new FileOutputStream(ks);
			try {
				keyStore.store(out, password);
			} finally {
				out.close();
			}
			File pem = new File("ca.pem");
			if (!pem.exists()) {
				FileWriter w = null;
				try {
					w = new FileWriter(pem);
					w.write(selector.getCACert());
				} catch (IOException e) {
					System.err.println("Error exporting CA cert : " + e.getLocalizedMessage());
				} finally {
					if (w != null)
						w.close();
				}
			}
			return selector;
		}
		if (ks.exists()) {
			try {
				InputStream in = new FileInputStream(ks);
				keyStore.load(in, password);
				return new AutoGeneratingContextSelector(keyStore, password);
			} catch (GeneralSecurityException e) {
				System.err.println("Error loading keystore: " + e.getLocalizedMessage());
			} catch (IOException e) {
				System.err.println("Error loading keystore: " + e.getLocalizedMessage());
			}
		}
		throw new GeneralSecurityException("Could not initialise Server SSL ContextSelector");
	}

	public static void main(String[] args) throws Exception {
		InetSocketAddress target = parseAddress(dst);

		InterceptFrame ui = new InterceptFrame();
		ui.setSize(800, 600);
		// InterceptFrame ui = null;
		InetSocketAddress listenAddr = new InetSocketAddress(INTERFACE, PORT);
		EventLoopGroup bossGroup = new NioEventLoopGroup(1);
		EventLoopGroup workerGroup = new NioEventLoopGroup();

		final SslContext clientContext = SslContextBuilder.forClient()
				.trustManager(InsecureTrustManagerFactory.INSTANCE).build();

		AutoGeneratingContextSelector serverCertMapping = getServerSslContextSelector();

		Graph graph = new Graph(ui, serverCertMapping, clientContext);
		try {
			ServerBootstrap b = new ServerBootstrap();
			Channel c = b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
					.handler(new LoggingHandler(LogLevel.DEBUG)).attr(ChannelAttributes.GRAPH, graph)
					.childHandler(new GraphChannelInitializer()).childOption(ChannelOption.AUTO_READ, true)
					.childOption(ChannelOption.ALLOW_HALF_CLOSURE, true).bind(listenAddr).sync().channel();
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
