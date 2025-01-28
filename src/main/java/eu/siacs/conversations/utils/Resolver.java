package eu.siacs.conversations.utils;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.minidns.AbstractDnsClient;
import org.minidns.DnsCache;
import org.minidns.DnsClient;
import org.minidns.cache.LruCache;
import org.minidns.dnsmessage.Question;
import org.minidns.dnsname.DnsName;
import org.minidns.dnssec.DnssecValidationFailedException;
import org.minidns.dnsserverlookup.AndroidUsingExec;
import org.minidns.hla.DnssecResolverApi;
import org.minidns.hla.ResolverApi;
import org.minidns.hla.ResolverResult;
import org.minidns.iterative.ReliableDnsClient;
import org.minidns.record.A;
import org.minidns.record.AAAA;
import org.minidns.record.CNAME;
import org.minidns.record.Data;
import org.minidns.record.InternetAddressRR;
import org.minidns.record.Record;
import org.minidns.record.SRV;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.Jid;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.InetAddresses;
import com.google.common.primitives.Ints;
import com.google.common.base.Strings;


public class Resolver {

    public static final int DEFAULT_PORT_XMPP = 5222;

    private static final String DIRECT_TLS_SERVICE = "_xmpps-client";
    private static final String STARTTLS_SERVICE = "_xmpp-client";

    private static XmppConnectionService SERVICE = null;

    private static List<String> DNSSECLESS_TLDS = Arrays.asList(
        "ae",
        "aero",
        "ai",
        "al",
        "ao",
        "aq",
        "as",
        "ba",
        "bb",
        "bd",
        "bf",
        "bi",
        "bj",
        "bn",
        "bo",
        "bs",
        "bw",
        "cd",
        "cf",
        "cg",
        "ci",
        "ck",
        "cm",
        "cu",
        "cv",
        "cw",
        "dj",
        "dm",
        "do",
        "ec",
        "eg",
        "eh",
        "er",
        "et",
        "fj",
        "fk",
        "ga",
        "ge",
        "gf",
        "gh",
        "gm",
        "gp",
        "gq",
        "gt",
        "gu",
        "hm",
        "ht",
        "im",
        "ir",
        "je",
        "jm",
        "jo",
        "ke",
        "kh",
        "km",
        "kn",
        "kp",
        "kz",
        "ls",
        "mg",
        "mh",
        "mk",
        "ml",
        "mm",
        "mo",
        "mp",
        "mq",
        "ms",
        "mt",
        "mu",
        "mv",
        "mw",
        "mz",
        "ne",
        "ng",
        "ni",
        "np",
        "nr",
        "om",
        "pa",
        "pf",
        "pg",
        "pk",
        "pn",
        "ps",
        "py",
        "qa",
        "rw",
        "sd",
        "sl",
        "sm",
        "so",
        "sr",
        "sv",
        "sy",
        "sz",
        "tc",
        "td",
        "tg",
        "tj",
        "to",
        "tr",
        "va",
        "vg",
        "vi",
        "ye",
        "zm",
        "zw"
    );

    protected static final Map<String, String> knownSRV = ImmutableMap.of(
            "_xmpp-client._tcp.yax.im", "xmpp.yaxim.org",
            "_xmpps-client._tcp.yax.im", "xmpp.yaxim.org",
            "_xmpp-server._tcp.yax.im", "xmpp.yaxim.org"
    );

    public static void init(XmppConnectionService service) {
        Resolver.SERVICE = service;
        DnsClient.removeDNSServerLookupMechanism(AndroidUsingExec.INSTANCE);
        DnsClient.addDnsServerLookupMechanism(AndroidUsingExecLowPriority.INSTANCE);
        DnsClient.addDnsServerLookupMechanism(new AndroidUsingLinkProperties(service));
        final AbstractDnsClient client = ResolverApi.INSTANCE.getClient();
        if (client instanceof ReliableDnsClient) {
            ((ReliableDnsClient) client).setUseHardcodedDnsServers(false);
        }
    }

    public static List<Result> fromHardCoded(final String hostname, final int port) {
        final Result result = new Result();
        result.hostname = DnsName.from(hostname);
        result.port = port;
        result.directTls = useDirectTls(port);
        result.authenticated = true;
        return Collections.singletonList(result);
    }

    public static void checkDomain(final Jid jid) {
        DnsName.from(jid.getDomain());
    }

    public static boolean invalidHostname(final String hostname) {
        try {
            DnsName.from(hostname);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    public static void clearCache() {
        final AbstractDnsClient client = ResolverApi.INSTANCE.getClient();
        final DnsCache dnsCache = client.getCache();
        if (dnsCache instanceof LruCache) {
            Log.d(Config.LOGTAG,"clearing DNS cache");
            ((LruCache) dnsCache).clear();
        }

        final AbstractDnsClient clientSec = DnssecResolverApi.INSTANCE.getClient();
        final DnsCache dnsCacheSec = clientSec.getCache();
        if (dnsCacheSec instanceof LruCache) {
            Log.d(Config.LOGTAG,"clearing DNSSEC cache");
            ((LruCache) dnsCacheSec).clear();
        }
    }


    public static boolean useDirectTls(final int port) {
        return port == 443 || port == 5223;
    }

    public static List<Result> resolve(final String domain) {
        final  List<Result> ipResults = fromIpAddress(domain, DEFAULT_PORT_XMPP);
        if (!ipResults.isEmpty()) {
            return ipResults;
        }
        final List<Result> results = new ArrayList<>();
        final List<Result> fallbackResults = new ArrayList<>();
        final Thread[] threads = new Thread[3];
        threads[0] = new Thread(() -> {
            try {
                final List<Result> list = resolveSrv(domain, true);
                synchronized (results) {
                    results.addAll(list);
                }
            } catch (final Throwable throwable) {
                if (!(Throwables.getRootCause(throwable) instanceof InterruptedException)) {
                    Log.d(Config.LOGTAG, Resolver.class.getSimpleName() + ": error resolving SRV record (direct TLS)", throwable);
                }
            }
        });
        threads[1] = new Thread(() -> {
            try {
                final List<Result> list = resolveSrv(domain, false);
                synchronized (results) {
                    results.addAll(list);
                }
            } catch (final Throwable throwable) {
                if (!(Throwables.getRootCause(throwable) instanceof InterruptedException)) {
                    Log.d(Config.LOGTAG, Resolver.class.getSimpleName() + ": error resolving SRV record (STARTTLS)", throwable);
                }
            }
        });
        threads[2] = new Thread(() -> {
            List<Result> list = resolveNoSrvRecords(DnsName.from(domain), DEFAULT_PORT_XMPP, true);
            synchronized (fallbackResults) {
                fallbackResults.addAll(list);
            }
        });
        for (final Thread thread : threads) {
            thread.start();
        }
        try {
            threads[0].join();
            threads[1].join();
            if (!results.isEmpty()) {
                threads[2].interrupt();
                synchronized (results) {
                    Collections.sort(results);
                    Log.d(Config.LOGTAG, Resolver.class.getSimpleName() + ": " + results);
                    return results;
                }
            } else {
                threads[2].join();
                synchronized (fallbackResults) {
                    Collections.sort(fallbackResults);
                    Log.d(Config.LOGTAG, Resolver.class.getSimpleName() + ": " + fallbackResults);
                    return fallbackResults;
                }
            }
        } catch (InterruptedException e) {
            for (Thread thread : threads) {
                thread.interrupt();
            }
            return Collections.emptyList();
        }
    }

    private static List<Result> fromIpAddress(String domain, int port) {
        if (!IP.matches(domain)) {
            return Collections.emptyList();
        }
        try {
            Result result = new Result();
            result.ip = InetAddress.getByName(domain);
            result.port = port;
            result.authenticated = true;
            return Collections.singletonList(result);
        } catch (UnknownHostException e) {
            return Collections.emptyList();
        }
    }

    private static List<Result> resolveSrv(String domain, final boolean directTls) throws IOException {
        final String dnsNameS = (directTls ? DIRECT_TLS_SERVICE : STARTTLS_SERVICE) + "._tcp." + domain;
        DnsName dnsName = DnsName.from(dnsNameS);
        ResolverResult<SRV> result = resolveWithFallback(dnsName, SRV.class);
        final List<Result> results = new ArrayList<>();
        final List<Thread> threads = new ArrayList<>();
        for (SRV record : result.getAnswersOrEmptySet()) {
            if (record.name.length() == 0 && record.priority == 0) {
                continue;
            }
            final boolean authentic = result.isAuthenticData() || record.target.toString().equals(knownSRV.get(dnsNameS));
            threads.add(new Thread(() -> {
                final List<Result> ipv4s = resolveIp(record, A.class, authentic, directTls);
                if (ipv4s.isEmpty()) {
                    Result resolverResult = Result.fromRecord(record, directTls);
                    resolverResult.authenticated = result.isAuthenticData();
                    ipv4s.add(resolverResult);
                }
                synchronized (results) {
                    results.addAll(ipv4s);
                }

            }));
            threads.add(new Thread(() -> {
                final List<Result> ipv6s = resolveIp(record, AAAA.class, authentic, directTls);
                synchronized (results) {
                    results.addAll(ipv6s);
                }
            }));
        }
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                return Collections.emptyList();
            }
        }
        return results;
    }

    private static <D extends InternetAddressRR> List<Result> resolveIp(SRV srv, Class<D> type, boolean authenticated, boolean directTls) {
        List<Result> list = new ArrayList<>();
        try {
            ResolverResult<D> results = resolveWithFallback(srv.target, type);
            for (D record : results.getAnswersOrEmptySet()) {
                Result resolverResult = Result.fromRecord(srv, directTls);
                resolverResult.authenticated = results.isAuthenticData() && authenticated;
                resolverResult.ip = record.getInetAddress();
                list.add(resolverResult);
            }
        } catch (Throwable t) {
            Log.d(Config.LOGTAG, Resolver.class.getSimpleName() + ": error resolving " + type.getSimpleName() + " " + t.getMessage());
        }
        return list;
    }

    private static List<Result> resolveNoSrvRecords(DnsName dnsName, int port, boolean withCnames) {
        final List<Result> results = new ArrayList<>();
        try {
            ResolverResult<A> aResult = resolveWithFallback(dnsName, A.class);
            for (A a : aResult.getAnswersOrEmptySet()) {
                Result r = Result.createDefault(dnsName, a.getInetAddress(), port);
                r.authenticated = aResult.isAuthenticData();
                results.add(r);
            }
            ResolverResult<AAAA> aaaaResult = resolveWithFallback(dnsName, AAAA.class);
            for (AAAA aaaa : aaaaResult.getAnswersOrEmptySet()) {
                Result r = Result.createDefault(dnsName, aaaa.getInetAddress(), port);
                r.authenticated = aaaaResult.isAuthenticData();
                results.add(r);
            }
            if (results.isEmpty() && withCnames) {
                ResolverResult<CNAME> cnameResult = resolveWithFallback(dnsName, CNAME.class);
                for (CNAME cname : cnameResult.getAnswersOrEmptySet()) {
                    for (Result r : resolveNoSrvRecords(cname.name, port, false)) {
                        r.authenticated = r.authenticated && cnameResult.isAuthenticData();
                        results.add(r);
                    }
                }
            }
        } catch (final Throwable throwable) {
            if (!(Throwables.getRootCause(throwable) instanceof InterruptedException)) {
                Log.d(Config.LOGTAG, Resolver.class.getSimpleName() + "error resolving fallback records", throwable);
            }
        }
        results.add(Result.createDefault(dnsName, port));
        return results;
    }

    private static <D extends Data> ResolverResult<D> resolveWithFallback(DnsName dnsName, Class<D> type) throws IOException {
        final Question question = new Question(dnsName, Record.TYPE.getType(type));
        if (!DNSSECLESS_TLDS.contains(dnsName.getLabels()[0].toString())) {
            try {
                ResolverResult<D> result = DnssecResolverApi.INSTANCE.resolve(question);
                if (result.wasSuccessful() && !result.isAuthenticData()) {
                    Log.d(Config.LOGTAG, "DNSSEC validation failed for " + type.getSimpleName() + " : " + result.getUnverifiedReasons());
                }
                return result;
            } catch (DnssecValidationFailedException e) {
                Log.d(Config.LOGTAG, Resolver.class.getSimpleName() + ": error resolving " + type.getSimpleName() + " with DNSSEC. Trying DNS instead.", e);
            } catch (IOException e) {
                throw e;
            } catch (Throwable throwable) {
                Log.d(Config.LOGTAG, Resolver.class.getSimpleName() + ": error resolving " + type.getSimpleName() + " with DNSSEC. Trying DNS instead.", throwable);
            }
        }
        return ResolverApi.INSTANCE.resolve(question);
    }

    public static class Result implements Comparable<Result> {
        public static final String DOMAIN = "domain";
        public static final String IP = "ip";
        public static final String HOSTNAME = "hostname";
        public static final String PORT = "port";
        public static final String PRIORITY = "priority";
        public static final String DIRECT_TLS = "directTls";
        public static final String AUTHENTICATED = "authenticated";
        public static final String TIME_REQUESTED = "time_requested";

        private InetAddress ip;
        private DnsName hostname;
        private int port = DEFAULT_PORT_XMPP;
        private boolean directTls = false;
        private boolean authenticated = false;
        private int priority;
        private long timeRequested;
        private Socket socket;

        private String logID = "";

        static Result fromRecord(SRV srv, boolean directTls) {
            Result result = new Result();
            result.port = srv.port;
            result.hostname = srv.name;
            result.directTls = directTls;
            result.priority = srv.priority;
            return result;
        }

        static Result createDefault(DnsName hostname, InetAddress ip, int port) {
            Result result = new Result();
            result.timeRequested = System.currentTimeMillis();
            result.port = port;
            result.hostname = hostname;
            result.ip = ip;
            result.directTls = useDirectTls(port);
            return result;
        }

        static Result createDefault(DnsName hostname, int port) {
            return createDefault(hostname, null, port);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Result result = (Result) o;

            if (port != result.port) return false;
            if (directTls != result.directTls) return false;
            if (authenticated != result.authenticated) return false;
            if (priority != result.priority) return false;
            if (ip != null ? !ip.equals(result.ip) : result.ip != null) return false;
            return hostname != null ? hostname.equals(result.hostname) : result.hostname == null;
        }

        @Override
        public int hashCode() {
            int result = ip != null ? ip.hashCode() : 0;
            result = 31 * result + (hostname != null ? hostname.hashCode() : 0);
            result = 31 * result + port;
            result = 31 * result + (directTls ? 1 : 0);
            result = 31 * result + (authenticated ? 1 : 0);
            result = 31 * result + priority;
            return result;
        }

        public InetAddress getIp() {
            return ip;
        }

        public int getPort() {
            return port;
        }

        public DnsName getHostname() {
            return hostname;
        }

        public boolean isDirectTls() {
            return directTls;
        }

        public boolean isAuthenticated() {
            return authenticated;
        }

        public boolean isOutdated() {
            return (System.currentTimeMillis() - timeRequested) > 300_000;
        }

        public Socket getSocket() {
            return socket;
        }

        @NotNull
        @Override
        public String toString() {
            return "Result{" +
                    "ip='" + (ip == null ? null : ip.getHostAddress()) + '\'' +
                    ", hostname='" + (hostname == null ? null : hostname.toString()) + '\'' +
                    ", port=" + port +
                    ", directTls=" + directTls +
                    ", authenticated=" + authenticated +
                    ", priority=" + priority +
                    '}';
        }

        public void connect() {
            if (this.socket != null) {
                this.disconnect();
            }
            if (this.ip == null || this.port == 0) {
                Log.d(Config.LOGTAG, "Resolver did not get IP:port (" + this.ip + ":" + this.port + ")");
                return;
            }
            final InetSocketAddress addr = new InetSocketAddress(this.ip, this.port);
            this.socket = new Socket();
            try {
                long time = System.currentTimeMillis();
                this.socket.connect(addr, Config.SOCKET_TIMEOUT * 1000);
                time = System.currentTimeMillis() - time;
                if (this.logID != null && !this.logID.isEmpty()) {
                    Log.d(Config.LOGTAG, Resolver.class.getSimpleName() + ": Result (" + this.logID + ") connect: " + toString() + " after: " + time + " ms");
                } else {
                    Log.d(Config.LOGTAG, Resolver.class.getSimpleName() + ": Result connect: " + toString() + " after: " + time + " ms");
                }
            } catch (IOException e) {
                e.printStackTrace();
                this.disconnect();
            }
        }

        public void disconnect() {
            if (this.socket != null) {
                FileBackend.close(this.socket);
                this.socket = null;
                if (this.logID != null && !this.logID.isEmpty()) {
                    Log.d(Config.LOGTAG, Resolver.class.getSimpleName() + ": Result (" + this.logID + ") disconnect: " + toString());
                } else {
                    Log.d(Config.LOGTAG, Resolver.class.getSimpleName() + ": Result disconnect: " + toString());
                }
            }
        }

        @Override
        public int compareTo(@NonNull Result result) {
            if (result.priority == priority) {
                if (directTls == result.directTls) {
                    if (ip == null && result.ip == null) {
                        return 0;
                    } else if (ip != null && result.ip != null) {
                        if (ip instanceof Inet4Address && result.ip instanceof Inet4Address) {
                            return 0;
                        } else {
                            return ip instanceof Inet4Address ? -1 : 1;
                        }
                    } else {
                        return ip != null ? -1 : 1;
                    }
                } else {
                    return directTls ? 1 : -1;
                }
            } else {
                return priority - result.priority;
            }
        }

        public Result call() throws Exception {
            this.connect();
            if (this.socket != null && this.socket.isConnected()) {
                return this;
            }
            throw new Exception("Resolver.Result was not possible to connect - should be catched by executor");
        }

        public static Result fromCursor(Cursor cursor) {
            final Result result = new Result();
            try {
                result.ip = InetAddress.getByAddress(cursor.getBlob(cursor.getColumnIndex(IP)));
            } catch (UnknownHostException e) {
                result.ip = null;
            }
            final String hostname = cursor.getString(cursor.getColumnIndex(HOSTNAME));
            result.hostname = hostname == null ? null : DnsName.from(hostname);
            result.port = cursor.getInt(cursor.getColumnIndex(PORT));
            result.directTls = cursor.getInt(cursor.getColumnIndex(DIRECT_TLS)) > 0;
            result.authenticated = cursor.getInt(cursor.getColumnIndex(AUTHENTICATED)) > 0;
            result.priority = cursor.getInt(cursor.getColumnIndex(PRIORITY));
            result.timeRequested = cursor.getLong(cursor.getColumnIndex(TIME_REQUESTED));
            return result;
        }

        public ContentValues toContentValues() {
            final ContentValues contentValues = new ContentValues();
            contentValues.put(IP, ip == null ? null : ip.getAddress());
            contentValues.put(HOSTNAME, hostname == null ? null : hostname.toString());
            contentValues.put(PORT, port);
            contentValues.put(PRIORITY, priority);
            contentValues.put(DIRECT_TLS, directTls ? 1 : 0);
            contentValues.put(AUTHENTICATED, authenticated ? 1 : 0);
            contentValues.put(TIME_REQUESTED, timeRequested);
            return contentValues;
        }

        public Result seeOtherHost(final String seeOtherHost) {
            final String hostname = seeOtherHost.trim();
            if (hostname.isEmpty()) {
                return null;
            }
            final Result result = new Result();
            result.directTls = this.directTls;
            final int portSegmentStart = hostname.lastIndexOf(':');
            if (hostname.charAt(hostname.length() - 1) != ']'
                    && portSegmentStart >= 0
                    && hostname.length() >= portSegmentStart + 1) {
                final String hostPart = hostname.substring(0, portSegmentStart);
                final String portPart = hostname.substring(portSegmentStart + 1);
                final Integer port = Ints.tryParse(portPart);
                if (port == null || Strings.isNullOrEmpty(hostPart)) {
                    return null;
                }
                final String host = eu.siacs.conversations.utils.IP.unwrapIPv6(hostPart);
                result.port = port;
                if (InetAddresses.isInetAddress(host)) {
                    final InetAddress inetAddress;
                    try {
                        inetAddress = InetAddresses.forString(host);
                    } catch (final IllegalArgumentException e) {
                        return null;
                    }
                    result.ip = inetAddress;
                } else {
                    if (hostPart.trim().isEmpty()) {
                        return null;
                    }
                    try {
                        result.hostname = DnsName.from(hostPart.trim());
                    } catch (final Exception e) {
                        return null;
                    }
                }
            } else {
                final String host = eu.siacs.conversations.utils.IP.unwrapIPv6(hostname);
                if (InetAddresses.isInetAddress(host)) {
                    final InetAddress inetAddress;
                    try {
                        inetAddress = InetAddresses.forString(host);
                    } catch (final IllegalArgumentException e) {
                        return null;
                    }
                    result.ip = inetAddress;
                } else {
                    try {
                        result.hostname = DnsName.from(hostname);
                    } catch (final Exception e) {
                        return null;
                    }
                }
                result.port = port;
            }
            return result;
        }
    }
}
