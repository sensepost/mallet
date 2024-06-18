package net.za.dawes.apostille;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.DSAParameterSpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.AnnotatedException;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class Apostille {

    private final static char[] KEY_PASSWORD = "password".toCharArray();

    private KeyStore keystore;
    private char[] keyPass;

    public Apostille() throws KeyStoreException {
        this(KeyStore.getInstance(KeyStore.getDefaultType()), KEY_PASSWORD);
    }

    public Apostille(KeyStore keystore, char[] keyPass) {
        this.keystore = keystore;
        this.keyPass = new char[keyPass.length];
        System.arraycopy(keyPass, 0, this.keyPass, 0, keyPass.length);
    }

    /**
     * Returns the X509Certificate of the cloned Certificate Authority that anchors the provided path
     * @param path
     * @return
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException 
     */
    public X509Certificate getCaCertificate(X509Certificate[] path) throws KeyStoreException, NoSuchAlgorithmException {
        int lastCertificate = path.length - 1;
        if (lastCertificate < 0)
            throw new ArrayIndexOutOfBoundsException(lastCertificate);
        X500Principal ca = path[lastCertificate].getIssuerX500Principal();
        String caDN = getDistinguishedName(ca);
        Certificate[] caChain = keystore.getCertificateChain(caDN);
        if (caChain != null && caChain.length > 0)
            return (X509Certificate) caChain[0];
        return null;
    }

    /**
     * Converts an X500Principal to a String, according to RFC1779
     * 
     * @param principal
     * @return
     */
    public String getDistinguishedName(X500Principal principal) {
        return principal.getName(X500Principal.RFC1779);
    }

    /**
     * Reads a PEM encoded series of certificates from an InputStream, and returns
     * an array of certificates
     * 
     * @param in the InputStream to read the certificates from
     * @return the certificates
     * @throws IOException
     * @throws CertificateException
     */
    public static X509Certificate[] certsFromStream(InputStream in) throws IOException, CertificateException {
        CertificateFactory fact = CertificateFactory.getInstance("X.509");
        Collection<? extends Certificate> certs = fact.generateCertificates(in);
        List<X509Certificate> ret = new ArrayList<>();
        Iterator<? extends Certificate> it = certs.iterator();
        while (it.hasNext()) {
            Certificate cert = it.next();
            if (cert instanceof X509Certificate) {
                X509Certificate xcert = (X509Certificate) cert;
                ret.add(xcert);
            }
        }
        return ret.toArray(new X509Certificate[ret.size()]);
    }

    public X509KeyManager getKeyManager(String dn)
            throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException {
        return new SingleX509KeyManager(dn, keystore, keyPass);
    }

    /**
     * Clones a certificate chain, and returns the distinguished name of the leaf
     * certificate which can be used to look up the entry in the keystore, if one is
     * passed in, or to retrieve an appropriate KeyManager from Apostille if the
     * keystore is managed by Apostille.
     * 
     * @param certs the certificates to clone
     * @return the distinguished name of the leaf certificate, a.k.a alias in the
     *         keystore
     * 
     * @throws NoSuchAlgorithmException
     * @throws InvalidAlgorithmParameterException
     * @throws KeyStoreException
     * @throws CertificateException
     * @throws UnrecoverableKeyException
     * @throws OperatorCreationException
     * @throws AnnotatedException
     * @throws IOException
     */
    public String cloneCertificates(X509Certificate... certs) throws NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, KeyStoreException, CertificateException, UnrecoverableKeyException,
            OperatorCreationException, AnnotatedException, IOException {
        return cloneCertificates(0, certs);
    }

    private String cloneCertificates(int index, X509Certificate... certs) throws NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, KeyStoreException, CertificateException, UnrecoverableKeyException,
            OperatorCreationException, AnnotatedException, IOException {
        if (certs == null || certs.length == 0)
            throw new NullPointerException("Certs cannot be null or zero-length!");

        if (index == certs.length)
            throw new ArrayIndexOutOfBoundsException(index);

        X509Certificate cert = certs[index];
        X500Principal subject = cert.getSubjectX500Principal();
        String subjectDN = getDistinguishedName(subject);

        // We have already cloned this certificate, here is the alias
        if (keystore.containsAlias(subjectDN) && keystore.isKeyEntry(subjectDN))
            return subjectDN;

        X500Principal issuer = cert.getIssuerX500Principal();

        X509KeyManager caKm = null;

        // if this is not self-signed, find the parent CA
        if (!issuer.equals(subject)) {
            String caDN = null;
            if (certs.length > index + 1) {
                caDN = cloneCertificates(index + 1, certs);
                caKm = getKeyManager(caDN);
            } else {
                caDN = cloneTrustedCertificate(issuer);
                if (caDN != null)
                    caKm = getKeyManager(caDN);
            }
            if (caKm == null) {
                System.err.println("WARNING: Cannot find certificate details for '" + issuer
                        + ", will self-sign instead.\n" + "If this is not what you want, find the CA certificate for '"
                        + caDN + "', and add it to the keystore passed as a parameter on the command line");
                // FIXME: Figure out a way to construct a fake
                // CA certificate here, based on the issuer name
                // and other details in the certificate
            }
        }

        // generate a key pair that matches the parameters of the
        // certificates public key
        KeyPair keyPair = generateKeyPair(cert.getPublicKey());

        Certificate[] chain = copyAndSign(cert, keyPair);
        keystore.setKeyEntry(subjectDN, keyPair.getPrivate(), keyPass, chain);
        return subjectDN;
    }

    private String cloneTrustedCertificate(X500Principal issuer) throws KeyStoreException, NoSuchAlgorithmException,
            UnrecoverableKeyException, InvalidAlgorithmParameterException, CertificateException,
            OperatorCreationException, AnnotatedException, IOException {
        String dn = getDistinguishedName(issuer);
        if (keystore.containsAlias(dn)) {
            return dn;
        } else {
            X509Certificate caCert = getTrustedCertificate(issuer);
            if (caCert != null)
                return cloneCertificates(caCert);
        }
        return null;
    }

    private Certificate[] copyAndSign(X509Certificate cert, KeyPair keyPair)
            throws CertificateException, OperatorCreationException, AnnotatedException, IOException, KeyStoreException,
            UnrecoverableKeyException, NoSuchAlgorithmException {
        Date startDate = cert.getNotBefore();
        Date expiryDate = cert.getNotAfter();
        BigInteger serialNumber = cert.getSerialNumber();
        X500Principal subject = cert.getSubjectX500Principal();
        X500Principal issuer = cert.getIssuerX500Principal();

        Certificate[] chain = new Certificate[1];
        PrivateKey caKey;
        if (!subject.equals(issuer)) {
            String issuerDN = getDistinguishedName(issuer);
            if (keystore.containsAlias(issuerDN)) {
                Certificate[] caChain = keystore.getCertificateChain(issuerDN);
                X509Certificate caCert = (X509Certificate) caChain[0];
                if (!caCert.getSubjectX500Principal().equals(caCert.getIssuerX500Principal())) {
                    chain = new Certificate[caChain.length + 1];
                    System.arraycopy(caChain, 0, chain, 1, caChain.length);
                }
                caKey = (PrivateKey) keystore.getKey(issuerDN, keyPass);
            } else {
                System.err.println("Could not find keystore entry for " + issuer + ", self-signing " + subject);
                issuer = subject;
                caKey = keyPair.getPrivate();
            }
        } else {
            caKey = keyPair.getPrivate();
        }

        X509v3CertificateBuilder generator = new JcaX509v3CertificateBuilder(issuer, serialNumber, startDate,
                expiryDate, subject, keyPair.getPublic());

        Set<Extension> extensions = getExtensions(cert);
        for (Extension extension : extensions) {
            generator.addExtension(extension);
        }

        chain[0] = signCertificate(generator, caKey, cert.getSigAlgName());

        return chain;
    }

    private X509Certificate getTrustedCertificate(X500Principal subject)
            throws NoSuchAlgorithmException, KeyStoreException {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory
                .getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init((KeyStore) null);
        TrustManager[] tms = trustManagerFactory.getTrustManagers();
        for (TrustManager tm : tms) {
            X509Certificate[] certs = ((X509TrustManager) tm).getAcceptedIssuers();
            for (X509Certificate cert : certs) {
                if (subject.equals(cert.getSubjectX500Principal())) {
                    return cert;
                }
            }
        }
        return null;
    }

    private KeyPair generateKeyPair(PublicKey pubKey)
            throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        String keyAlg = pubKey.getAlgorithm();
        AlgorithmParameterSpec params = null;
        if ("RSA".equals(keyAlg)) {
            RSAPublicKey rsaKey = (RSAPublicKey) pubKey;
            params = new RSAKeyGenParameterSpec(rsaKey.getModulus().bitLength(), rsaKey.getPublicExponent());
        } else if ("EC".equals(keyAlg)) {
            ECPublicKey ecKey = (ECPublicKey) pubKey;
            params = ecKey.getParams();
        } else if ("DSA".equals(keyAlg)) {
            DSAPublicKey dsaKey = (DSAPublicKey) pubKey;
            DSAParams p = dsaKey.getParams();
            params = new DSAParameterSpec(p.getP(), p.getQ(), p.getG());
        } else {
            throw new UnsupportedOperationException("No support for " + keyAlg);
        }

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(keyAlg);
        keyGen.initialize(params);
        return keyGen.generateKeyPair();
    }

    private X509Certificate signCertificate(X509v3CertificateBuilder certificateBuilder, PrivateKey privateKey,
            String signatureAlgorithm) throws CertificateException, OperatorCreationException {
        ContentSigner signer = new JcaContentSignerBuilder(signatureAlgorithm).build(privateKey);
        X509CertificateHolder holder = certificateBuilder.build(signer);
        JcaX509CertificateConverter converter = new JcaX509CertificateConverter();
        return converter.getCertificate(holder);
    }

    private Set<Extension> getExtensions(X509Certificate cert) throws AnnotatedException, IOException {
        Set<Extension> extensions = new LinkedHashSet<>();
        Set<String> criticalExtensionOids = cert.getCriticalExtensionOIDs();
        for (String oid : criticalExtensionOids) {
            ASN1Primitive ap = getExtensionValue(cert, oid);
            Extension extension = new Extension(new ASN1ObjectIdentifier(oid), true, ap.getEncoded());
            extensions.add(extension);
        }
        Set<String> nonCriticalExtensionOids = cert.getNonCriticalExtensionOIDs();
        for (String oid : nonCriticalExtensionOids) {
            ASN1Primitive ap = getExtensionValue(cert, oid);
            Extension extension = new Extension(new ASN1ObjectIdentifier(oid), false, ap.getEncoded());
            extensions.add(extension);
        }
        return extensions;
    }

    private ASN1Primitive getExtensionValue(java.security.cert.X509Extension ext, String oid)
            throws AnnotatedException {
        byte[] bytes = ext.getExtensionValue(oid);
        return bytes == null ? null : getObject(oid, bytes);
    }

    private ASN1Primitive getObject(String oid, byte[] ext) throws AnnotatedException {
        try {
            ASN1InputStream aIn = new ASN1InputStream(ext);
            ASN1OctetString octs = (ASN1OctetString) aIn.readObject();
            aIn.close();

            aIn = new ASN1InputStream(octs.getOctets());
            ASN1Primitive p = aIn.readObject();
            aIn.close();

            return p;
        } catch (Exception e) {
            throw new AnnotatedException("exception processing extension " + oid, e);
        }
    }

}
