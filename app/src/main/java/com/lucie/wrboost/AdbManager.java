package com.lucie.wrboost;

import android.content.Context;
import android.os.Build;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.Random;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import android.sun.security.x509.AlgorithmId;
import android.sun.security.x509.CertificateAlgorithmId;
import android.sun.security.x509.CertificateExtensions;
import android.sun.security.x509.CertificateIssuerName;
import android.sun.security.x509.CertificateSerialNumber;
import android.sun.security.x509.CertificateSubjectName;
import android.sun.security.x509.CertificateValidity;
import android.sun.security.x509.CertificateVersion;
import android.sun.security.x509.CertificateX509Key;
import android.sun.security.x509.KeyIdentifier;
import android.sun.security.x509.PrivateKeyUsageExtension;
import android.sun.security.x509.SubjectKeyIdentifierExtension;
import android.sun.security.x509.X500Name;
import android.sun.security.x509.X509CertImpl;
import android.sun.security.x509.X509CertInfo;

// Guarda a chave do Booster para o ADB sem fio; ela é criada uma vez e reaproveitada.
public final class AdbManager extends AbsAdbConnectionManager {
    private static AdbManager instance;
    private final PrivateKey key;
    private final Certificate cert;

    public static synchronized AdbManager get(Context c) throws Exception {
        if (instance == null) instance = new AdbManager(c.getApplicationContext());
        return instance;
    }

    private AdbManager(Context c) throws Exception {
        setApi(Build.VERSION.SDK_INT);
        File kf = new File(c.getFilesDir(), "adb.key");
        File cf = new File(c.getFilesDir(), "adb.crt");
        PrivateKey k = null;
        Certificate cr = null;
        if (kf.exists() && cf.exists()) {
            try {
                k = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(read(kf)));
                cr = CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(read(cf)));
            } catch (Exception e) {
                k = null;
                cr = null;
            }
        }
        if (k == null) {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048, new SecureRandom());
            KeyPair pair = g.generateKeyPair();
            PublicKey pub = pair.getPublic();
            k = pair.getPrivate();

            String alg = "SHA512withRSA";
            Date from = new Date();
            Date to = new Date(System.currentTimeMillis() + 3650L * 86400000L);
            CertificateExtensions ext = new CertificateExtensions();
            ext.set("SubjectKeyIdentifier", new SubjectKeyIdentifierExtension(new KeyIdentifier(pub).getIdentifier()));
            X500Name name = new X500Name("CN=Booster");
            ext.set("PrivateKeyUsage", new PrivateKeyUsageExtension(from, to));
            X509CertInfo info = new X509CertInfo();
            info.set("version", new CertificateVersion(2));
            info.set("serialNumber", new CertificateSerialNumber(new Random().nextInt() & Integer.MAX_VALUE));
            info.set("algorithmID", new CertificateAlgorithmId(AlgorithmId.get(alg)));
            info.set("subject", new CertificateSubjectName(name));
            info.set("key", new CertificateX509Key(pub));
            info.set("validity", new CertificateValidity(from, to));
            info.set("issuer", new CertificateIssuerName(name));
            info.set("extensions", ext);
            X509CertImpl impl = new X509CertImpl(info);
            impl.sign(k, alg);
            cr = impl;
            write(kf, k.getEncoded());
            write(cf, cr.getEncoded());
        }
        key = k;
        cert = cr;
    }

    @Override
    protected PrivateKey getPrivateKey() {
        return key;
    }

    @Override
    protected Certificate getCertificate() {
        return cert;
    }

    @Override
    protected String getDeviceName() {
        return "Booster";
    }

    private static byte[] read(File f) throws Exception {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            return o.toByteArray();
        }
    }

    private static void write(File f, byte[] data) throws Exception {
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(data);
        }
    }
}
