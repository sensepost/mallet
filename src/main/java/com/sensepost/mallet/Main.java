package com.sensepost.mallet;

import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.X509KeyManager;
import javax.script.Bindings;
import javax.script.SimpleBindings;
import javax.security.auth.x500.X500Principal;

import com.mxgraph.swing.mxGraphComponent;
import com.sensepost.mallet.graph.Graph;
import com.sensepost.mallet.ssl.AutoGeneratingContextSelector;
import com.sensepost.mallet.ssl.KeyStoreX509KeyManager;
import com.sensepost.mallet.swing.GraphEditor.CustomGraph;
import com.sensepost.mallet.swing.GraphEditor.CustomGraphComponent;
import com.sensepost.mallet.swing.InterceptFrame;

public class Main {

	private final static char[] PASSWORD = "password".toCharArray();

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

	private static AutoGeneratingContextSelector getServerSslContextSelector(KeyStore keystore)
			throws GeneralSecurityException, IOException {
		return new AutoGeneratingContextSelector(keystore, PASSWORD);
	}

	private static KeyStore loadOrInitKeyStore(String location) throws GeneralSecurityException {
		try {
			File ks = new File("keystore.jks");
			KeyStore keyStore = KeyStore.getInstance("JKS");
			if (!ks.exists()) {
				AutoGeneratingContextSelector selector = null;
				if (!initFromP12(keyStore, new File("ca.p12"), PASSWORD, PASSWORD)) {
					keyStore.load(null, PASSWORD);
					System.err.println("Generating a new CA");
					X500Principal ca = new X500Principal(
							"cn=Mallet Custom CA for " + java.net.InetAddress.getLocalHost().getHostName()
									+ ",ou=Mallet Custom CA,o=Mallet,l=Mallet,st=Mallet,c=Mallet");
					selector = new AutoGeneratingContextSelector(ca, keyStore, PASSWORD);
				}
				OutputStream out = new FileOutputStream(ks);
				try {
					keyStore.store(out, PASSWORD);
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
				return keyStore;
			} else {
				InputStream in = new FileInputStream(ks);
				keyStore.load(in, PASSWORD);
				return keyStore;
			}
		} catch (KeyStoreException | IOException e) {
			throw new GeneralSecurityException("Could not initialise Server SSL ContextSelector", e);
		}
	}

	public static void main(String[] args) throws Exception {
		final KeyStore ks = loadOrInitKeyStore("keystore.jks");

		final AutoGeneratingContextSelector serverCertMapping = getServerSslContextSelector(ks);

		final SslContext clientContext = SslContextBuilder.forClient()
				.trustManager(InsecureTrustManagerFactory.INSTANCE).build();

		mxGraphComponent graphComponent = new CustomGraphComponent(new CustomGraph());
		Bindings scriptContext = new SimpleBindings();

		scriptContext.put("SSLServerCertificateMap", serverCertMapping);
		scriptContext.put("SSLClientContext", clientContext);

		X509KeyManager clientKeyManager = new KeyStoreX509KeyManager(ks, PASSWORD);
		scriptContext.put("SSLClientKeyManager", clientKeyManager);

		InterceptFrame ui = new InterceptFrame(graphComponent);
		InterceptController ic = ui.getInterceptController();
		ui.setServerKeyStore(ks);
		Graph graph = new Graph(graphComponent, ic, scriptContext);

		// set up LoggingHandler logging
		Handler handler = ui.getLogHandler();
		Logger logger = Logger.getLogger(LoggingHandler.class.getCanonicalName());
		logger.setLevel(Level.FINEST);
		logger.addHandler(handler);

		scriptContext.put("InterceptController", ic);
//		ObjectMapper om = new ObjectMapper();
//		MessageDAO dao = new MessageDAO(null, om);
//		ic.setMessageDAO(dao);

		ui.addWindowStateListener(new WindowStateListener() {
			@Override
			public void windowStateChanged(WindowEvent arg0) {
				try {
					ks.store(new FileOutputStream("keystore.jks"), PASSWORD);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		ui.setVisible(true);
	}
}
