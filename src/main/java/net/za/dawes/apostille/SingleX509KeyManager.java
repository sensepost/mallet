package net.za.dawes.apostille;

import java.net.Socket;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;

class SingleX509KeyManager extends X509ExtendedKeyManager {

	private String alias;

	private PrivateKey pk;

	private X509Certificate[] certs;

	public SingleX509KeyManager(String alias, PrivateKey pk, Certificate[] certs) {
		this.alias = alias;
		this.pk = pk;
		this.certs = copy(certs);
	}

	public SingleX509KeyManager(String alias, KeyStore keystore, char[] keyPass)
			throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException {
		this.alias = alias;
		this.pk = (PrivateKey) keystore.getKey(alias, keyPass);
		this.certs = copy(keystore.getCertificateChain(alias));
	}

	@Override
	public String chooseEngineClientAlias(String[] paramArrayOfString, Principal[] paramArrayOfPrincipal,
			SSLEngine paramSSLEngine) {
		return alias;
	}

	@Override
	public String chooseEngineServerAlias(String paramString, Principal[] paramArrayOfPrincipal,
			SSLEngine paramSSLEngine) {
		return alias;
	}

	public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
		return alias;
	}

	public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
		return alias;
	}

	public X509Certificate[] getCertificateChain(String alias) {
		return copy(certs);
	}

	public String[] getClientAliases(String keyType, Principal[] issuers) {
		return new String[] { alias };
	}

	public PrivateKey getPrivateKey(String alias) {
		return pk;
	}

	public String[] getServerAliases(String keyType, Principal[] issuers) {
		return new String[] { alias };
	}

	private X509Certificate[] copy(Certificate[] certs) {
		if (certs == null)
			return null;
		X509Certificate[] copy = new X509Certificate[certs.length];
		System.arraycopy(certs, 0, copy, 0, certs.length);
		return copy;
	}

}