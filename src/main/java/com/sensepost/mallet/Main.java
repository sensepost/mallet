package com.sensepost.mallet;

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
import java.security.cert.Certificate;

import javax.security.auth.x500.X500Principal;

import com.sensepost.mallet.graph.Graph;
import com.sensepost.mallet.ssl.AutoGeneratingContextSelector;
import com.sensepost.mallet.swing.InterceptFrame;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

public class Main {

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
		InterceptFrame ui = new InterceptFrame();
		ui.setSize(800, 600);

		final SslContext clientContext = SslContextBuilder.forClient()
				.trustManager(InsecureTrustManagerFactory.INSTANCE).build();

		AutoGeneratingContextSelector serverCertMapping = getServerSslContextSelector();

		Graph graph = new Graph(serverCertMapping, clientContext);
		ui.setGraph(graph);
		ui.setVisible(true);
	}
}
