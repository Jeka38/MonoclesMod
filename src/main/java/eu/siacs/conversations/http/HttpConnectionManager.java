package eu.siacs.conversations.http;

import android.os.Build;
import android.util.Log;

import org.apache.http.conn.ssl.StrictHostnameVerifier;
import static eu.siacs.conversations.utils.Random.SECURE_RANDOM;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.utils.Consumer;
import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.AbstractConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.TLSSocketFactory;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;

public class HttpConnectionManager extends AbstractConnectionManager {

    private final List<HttpDownloadConnection> downloadConnections = new ArrayList<>();
    private final List<HttpUploadConnection> uploadConnections = new ArrayList<>();

    public static final Executor FileTransferExecutor = Executors.newFixedThreadPool(4);

    public static final OkHttpClient OK_HTTP_CLIENT;

    static {
        OK_HTTP_CLIENT = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    final Request original = chain.request();
                    final Request modified = original.newBuilder()
                            .header("User-Agent", getUserAgent())
                            .build();
                    return chain.proceed(modified);
                })
                .build();
    }

    public static String getUserAgent() {
        return String.format("%s/%s", "monocles mod", BuildConfig.VERSION_NAME);
    }

    public HttpConnectionManager(XmppConnectionService service) {
        super(service);
    }

    public static Proxy getProxy(boolean isI2P) {
        final InetAddress localhost;
        try {
            localhost = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        } catch (final UnknownHostException e) {
            throw new IllegalStateException(e);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(localhost, isI2P ? 4447 : 9050));
        } else {
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(localhost, isI2P ? 4444 : 8118));
        }
    }

    public void createNewDownloadConnection(Message message) {
        this.createNewDownloadConnection(message, false);
    }

    public void createNewDownloadConnection(final Message message, boolean interactive) {
        createNewDownloadConnection(message, interactive, null);
    }

    public void createNewDownloadConnection(final Message message, boolean interactive, Consumer<DownloadableFile> cb) {
        synchronized (this.downloadConnections) {
            for (HttpDownloadConnection connection : this.downloadConnections) {
                if (connection.getMessage() == message) {
                    Log.d(Config.LOGTAG, message.getConversation().getAccount().getJid().asBareJid() + ": download already in progress");
                    return;
                }
            }
            final HttpDownloadConnection connection = new HttpDownloadConnection(message, this, cb);
            connection.init(interactive);
            this.downloadConnections.add(connection);
        }
    }

    public void createNewUploadConnection(final Message message, boolean delay) {
        synchronized (this.uploadConnections) {
            for (HttpUploadConnection connection : this.uploadConnections) {
                if (connection.getMessage() == message) {
                    Log.d(Config.LOGTAG, message.getConversation().getAccount().getJid().asBareJid() + ": upload already in progress");
                    return;
                }
            }
            HttpUploadConnection connection = new HttpUploadConnection(message, Method.determine(message.getConversation().getAccount()), this);
            connection.init(delay);
            this.uploadConnections.add(connection);
        }
    }

    void finishConnection(HttpDownloadConnection connection) {
        synchronized (this.downloadConnections) {
            this.downloadConnections.remove(connection);
        }
    }

    void finishUploadConnection(HttpUploadConnection httpUploadConnection) {
        synchronized (this.uploadConnections) {
            this.uploadConnections.remove(httpUploadConnection);
        }
    }

    public OkHttpClient buildHttpClient(final HttpUrl url, final Account account, boolean interactive) {
        return buildHttpClient(url, account, 30, interactive);
    }

    public OkHttpClient buildHttpClient(final HttpUrl url, final Account account, int readTimeout, boolean interactive) {
        final String slotHostname = url.host();
        final boolean onionSlot = slotHostname.endsWith(".onion");
        final boolean I2PSlot = slotHostname.endsWith(".i2p");
        final OkHttpClient.Builder builder = newBuilder(mXmppConnectionService.useTorToConnect() || account.isOnion() || onionSlot , mXmppConnectionService.useI2PToConnect() || account.isI2P() || I2PSlot);
        builder.readTimeout(readTimeout, TimeUnit.SECONDS);
        setupTrustManager(builder, interactive);
        return builder.build();
    }

    private void setupTrustManager(final OkHttpClient.Builder builder, final boolean interactive) {
        final X509TrustManager trustManager;
        if (interactive) {
            trustManager = mXmppConnectionService.getMemorizingTrustManager().getInteractive();
        } else {
            trustManager = mXmppConnectionService.getMemorizingTrustManager().getNonInteractive();
        }
        try {
            final SSLSocketFactory sf = new TLSSocketFactory(new X509TrustManager[]{trustManager}, SECURE_RANDOM);
            builder.sslSocketFactory(sf, trustManager);
            builder.hostnameVerifier(new StrictHostnameVerifier());
        } catch (final KeyManagementException ignored) {
        } catch (final NoSuchAlgorithmException ignored) {
        }
    }

    public static OkHttpClient.Builder newBuilder(final boolean tor, final boolean i2p) {
        final OkHttpClient.Builder builder = OK_HTTP_CLIENT.newBuilder();
        builder.writeTimeout(30, TimeUnit.SECONDS);
        builder.readTimeout(30, TimeUnit.SECONDS);
        if (tor || i2p) {
            builder.proxy(HttpConnectionManager.getProxy(i2p)).build();
        }
        return builder;
    }

    public static InputStream open(final String url, final boolean tor, final boolean i2p) throws IOException {
        return open(HttpUrl.get(url), tor, i2p);
    }

    public static InputStream open(final HttpUrl httpUrl, final boolean tor, final boolean i2p) throws IOException {
        final OkHttpClient client = newBuilder(tor, i2p).build();
        final Request request = new Request.Builder().get().url(httpUrl).build();
        final ResponseBody body = client.newCall(request).execute().body();
        if (body == null) {
            throw new IOException("No response body found");
        }
        return body.byteStream();
    }

    public static String extractFilenameFromResponse(okhttp3.Response response) {
        String filename = null;

        // Try to extract filename from the Content-Disposition header
        String contentDisposition = response.header("Content-Disposition");
        if (contentDisposition != null && contentDisposition.contains("filename=")) {
            String[] parts = contentDisposition.split(";");
            for (String part : parts) {
                if (part.trim().startsWith("filename=")) {
                    filename = part.substring("filename=".length()).trim().replace("\"", "");
                    break;
                }
            }
        }

        // If filename is not found in the Content-Disposition header, try to get it from the URL
        if (filename == null || filename.isEmpty()) {
            HttpUrl httpUrl = response.request().url();
            List<String> pathSegments = httpUrl.pathSegments();
            if (!pathSegments.isEmpty()) {
                filename = pathSegments.get(pathSegments.size() - 1);
            }
        }

        return filename;
    }
}