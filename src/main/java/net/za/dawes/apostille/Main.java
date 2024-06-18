package net.za.dawes.apostille;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

public class Main {

    private static X509Certificate[] certsFromServer(String host, int port)
            throws IOException, UnknownHostException, NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        LoggingTrustManager tm = new LoggingTrustManager();
        sslContext.init(null, new TrustManager[] { tm }, null);

        SSLSocketFactory factory = sslContext.getSocketFactory();
        SSLSocket socket = (SSLSocket) factory.createSocket(host, port);
        try {
            socket.startHandshake();
        } catch (Exception e) {
        } finally {
            socket.close();
        }
        if (tm.certs == null)
            throw new RuntimeException("Couldn't get the certs");
        return tm.certs;
    }

    private static class LoggingTrustManager implements X509TrustManager {

        X509Certificate[] certs;

        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            certs = new X509Certificate[chain.length];
            System.arraycopy(chain, 0, certs, 0, chain.length);
        }

        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    };

    private static void outputKeyAndFullCertificateChain(String alias, X509KeyManager km, X509Certificate ca,
            Writer out) throws IOException {
        try (JcaPEMWriter w = new JcaPEMWriter(out)) {
            w.write("Key for " + alias + "\n");
            w.writeObject(km.getPrivateKey(alias));
            X509Certificate[] certs = km.getCertificateChain(alias);

            if (ca != null) {
                X509Certificate[] fullChain = new X509Certificate[certs.length + 1];
                System.arraycopy(certs, 0, fullChain, 0, certs.length);
                fullChain[certs.length] = ca;
                certs = fullChain;
            }

            for (int i = 0; i < certs.length; i++) {
                w.write("Certificate " + i + ": Subject = " + certs[i].getSubjectX500Principal() + "\n");
                w.write("Certificate " + i + ": Issuer  = " + certs[i].getIssuerX500Principal() + "\n");
                w.writeObject(certs[i]);
            }
        }
    }

    private static void dumpKeystore(String message, KeyStore keystore) throws KeyStoreException {
        Enumeration<String> aliases = keystore.aliases();
        System.err.println(message);
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            System.err.println("Alias: " + alias);
        }
        System.err.println();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 4) {
            System.out.println("Usage: java -jar apostille.jar <src> [dst.jks [<keystore_password> [<key_password>]]]");
            System.out.println();
            System.out.println("\tWhere <src> can be a file containing a certificate chain in PEM format");
            System.out.println("\tor a hostname:port to connect to, to obtain the certificate chain");
            System.out.println();
            System.out.println("You can optionally provide a keystore to save intermediate private keys into");
            System.out.println("which can be used on a later run with a different certificate to maintain a");
            System.out.println("consistent certificate hierarchy.");
            System.out.println(
                    "If the keystore password or key password are not provided, they will default to 'password'");
            System.exit(0);
        }
        File src = new File(args[0]);
        File dst = args.length > 1 ? new File(args[1]) : null;
        char[] ksp = (args.length > 2 ? args[2] : "password").toCharArray();
        char[] kp = (args.length > 3 ? args[3] : "password").toCharArray();
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());

        if (dst != null && dst.exists()) {
            try {
                keystore.load(new FileInputStream(dst), ksp);
            } catch (IOException e) {
                System.err.println("Error loading keystore: " + e.getLocalizedMessage());
                System.exit(1);
            }
            dumpKeystore("Keystore had the following aliases: ", keystore);
        } else {
            keystore.load(null, ksp);
        }

        Apostille apostille = new Apostille(keystore, kp);
        X509Certificate[] certs = null;

        if (src.exists()) {
            certs = Apostille.certsFromStream(new FileInputStream(src));
        } else {
            int c = args[0].indexOf(':');
            if (c > 0) {
                String host = args[0].substring(0, c);
                int port = Integer.parseInt(args[0].substring(c + 1));
                certs = certsFromServer(host, port);
            } else {
                System.err.println("Certificate file " + src + " not found, and it doesn't look like a host:port");
                System.exit(1);
            }
        }

        if (certs == null) {
            System.err.println("Unable to clone certificates!");
            System.exit(1);
        }

        try (Writer out = new OutputStreamWriter(new CloseIgnoringOutputStream(System.out))) {
            String dn = apostille.cloneCertificates(certs);
            X509KeyManager km = apostille.getKeyManager(dn);
            X509Certificate ca = apostille.getCaCertificate(certs);
            outputKeyAndFullCertificateChain(dn, km, ca, out);
        }

        dumpKeystore("Keystore has the following aliases: ", keystore);

        if (dst != null) {
            try {
                keystore.store(new FileOutputStream(dst), ksp);
            } catch (IOException e) {
                System.err.println("Error saving keystore: " + e.getMessage());
            }
        }
    }
}
