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

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.Mapping;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.KeyManagerFactorySpi;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.X509KeyManager;
import javax.security.auth.x500.X500Principal;

import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.util.encoders.Base64;

public class AutoGeneratingContextSelector implements
		Mapping<String, SslContext> {

	public final static String CA_ALIAS = "ca";

	private KeyStore keyStore;

	private char[] keyPassword;

	private static final long DEFAULT_VALIDITY = 10L * 365L * 24L * 60L * 60L
			* 1000L;

	private static Logger logger = Logger
			.getLogger(AutoGeneratingContextSelector.class.getName());

	private boolean reuseKeys = false;

	private PrivateKey caKey;

	private X509Certificate[] caCerts;

	private Set<BigInteger> serials = new HashSet<BigInteger>();

	private final SslContextMapper DEFAULT_MAPPER = new SslContextMapper(
			getContextBuilderForServerTemplate());

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
	public AutoGeneratingContextSelector(X500Principal ca, KeyStore keyStore,
			char[] keyPassword) throws GeneralSecurityException, IOException {
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
	 * @param keyPassword
	 *            the key password
	 * @throws GeneralSecurityException
	 * @throws IOException
	 */
	public AutoGeneratingContextSelector(KeyStore keystore, char[] keyPassword)
			throws GeneralSecurityException, IOException {
		this.keyStore = keystore;
		this.keyPassword = keyPassword;
		initFromKeyStore();
	}

	private void initFromKeyStore() throws GeneralSecurityException,
			IOException {
		caKey = (PrivateKey) keyStore.getKey(CA_ALIAS, keyPassword);
		Certificate[] certChain = keyStore.getCertificateChain(CA_ALIAS);
		caCerts = new X509Certificate[certChain.length];
		System.arraycopy(certChain, 0, caCerts, 0, certChain.length);

		// make sure we don't reuse any serial numbers
		Enumeration<String> aliases = keyStore.aliases();
		while (aliases.hasMoreElements()) {
			String alias = aliases.nextElement();
			Certificate[] certs = keyStore.getCertificateChain(alias);
			if (certs != null) {
				for (int i=0; i<certs.length; i++) {
					if (certs[i] instanceof X509Certificate) {
						BigInteger serial = ((X509Certificate)certs[i]).getSerialNumber();
						serials.add(serial);
					}
				}
			}
		}
	}

	private void create(X500Principal caName) throws GeneralSecurityException,
			IOException {
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
		keyGen.initialize(1024);
		KeyPair caPair = keyGen.generateKeyPair();
		caKey = caPair.getPrivate();
		PublicKey caPubKey = caPair.getPublic();
		Date begin = new Date();
		Date ends = new Date(begin.getTime() + DEFAULT_VALIDITY);

		try {
			X509Certificate cert = CertificateUtils.sign(caName, caPubKey,
					caName, caPubKey, caKey, begin, ends, BigInteger.ONE, null);
			caCerts = new X509Certificate[] { cert };
		} catch (OperatorCreationException oce) {
			throw new GeneralSecurityException(oce);
		}
		keyStore.setKeyEntry(CA_ALIAS, caKey, keyPassword, caCerts);
	}

	public String getCACert() throws CertificateEncodingException {
		return "-----BEGIN CERTIFICATE-----\n"
				+ Base64.toBase64String(caCerts[0].getEncoded())
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
	public void save(File file, char[] password)
			throws GeneralSecurityException, IOException {
		synchronized(keyStore) {
			OutputStream out = new FileOutputStream(file);
			try {
				keyStore.store(out, password);
			} finally {
				out.close();
			}
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
		return DEFAULT_MAPPER.map(target);
	}

	protected X500Principal getSubjectPrincipal(String target) {
		return new X500Principal("cn=" + target + ",ou=UNTRUSTED,o=UNTRUSTED");
	}

	private X509KeyManager createKeyMaterial(String target)
			throws GeneralSecurityException, IOException,
			OperatorCreationException {
		synchronized(keyStore) {
			if (keyStore.containsAlias(target))
				return KeystoreUtils.getKeyManagerForAlias(keyStore, target,
						keyPassword);
	
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
	
			X509Certificate cert = CertificateUtils.sign(subject,
					keyPair.getPublic(), caCerts[0].getSubjectX500Principal(),
					caCerts[0].getPublicKey(), caKey, begin, ends,
					getNextSerialNo(), null);
	
			X509Certificate[] chain = new X509Certificate[caCerts.length + 1];
			System.arraycopy(caCerts, 0, chain, 1, caCerts.length);
			chain[0] = cert;
	
			keyStore.setKeyEntry(target, keyPair.getPrivate(), keyPassword, chain);
	
			return new SingleX509KeyManager(target, keyPair.getPrivate(), chain);
		}
	}

	protected BigInteger getNextSerialNo() {
		BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
		while (serials.contains(serial))
			serial = serial.add(BigInteger.ONE);
		serials.add(serial);
		return serial;
	}

	public Mapping<String, SslContext> getCustomSslContextMapper(
			SslContextBuilder builder) {
		return new SslContextMapper(builder);
	}

	public SslContextBuilder getContextBuilderForServerTemplate() {
		return SslContextBuilder.forServer(new AutoGeneratingKeyManagerFactory());
	}
	
	private class SslContextMapper implements Mapping<String, SslContext> {

		private SslContextBuilder builder;

		private LinkedHashMap<String, SslContext> cache = new LinkedHashMap<String, SslContext>() {
			protected boolean removeEldestEntry(Map.Entry<String, SslContext> eldest) {
				return size() > 100;
			}
		};

		public SslContextMapper(SslContextBuilder builder) {
			this.builder = builder;
		}

		@Override
		public SslContext map(String target) {
			if (target == null)
				throw new NullPointerException("target");

			synchronized (cache) {
				if (cache.containsKey(target))
					return cache.get(target);
				try {
					X509KeyManager km = createKeyMaterial(target);
					SslContext sslContext = builder.keyManager(
							km.getPrivateKey(target),
							km.getCertificateChain(target)).build();
					cache.put(target, sslContext);
					return sslContext;
				} catch (GeneralSecurityException | OperatorCreationException
						| IOException e) {
					logger.warning("Error obtaining the SSLContext: "
							+ e.getLocalizedMessage());
					e.printStackTrace();
					return null;
				}
			}
		}

	}
	
	private class AutoGeneratingKeyManagerFactory extends KeyManagerFactory {

		public AutoGeneratingKeyManagerFactory() {
			super(new AutoGeneratingKeyManagerFactorySpi(), null, null);
		}
		
	}
	
	private class AutoGeneratingKeyManagerFactorySpi extends KeyManagerFactorySpi {

		@Override
		protected void engineInit(KeyStore ks, char[] password)
				throws KeyStoreException, NoSuchAlgorithmException,
				UnrecoverableKeyException {
			throw new UnsupportedOperationException();
		}

		@Override
		protected void engineInit(ManagerFactoryParameters spec)
				throws InvalidAlgorithmParameterException {
			throw new UnsupportedOperationException();
		}

		@Override
		protected KeyManager[] engineGetKeyManagers() {
			return new KeyManager[] { new AutoGeneratingKeyManager() };
		}
		
	}
	
	private class AutoGeneratingKeyManager implements X509KeyManager {

		@Override
		public String[] getClientAliases(String keyType, Principal[] issuers) {
			return null;
		}

		@Override
		public String chooseClientAlias(String[] keyType, Principal[] issuers,
				Socket socket) {
			return null;
		}

		@Override
		public String[] getServerAliases(String keyType, Principal[] issuers) {
			try {
				Set<String> aliases = KeystoreUtils.getAliases(keyStore).keySet();
				return aliases.toArray(new String[aliases.size()]);
			} catch (KeyStoreException e) {
			}
			return null;
		}

		@Override
		public String chooseServerAlias(String keyType, Principal[] issuers,
				Socket socket) {
			return null;
		}

		@Override
		public X509Certificate[] getCertificateChain(String alias) {
			try {
				return createKeyMaterial(alias).getCertificateChain(alias);
			} catch (OperatorCreationException | GeneralSecurityException
					| IOException e) {
				e.printStackTrace();
			}
			return null;
		}

		@Override
		public PrivateKey getPrivateKey(String alias) {
			try {
				return createKeyMaterial(alias).getPrivateKey(alias);
			} catch (OperatorCreationException | GeneralSecurityException
					| IOException e) {
				e.printStackTrace();
			}
			return null;
		}
		
	}
}
