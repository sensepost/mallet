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

import static io.netty.util.internal.StringUtil.commonSuffixOfLength;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.IDN;
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
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.KeyManagerFactorySpi;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.util.encoders.Base64;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.SimpleTrustManagerFactory;
import io.netty.util.Mapping;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

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

	private final KeyManagerFactory AUTO_FACTORY = new AutoGeneratingKeyManagerFactory();

	private final SslContextBuilder DEFAULT_SSLCONTEXTBUILDER = getContextBuilderForServerTemplate();

	private final SslContextMapper DEFAULT_MAPPER = new SslContextMapper(
			DEFAULT_SSLCONTEXTBUILDER);

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
				for (int i = 0; i < certs.length; i++) {
					if (certs[i] instanceof X509Certificate) {
						BigInteger serial = ((X509Certificate) certs[i])
								.getSerialNumber();
						serials.add(serial);
					}
				}
			}
		}
	}

	private void create(X500Principal caName) throws GeneralSecurityException,
			IOException {
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
		keyGen.initialize(2048);
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
		synchronized (keyStore) {
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

	public void addCertificateChain(String alias, X509Certificate[] chain, boolean persistent) throws KeyStoreException {
	    for (X509Certificate cert : chain) {
	        
	    }
	    if (keyStore.containsAlias(alias))
	        keyStore.deleteEntry(alias);
	}

	public void importFromKeystore(KeyStore other, char[] password) {
	    // FIXME: allow importing entries from another keystore (e.g. P12)
	}

    /**
     * IDNA ASCII conversion and case normalization
     */
    private static String normalizeHostname(String hostname) {
        if (needsNormalization(hostname)) {
            hostname = IDN.toASCII(hostname, IDN.ALLOW_UNASSIGNED);
        }
        return hostname.toLowerCase(Locale.US);
    }

    private static boolean needsNormalization(String hostname) {
        final int length = hostname.length();
        for (int i = 0; i < length; i++) {
            int c = hostname.charAt(i);
            if (c > 0x7F) {
                return true;
            }
        }
        return false;
    }

    /**
     * Simple function to match <a href="http://en.wikipedia.org/wiki/Wildcard_DNS_record">DNS wildcard</a>.
     */
    private static boolean matches(String template, String hostName) {
        if (template.startsWith("*.")) {
            return template.regionMatches(2, hostName, 0, hostName.length())
                || commonSuffixOfLength(hostName, template, template.length() - 1);
        }
        return template.equals(hostName);
    }

    private X509KeyManager searchKeyStore(String target) throws CertificateParsingException, KeyStoreException, GeneralSecurityException {
        if (keyStore.containsAlias(target)) {
            if (keyStore.isKeyEntry(target)) {
                return KeystoreUtils.getKeyManagerForAlias(keyStore, target,
                        keyPassword);
            } else if (keyStore.isCertificateEntry(target)) {
                // FIXME: clone the certificate chain based on this entry
            }
        } else {
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                Certificate cert = keyStore.getCertificate(alias);
                if (cert instanceof X509Certificate) {
                    X509Certificate xcert = (X509Certificate) cert;
                    Collection<List<?>> sans = xcert.getSubjectAlternativeNames();
                    if (sans != null) {
                        for (List<?> asn : sans) {
                            Integer generalName = (Integer) asn.get(0);
                            if (generalName.equals(GeneralName.dNSName) || generalName.equals(GeneralName.iPAddress)) {
                                String name = (String) asn.get(1);
                                if (matches(name, target)) {
                                    System.err.println("Found match for " + target + " via alias: " + alias);
                                    return KeystoreUtils.getKeyManagerForAlias(keyStore, alias,
                                        keyPassword);
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private X509KeyManager createKeyMaterial(String target)
			throws GeneralSecurityException, IOException,
			OperatorCreationException {
        synchronized (keyStore) {
            if (target == null)
                return searchKeyStore(CA_ALIAS);
            
            target = normalizeHostname(target);
		    X509KeyManager km = searchKeyStore(target);
		    if (km != null) {
		        return km;
		    }

			KeyPair keyPair;
			if (reuseKeys) {
				keyPair = new KeyPair(caCerts[0].getPublicKey(), caKey);
			} else {
				KeyPairGenerator keygen = KeyPairGenerator.getInstance("RSA");
				keygen.initialize(2048);
				keyPair = keygen.generateKeyPair();
			}

			logger.info("Creating key material for " + target);

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

			if (keyStore.isCertificateEntry(target)) {
			    keyStore.deleteEntry(target); // FIXME: rename to _orig, perhaps?
			}
			keyStore.setKeyEntry(target, keyPair.getPrivate(), keyPassword,
					chain);

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
		SslContextBuilder builder = SslContextBuilder.forServer(AUTO_FACTORY);
//		builder.protocols(new String[] { "TLSv1.2" });
		return builder;
	}

	private class SslContextMapper implements Mapping<String, SslContext> {

		private SslContextBuilder builder;

		public SslContextMapper(SslContextBuilder builder) {
			this.builder = builder;
		}

		@Override
		public SslContext map(String target) {
//			if (target == null)
//				throw new NullPointerException("SSLContext requested for null target - SNI failure?");

			try {
				X509KeyManager km = createKeyMaterial(target);
				SslContext sslContext = builder.keyManager(
						km.getPrivateKey(target),
						km.getCertificateChain(target)).build();
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

	private class AutoGeneratingKeyManagerFactory extends KeyManagerFactory {

		public AutoGeneratingKeyManagerFactory() {
			super(new AutoGeneratingKeyManagerFactorySpi(), null, null);
		}

	}

	private class AutoGeneratingKeyManagerFactorySpi extends
			KeyManagerFactorySpi {

	    private AutoGeneratingKeyManager agkm = new AutoGeneratingKeyManager();

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
			return new KeyManager[] { agkm };
		}

	}

	private class AutoGeneratingKeyManager extends X509ExtendedKeyManager {

		@Override
		public String chooseEngineClientAlias(String[] keyType,
				Principal[] issuers, SSLEngine engine) {
			return super.chooseEngineClientAlias(keyType, issuers, engine);
		}

		@Override
		public String chooseEngineServerAlias(String keyType,
				Principal[] issuers, SSLEngine engine) {
			return engine.getPeerHost();
		}

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
				Set<String> aliases = KeystoreUtils.getAliases(keyStore)
						.keySet();
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
