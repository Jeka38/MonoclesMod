/* MemorizingTrustManager - a TrustManager which asks the user about invalid
 *  certificates and memorizes their decision.
 *
 * Copyright (c) 2010 Georg Lukas <georg@op-co.de>
 *
 * MemorizingTrustManager.java contains the actual trust manager and interface
 * code to create a MemorizingActivity and obtain the results.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package eu.siacs.conversations.services;

import android.app.Application;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;
import android.util.SparseArray;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.Consumer;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.BundledTrustManager;
import eu.siacs.conversations.crypto.CombiningTrustManager;
import eu.siacs.conversations.crypto.TrustManagers;
import eu.siacs.conversations.crypto.XmppDomainVerifier;
import eu.siacs.conversations.entities.MTMDecision;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.ui.MemorizingActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.minidns.dane.DaneVerifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * A X509 trust manager implementation which asks the user about invalid certificates and memorizes
 * their decision.
 *
 * <p>The certificate validity is checked using the system default X509 TrustManager, creating a
 * query Dialog if the check fails.
 *
 * <p><b>WARNING:</b> This only works if a dedicated thread is used for opening sockets!
 */
public class MemorizingTrustManager {

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    static final String DECISION_INTENT = "de.duenndns.ssl.DECISION";
    public static final String DECISION_INTENT_ID = DECISION_INTENT + ".decisionId";
    public static final String DECISION_INTENT_CERT = DECISION_INTENT + ".cert";
    public static final String DECISION_TITLE_ID = DECISION_INTENT + ".titleId";
    static final String NO_TRUST_ANCHOR = "Trust anchor for certification path not found.";
    private static final Pattern PATTERN_IPV4 =
            Pattern.compile(
                    "\\A(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z");
    private static final Pattern PATTERN_IPV6_HEX4DECCOMPRESSED =
            Pattern.compile(
                    "\\A((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?) ::((?:[0-9A-Fa-f]{1,4}:)*)(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z");
    private static final Pattern PATTERN_IPV6_6HEX4DEC =
            Pattern.compile(
                    "\\A((?:[0-9A-Fa-f]{1,4}:){6,6})(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z");
    private static final Pattern PATTERN_IPV6_HEXCOMPRESSED =
            Pattern.compile(
                    "\\A((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)\\z");
    private static final Pattern PATTERN_IPV6 =
            Pattern.compile("\\A(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\\z");
    private static final Logger LOGGER = Logger.getLogger(MemorizingTrustManager.class.getName());
    static String KEYSTORE_DIR = "KeyStore";
    static String KEYSTORE_FILE = "KeyStore.bks";
    private static int decisionId = 0;
    private static final SparseArray<MTMDecision> openDecisions = new SparseArray<MTMDecision>();
    Context master;
    AppCompatActivity foregroundAct;
    NotificationManager notificationManager;
    Handler masterHandler;
    private File keyStoreFile;
    private KeyStore appKeyStore;
    private final X509TrustManager defaultTrustManager;
    private X509TrustManager appTrustManager;
    private final DaneVerifier daneVerifier;

    /**
     * Creates an instance of the MemorizingTrustManager class that falls back to a custom
     * TrustManager.
     *
     * <p>You need to supply the application context. This has to be one of: - Application -
     * Activity - Service
     *
     * <p>The context is used for file management, to display the dialog / notification and for
     * obtaining translated strings.
     *
     * @param context Context for the application.
     * @param defaultTrustManager Delegate trust management to this TM. If null, the user must
     *     accept every certificate.
     */
    public MemorizingTrustManager(
            final Context context, final X509TrustManager defaultTrustManager) {
        init(context);
        this.appTrustManager = getTrustManager(appKeyStore);
        this.defaultTrustManager = defaultTrustManager;
        this.daneVerifier = new DaneVerifier();
    }

    /**
     * Creates an instance of the MemorizingTrustManager class using the system X509TrustManager.
     *
     * <p>You need to supply the application context. This has to be one of: - Application -
     * Activity - Service
     *
     * <p>The context is used for file management, to display the dialog / notification and for
     * obtaining translated strings.
     *
     * @param context Context for the application.
     */
    public MemorizingTrustManager(final Context context) {
        init(context);
        this.appTrustManager = getTrustManager(appKeyStore);
        this.daneVerifier = new DaneVerifier();
        try {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
                this.defaultTrustManager = defaultWithBundledLetsEncrypt(context);
            } else {
                this.defaultTrustManager = TrustManagers.createDefaultTrustManager();
            }
        } catch (final NoSuchAlgorithmException
                       | KeyStoreException
                       | CertificateException
                       | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static X509TrustManager defaultWithBundledLetsEncrypt(final Context context)
            throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {
        final BundledTrustManager bundleTrustManager =
                BundledTrustManager.builder()
                        .loadKeyStore(
                                context.getResources().openRawResource(R.raw.letsencrypt),
                                "letsencrypt")
                        .build();
        return CombiningTrustManager.combineWithDefault(bundleTrustManager);
    }

    private static boolean isIp(final String server) {
        return server != null
                && (PATTERN_IPV4.matcher(server).matches()
                || PATTERN_IPV6.matcher(server).matches()
                || PATTERN_IPV6_6HEX4DEC.matcher(server).matches()
                || PATTERN_IPV6_HEX4DECCOMPRESSED.matcher(server).matches()
                || PATTERN_IPV6_HEXCOMPRESSED.matcher(server).matches());
    }

    private static String getBase64Hash(X509Certificate certificate, String digest)
            throws CertificateEncodingException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(digest);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        md.update(certificate.getEncoded());
        return Base64.encodeToString(md.digest(), Base64.NO_WRAP);
    }

    private static String hexString(byte[] data) {
        StringBuffer si = new StringBuffer();
        for (int i = 0; i < data.length; i++) {
            si.append(String.format("%02x", data[i]));
            if (i < data.length - 1) si.append(":");
        }
        return si.toString();
    }

    private static String certHash(final X509Certificate cert, String digest) {
        try {
            MessageDigest md = MessageDigest.getInstance(digest);
            md.update(cert.getEncoded());
            return hexString(md.digest());
        } catch (CertificateEncodingException | NoSuchAlgorithmException e) {
            return e.getMessage();
        }
    }

    public static void interactResult(int decisionId, int choice) {
        MTMDecision d;
        synchronized (openDecisions) {
            d = openDecisions.get(decisionId);
            openDecisions.remove(decisionId);
        }
        if (d == null) {
            LOGGER.log(Level.SEVERE, "interactResult: aborting due to stale decision reference!");
            return;
        }
        synchronized (d) {
            d.state = choice;
            d.notify();
        }
    }

    void init(final Context context) {
        master = context;
        masterHandler = new Handler(context.getMainLooper());
        notificationManager =
                (NotificationManager) master.getSystemService(Context.NOTIFICATION_SERVICE);

        Application app;
        if (context instanceof Application) {
            app = (Application) context;
        } else if (context instanceof Service) {
            app = ((Service) context).getApplication();
        } else if (context instanceof AppCompatActivity) {
            app = ((AppCompatActivity) context).getApplication();
        } else
            throw new ClassCastException(
                    "MemorizingTrustManager context must be either Activity or Service!");

        File dir = app.getDir(KEYSTORE_DIR, Context.MODE_PRIVATE);
        keyStoreFile = new File(dir + File.separator + KEYSTORE_FILE);

        appKeyStore = loadAppKeyStore();
    }

    /**
     * Get a list of all certificate aliases stored in MTM.
     *
     * @return an {@link Enumeration} of all certificates
     */
    public Enumeration<String> getCertificates() {
        try {
            return appKeyStore.aliases();
        } catch (KeyStoreException e) {
            // this should never happen, however...
            throw new RuntimeException(e);
        }
    }

    /**
     * Removes the given certificate from MTMs key store.
     *
     * <p><b>WARNING</b>: this does not immediately invalidate the certificate. It is well possible
     * that (a) data is transmitted over still existing connections or (b) new connections are
     * created using TLS renegotiation, without a new cert check.
     *
     * @param alias the certificate's alias as returned by {@link #getCertificates()}.
     * @throws KeyStoreException if the certificate could not be deleted.
     */
    public void deleteCertificate(String alias) throws KeyStoreException {
        appKeyStore.deleteEntry(alias);
        keyStoreUpdated();
    }

    private X509TrustManager getTrustManager(final KeyStore keyStore) {
        Preconditions.checkNotNull(keyStore);
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
            tmf.init(keyStore);
            for (TrustManager t : tmf.getTrustManagers()) {
                if (t instanceof X509TrustManager) {
                    return (X509TrustManager) t;
                }
            }
        } catch (final Exception e) {
            // Here, we are covering up errors. It might be more useful
            // however to throw them out of the constructor so the
            // embedding app knows something went wrong.
            LOGGER.log(Level.SEVERE, "getTrustManager(" + keyStore + ")", e);
        }
        return null;
    }

    KeyStore loadAppKeyStore() {
        KeyStore ks;
        try {
            ks = KeyStore.getInstance(KeyStore.getDefaultType());
        } catch (KeyStoreException e) {
            LOGGER.log(Level.SEVERE, "getAppKeyStore()", e);
            return null;
        }
        FileInputStream fileInputStream = null;
        try {
            ks.load(null, null);
            fileInputStream = new FileInputStream(keyStoreFile);
            ks.load(fileInputStream, "MTM".toCharArray());
        } catch (java.io.FileNotFoundException e) {
            LOGGER.log(Level.INFO, "getAppKeyStore(" + keyStoreFile + ") - file does not exist");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "getAppKeyStore(" + keyStoreFile + ")", e);
        } finally {
            FileBackend.close(fileInputStream);
        }
        return ks;
    }

    void storeCert(String alias, Certificate cert) {
        try {
            appKeyStore.setCertificateEntry(alias, cert);
        } catch (KeyStoreException e) {
            LOGGER.log(Level.SEVERE, "storeCert(" + cert + ")", e);
            return;
        }
        keyStoreUpdated();
    }

    void storeCert(X509Certificate cert) {
        storeCert(cert.getSubjectDN().toString(), cert);
    }

    void keyStoreUpdated() {
        // reload appTrustManager
        appTrustManager = getTrustManager(appKeyStore);

        // store KeyStore to file
        java.io.FileOutputStream fos = null;
        try {
            fos = new java.io.FileOutputStream(keyStoreFile);
            appKeyStore.store(fos, "MTM".toCharArray());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "storeCert(" + keyStoreFile + ")", e);
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "storeCert(" + keyStoreFile + ")", e);
                }
            }
        }
    }

    // if the certificate is stored in the app key store, it is considered "known"
    private boolean isCertKnown(X509Certificate cert) {
        try {
            return appKeyStore.getCertificateAlias(cert) != null;
        } catch (KeyStoreException e) {
            return false;
        }
    }

    private void checkCertTrusted(
            X509Certificate[] chain,
            String authType,
            String domain,
            boolean isServer,
            boolean interactive,
            String verifiedHostname,
            int port,
            Consumer<Boolean> daneCb)
            throws CertificateException {
        LOGGER.log(
                Level.FINE, "checkCertTrusted(" + chain + ", " + authType + ", " + isServer + ")");
        try {
            LOGGER.log(Level.FINE, "checkCertTrusted: trying appTrustManager");
            if (isServer) {
                if (verifiedHostname != null) {
                    try {
                        if (daneVerifier.verifyCertificateChain(chain, verifiedHostname, port)) {
                            if (daneCb != null) daneCb.accept(true);
                            return;
                        }
                    } catch (final CertificateException e) {
                        Log.d(Config.LOGTAG, "checkCertTrusted DANE failure: " + e);
                        throw e;
                    } catch (final Throwable e) {
                        Log.d(Config.LOGTAG, "checkCertTrusted DANE related failure: " + e);
                    }
                }
                appTrustManager.checkServerTrusted(chain, authType);
            } else
                appTrustManager.checkClientTrusted(chain, authType);
        } catch (final CertificateException ae) {
            LOGGER.log(Level.FINER, "checkCertTrusted: appTrustManager failed", ae);
            if (isCertKnown(chain[0])) {
                LOGGER.log(
                        Level.INFO, "checkCertTrusted: accepting cert already stored in keystore");
                return;
            }
            try {
                if (defaultTrustManager == null) throw ae;
                LOGGER.log(Level.FINE, "checkCertTrusted: trying defaultTrustManager");
                if (isServer) defaultTrustManager.checkServerTrusted(chain, authType);
                else defaultTrustManager.checkClientTrusted(chain, authType);
            } catch (final CertificateException e) {
                if (interactive) {
                    interactCert(chain, authType, e);
                } else {
                    throw e;
                }
            }
        }
    }

    private X509Certificate[] getAcceptedIssuers() {
        return defaultTrustManager == null
                ? new X509Certificate[0]
                : defaultTrustManager.getAcceptedIssuers();
    }

    private int createDecisionId(MTMDecision d) {
        int myId;
        synchronized (openDecisions) {
            myId = decisionId;
            openDecisions.put(myId, d);
            decisionId += 1;
        }
        return myId;
    }

    private void certDetails(
            final StringBuffer si, final X509Certificate c, final boolean showValidFor) {

        si.append("\n");
        if (showValidFor) {
            try {
                si.append("Valid for: ");
                si.append(Joiner.on(", ").join(XmppDomainVerifier.parseValidDomains(c).all()));
            } catch (final CertificateParsingException e) {
                si.append("Unable to parse Certificate");
            }
            si.append("\n");
        } else {
            si.append(c.getSubjectDN());
        }
        si.append("\n");
        si.append(DATE_FORMAT.format(c.getNotBefore()));
        si.append(" - ");
        si.append(DATE_FORMAT.format(c.getNotAfter()));
        si.append("\nSHA-256: ");
        si.append(certHash(c, "SHA-256"));
        si.append("\nSHA-1: ");
        si.append(certHash(c, "SHA-1"));
        si.append("\nSigned by: ");
        si.append(c.getIssuerDN().toString());
        si.append("\n");
    }

    private String certChainMessage(final X509Certificate[] chain, CertificateException cause) {
        Throwable e = cause;
        LOGGER.log(Level.FINE, "certChainMessage for " + e);
        final StringBuffer si = new StringBuffer();
        if (e.getCause() != null) {
            e = e.getCause();
            // HACK: there is no sane way to check if the error is a "trust anchor
            // not found", so we use string comparison.
            if (NO_TRUST_ANCHOR.equals(e.getMessage())) {
                si.append(master.getString(R.string.mtm_trust_anchor));
            } else si.append(e.getLocalizedMessage());
            si.append("\n");
        }
        si.append("\n");
        si.append(master.getString(R.string.mtm_connect_anyway));
        si.append("\n\n");
        si.append(master.getString(R.string.mtm_cert_details));
        si.append('\n');
        for (int i = 0; i < chain.length; ++i) {
            certDetails(si, chain[i], i == 0);
        }
        return si.toString();
    }

    /**
     * Returns the top-most entry of the activity stack.
     *
     * @return the Context of the currently bound UI or the master context if none is bound
     */
    Context getUI() {
        return (foregroundAct != null) ? foregroundAct : master;
    }

    int interact(final String message, final int titleId) {
        /* prepare the MTMDecision blocker object */
        MTMDecision choice = new MTMDecision();
        final int myId = createDecisionId(choice);

        masterHandler.post(
                new Runnable() {
                    public void run() {
                        Intent ni = new Intent(master, MemorizingActivity.class);
                        ni.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ni.setData(Uri.parse(MemorizingTrustManager.class.getName() + "/" + myId));
                        ni.putExtra(DECISION_INTENT_ID, myId);
                        ni.putExtra(DECISION_INTENT_CERT, message);
                        ni.putExtra(DECISION_TITLE_ID, titleId);

                        // we try to directly start the activity and fall back to
                        // making a notification
                        try {
                            getUI().startActivity(ni);
                        } catch (Exception e) {
                            LOGGER.log(Level.FINE, "startActivity(MemorizingActivity)", e);
                        }
                    }
                });

        LOGGER.log(Level.FINE, "openDecisions: " + openDecisions + ", waiting on " + myId);
        try {
            synchronized (choice) {
                choice.wait();
            }
        } catch (InterruptedException e) {
            LOGGER.log(Level.FINER, "InterruptedException", e);
        }
        LOGGER.log(Level.FINE, "finished wait on " + myId + ": " + choice.state);
        return choice.state;
    }

    void interactCert(final X509Certificate[] chain, String authType, CertificateException cause)
            throws CertificateException {
        switch (interact(certChainMessage(chain, cause), R.string.mtm_accept_cert)) {
            case MTMDecision.DECISION_ALWAYS:
                storeCert(chain[0]); // only store the server cert, not the whole chain
            case MTMDecision.DECISION_ONCE:
                break;
            default:
                throw (cause);
        }
    }

    public X509TrustManager getNonInteractive(String domain, String verifiedHostname, int port, Consumer<Boolean> daneCb) {
        return new NonInteractiveMemorizingTrustManager(domain, verifiedHostname, port, daneCb);
    }

    public X509TrustManager getInteractive(String domain, String verifiedHostname, int port, Consumer<Boolean> daneCb) {
        return new InteractiveMemorizingTrustManager(domain, verifiedHostname, port, daneCb);
    }

    public X509TrustManager getNonInteractive() {
        return new NonInteractiveMemorizingTrustManager(null, null, 0, null);
    }

    public X509TrustManager getInteractive() {
        return new InteractiveMemorizingTrustManager(null, null, 0, null);
    }

    private class NonInteractiveMemorizingTrustManager implements X509TrustManager {

        private final String domain;
        private final String verifiedHostname;
        private final int port;
        private final Consumer<Boolean> daneCb;

        public NonInteractiveMemorizingTrustManager(String domain, String verifiedHostname, int port, Consumer<Boolean> daneCb) {
            this.domain = domain;
            this.verifiedHostname = verifiedHostname;
            this.port = port;
            this.daneCb = daneCb;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            MemorizingTrustManager.this.checkCertTrusted(chain, authType, domain, false, false, verifiedHostname, port, daneCb);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            MemorizingTrustManager.this.checkCertTrusted(chain, authType, domain, true, false, verifiedHostname, port, daneCb);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return MemorizingTrustManager.this.getAcceptedIssuers();
        }
    }

    private class InteractiveMemorizingTrustManager implements X509TrustManager {
        private final String domain;
        private final String verifiedHostname;
        private final int port;
        private final Consumer<Boolean> daneCb;

        public InteractiveMemorizingTrustManager(String domain, String verifiedHostname, int port, Consumer<Boolean> daneCb) {
            this.domain = domain;
            this.verifiedHostname = verifiedHostname;
            this.port = port;
            this.daneCb = daneCb;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            MemorizingTrustManager.this.checkCertTrusted(chain, authType, domain, false, true, verifiedHostname, port, daneCb);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            MemorizingTrustManager.this.checkCertTrusted(chain, authType, domain, true, true, verifiedHostname, port, daneCb);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return MemorizingTrustManager.this.getAcceptedIssuers();
        }
    }
}
