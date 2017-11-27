package com.sensepost.mallet.ssl;

import java.net.Socket;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;

public class KeyStoreX509KeyManager extends X509ExtendedKeyManager {

	private KeyStore ks;
	private char[] keyPassword;

	public KeyStoreX509KeyManager(KeyStore ks, char[] keyPassword) {
		this.ks = ks;
		this.keyPassword = keyPassword;
	}

	@Override
	public String chooseEngineClientAlias(String[] paramArrayOfString, Principal[] paramArrayOfPrincipal,
			SSLEngine paramSSLEngine) {
		return null;
	}

	@Override
	public String chooseEngineServerAlias(String paramString, Principal[] paramArrayOfPrincipal,
			SSLEngine paramSSLEngine) {
		return null;
	}

	public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
		return null;
	}

	public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
		return null;
	}

	public X509Certificate[] getCertificateChain(String alias) {
		try {
			return copy(ks.getCertificateChain(alias));
		} catch (KeyStoreException e) {
			e.printStackTrace();
			return null;
		}
	}

	public String[] getClientAliases(String keyType, Principal[] issuers) {
		return aliasesForKeyType(keyType);
	}

	public PrivateKey getPrivateKey(String alias) {
		try {
			return (PrivateKey) ks.getKey(alias, keyPassword);
		} catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException e) {
			e.printStackTrace();
			return null;
		}
	}

	public String[] getServerAliases(String keyType, Principal[] issuers) {
		return aliasesForKeyType(keyType);
	}

	private String[] aliasesForKeyType(String keyType) {
		List<String> aliases;
		try {
			aliases = Collections.list(ks.aliases());
			Iterator<String> it = aliases.iterator();
			while (it.hasNext()) {
				String alias = it.next();
				Certificate[] c = ks.getCertificateChain(alias);
				if (!((X509Certificate) c[0]).getPublicKey().getAlgorithm().equals(keyType)) {
					it.remove();
				}
			}
			return aliases.toArray(new String[aliases.size()]);
		} catch (KeyStoreException e) {
			e.printStackTrace();
			return null;
		}
	}

	private X509Certificate[] copy(Certificate[] certs) {
		if (certs == null)
			return null;
		X509Certificate[] copy = new X509Certificate[certs.length];
		System.arraycopy(certs, 0, copy, 0, certs.length);
		return copy;
	}

}
