/*
 * This file is part of the OWASP Proxy, a free intercepting proxy library.
 * Copyright (C) 2008-2010 Rogan Dawes <rogan@dawes.za.net>
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to:
 * The Free Software Foundation, Inc., 
 * 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */

package com.sensepost.mallet.ssl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.net.ssl.X509KeyManager;
import javax.security.auth.x500.X500Principal;

import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.util.encoders.Base64;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.Mapping;

public class AutoGeneratingContextSelector implements Mapping<String, SslContext> {

	public final static String CA_ALIAS = "ca";

	private KeyStore keyStore;

	private char[] keyPassword;

	private static final long DEFAULT_VALIDITY = 10L * 365L * 24L * 60L * 60L * 1000L;

	private static Logger logger = Logger.getLogger(AutoGeneratingContextSelector.class.getName());

	private boolean reuseKeys = false;

	private Map<String, SslContext> contextCache = new HashMap<String, SslContext>();

	private PrivateKey caKey;

	private X509Certificate[] caCerts;

	private Set<BigInteger> serials = new HashSet<BigInteger>();

	/**
	 * creates a {@link AutoGeneratingContextSelector} that will create a RSA
	 * {@link KeyPair} and self-signed {@link X509Certificate} based on the
	 * {@link X500Principal} supplied. The user can call
	 * {@link #save(File, String, char[], char[], String)} to save the generated
	 * details at a later stage.
	 * 
	 * @param ca
	 * @throws GeneralSecurityException
	 * @throws IOException
	 * @throws OperatorCreationException
	 */
	public AutoGeneratingContextSelector(X500Principal ca, KeyStore keyStore, char[] keyPassword)
			throws GeneralSecurityException, IOException {
		this.keyStore = keyStore;
		this.keyPassword = keyPassword;
		create(ca);
	}

	/**
	 * creates a {@link AutoGeneratingContextSelector} that will load its CA
	 * {@link PrivateKey} and {@link X509Certificate} chain from the indicated
	 * keystore
	 * 
	 * @param keystore
	 *            the location of the keystore
	 * @param type
	 *            the keystore type
	 * @param password
	 *            the keystore password
	 * @param keyPassword
	 *            the key password
	 * @param caAlias
	 *            the alias of the key entry
	 * @throws GeneralSecurityException
	 * @throws IOException
	 */
	public AutoGeneratingContextSelector(KeyStore keystore, char[] keyPassword)
			throws GeneralSecurityException, IOException {
		this.keyStore = keystore;
		this.keyPassword = keyPassword;
		initFromKeyStore();
	}

	private void initFromKeyStore() throws GeneralSecurityException, IOException {
		caKey = (PrivateKey) keyStore.getKey(CA_ALIAS, keyPassword);
		Certificate[] certChain = keyStore.getCertificateChain(CA_ALIAS);
		caCerts = new X509Certificate[certChain.length];
		System.arraycopy(certChain, 0, caCerts, 0, certChain.length);
		// FIXME: Should initialise the serials cache from the keystore
	}

	private void create(X500Principal caName) throws GeneralSecurityException, IOException {
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
		keyGen.initialize(1024);
		KeyPair caPair = keyGen.generateKeyPair();
		caKey = caPair.getPrivate();
		PublicKey caPubKey = caPair.getPublic();
		Date begin = new Date();
		Date ends = new Date(begin.getTime() + DEFAULT_VALIDITY);

		try {
			X509Certificate cert = CertificateUtils.sign(caName, caPubKey, caName, caPubKey, caKey, begin, ends,
					BigInteger.ONE, null);
			caCerts = new X509Certificate[] { cert };
		} catch (OperatorCreationException oce) {
			throw new GeneralSecurityException(oce);
		}
		keyStore.setKeyEntry(CA_ALIAS, caKey, keyPassword, caCerts);
	}

	public String getCACert() throws CertificateEncodingException {
		return "-----BEGIN CERTIFICATE-----\n" + Base64.toBase64String(caCerts[0].getEncoded())
				+ "\n-----END CERTIFICATE-----\n";

	}

	/**
	 * Saves the KeyStore to the specified file
	 * 
	 * @param keyStore
	 *            the file to save the keystore to
	 * @param password
	 *            the keystore password
	 * @throws GeneralSecurityException
	 * @throws IOException
	 */
	public void save(File file, char[] password) throws GeneralSecurityException, IOException {
		OutputStream out = new FileOutputStream(file);
		try {
			keyStore.store(out, password);
		} finally {
			out.close();
		}
	}

	/**
	 * Determines whether the public and private key used for the CA will be
	 * reused for other hosts as well.
	 * 
	 * This is mostly just a performance optimisation, to save time generating a
	 * key pair for each host. Paranoid clients may have an issue with this, in
	 * theory.
	 * 
	 * @param reuse
	 *            true to reuse the CA key pair, false to generate a new key
	 *            pair for each host
	 */
	public synchronized void setReuseKeys(boolean reuse) {
		reuseKeys = reuse;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.owasp.proxy.daemon.CertificateProvider#getSocketFactory(java.lang
	 * .String, int)
	 */
	public synchronized SslContext map(String target) {
		if (target == null)
			throw new NullPointerException("target");
		
		SslContext sslContext = contextCache.get(target);
		if (sslContext == null) {
			try {
				X509KeyManager km = createKeyMaterial(target);
				sslContext = SslContextBuilder.forServer(km.getPrivateKey(target), km.getCertificateChain(target))
						.build();
				contextCache.put(target, sslContext);
			} catch (GeneralSecurityException gse) {
				logger.warning("Error obtaining the SSLContext: " + gse.getLocalizedMessage());
				gse.printStackTrace();
			} catch (OperatorCreationException e) {
				logger.warning("Error obtaining the SSLContext: " + e.getLocalizedMessage());
				e.printStackTrace();
			} catch (IOException e) {
				logger.warning("Error obtaining the SSLContext: " + e.getLocalizedMessage());
				e.printStackTrace();
			}
		}
		return sslContext;
	}

	protected X500Principal getSubjectPrincipal(String target) {
		return new X500Principal("cn=" + target + ",ou=UNTRUSTED,o=UNTRUSTED");
	}

	private X509KeyManager createKeyMaterial(String target)
			throws GeneralSecurityException, IOException, OperatorCreationException {
		if (keyStore.containsAlias(target))
			return KeystoreUtils.getKeyManagerForAlias(keyStore, target, keyPassword);

		KeyPair keyPair;
		if (reuseKeys) {
			keyPair = new KeyPair(caCerts[0].getPublicKey(), caKey);
		} else {
			KeyPairGenerator keygen = KeyPairGenerator.getInstance("RSA");
			keygen.initialize(1024);
			keyPair = keygen.generateKeyPair();
		}

		X500Principal subject = getSubjectPrincipal(target);
		Date begin = new Date();
		Date ends = new Date(begin.getTime() + DEFAULT_VALIDITY);

		X509Certificate cert = CertificateUtils.sign(subject, keyPair.getPublic(), caCerts[0].getSubjectX500Principal(),
				caCerts[0].getPublicKey(), caKey, begin, ends, getNextSerialNo(), null);

		X509Certificate[] chain = new X509Certificate[caCerts.length + 1];
		System.arraycopy(caCerts, 0, chain, 1, caCerts.length);
		chain[0] = cert;

		keyStore.setKeyEntry(target, keyPair.getPrivate(), keyPassword, chain);

		return new SingleX509KeyManager(target, keyPair.getPrivate(), chain);
	}

	protected BigInteger getNextSerialNo() {
		BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
		while (serials.contains(serial))
			serial = serial.add(BigInteger.ONE);
		serials.add(serial);
		return serial;
	}

}
