package eu.siacs.conversations.services;

import static eu.siacs.conversations.persistance.FileBackend.APP_DIRECTORY;
import static eu.siacs.conversations.services.NotificationService.EXPORT_BACKUP_NOTIFICATION_ID;
import static eu.siacs.conversations.services.NotificationService.NOTIFICATION_ID;
import static eu.siacs.conversations.utils.Compatibility.runsTwentySix;
import static eu.siacs.conversations.utils.StorageHelper.getAppLogsDirectory;
import static eu.siacs.conversations.utils.StorageHelper.getBackupDirectory;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import com.google.common.base.CharMatcher;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import androidx.core.app.NotificationCompat;

import com.google.common.base.Strings;

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.SQLiteAxolotlStore;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.utils.BackupFileHeader;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.WakeLockHelper;
import eu.siacs.conversations.xmpp.Jid;
import static eu.siacs.conversations.utils.Compatibility.s;

public class ExportBackupService extends Service {

    private PowerManager.WakeLock wakeLock;
    private PowerManager pm;

    public static final String KEYTYPE = "AES";
    public static final String CIPHERMODE = "AES/GCM/NoPadding";
    public static final String PROVIDER = "BC";

    public static final String MIME_TYPE = "application/vnd.conversations.backup";

    boolean ReadableLogsEnabled = false;
    private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private static final String MESSAGE_STRING_FORMAT = "(%s) %s: %s\n";

    private static final int PAGE_SIZE = 20;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private DatabaseBackend mDatabaseBackend;
    private List<Account> mAccounts;
    private NotificationManager notificationManager;

    private static List<Intent> getPossibleFileOpenIntents(final Context context, final String path) {

        //http://www.openintents.org/action/android-intent-action-view/file-directory
        //do not use 'vnd.android.document/directory' since this will trigger system file manager
        final Intent openIntent = new Intent(Intent.ACTION_VIEW);
        openIntent.addCategory(Intent.CATEGORY_DEFAULT);
        if (Compatibility.runsAndTargetsTwentyFour(context)) {
            openIntent.setType("resource/folder");
        } else {
            openIntent.setDataAndType(Uri.parse("file://" + path), "resource/folder");
        }
        openIntent.putExtra("org.openintents.extra.ABSOLUTE_PATH", path);

        final Intent amazeIntent = new Intent(Intent.ACTION_VIEW);
        amazeIntent.setDataAndType(Uri.parse("com.amaze.filemanager:" + path), "resource/folder");

        //will open a file manager at root and user can navigate themselves
        final Intent systemFallBack = new Intent(Intent.ACTION_VIEW);
        systemFallBack.addCategory(Intent.CATEGORY_DEFAULT);
        systemFallBack.setData(Uri.parse("content://com.android.externalstorage.documents/root/primary"));

        return Arrays.asList(openIntent, amazeIntent, systemFallBack);
    }

    private static void accountExport(
            final SQLiteDatabase db, final String uuid, final JsonWriter writer)
            throws IOException {
        final Cursor accountCursor =
                db.query(
                        Account.TABLENAME,
                        null,
                        Account.UUID + "=?",
                        new String[] {uuid},
                        null,
                        null,
                        null);
        while (accountCursor != null && accountCursor.moveToNext()) {
            writer.beginObject();
            writer.name("table");
            writer.value(Account.TABLENAME);
            writer.name("values");
            writer.beginObject();
            for (int i = 0; i < accountCursor.getColumnCount(); ++i) {
                final String name = accountCursor.getColumnName(i);
                writer.name(name);
                final String value = accountCursor.getString(i);
                if (value == null || Account.ROSTERVERSION.equals(accountCursor.getColumnName(i))) {
                    writer.nullValue();
                } else if (Account.OPTIONS.equals(accountCursor.getColumnName(i))
                        && value.matches("\\d+")) {
                    int intValue = Integer.parseInt(value);
                    intValue |= 1 << Account.OPTION_DISABLED;
                    writer.value(intValue);
                } else {
                    writer.value(value);
                }
            }
            writer.endObject();
            writer.endObject();
        }
        if (accountCursor != null) {
            accountCursor.close();
        }
    }

    private static void simpleExport(
            final SQLiteDatabase db,
            final String table,
            final String column,
            final String uuid,
            final JsonWriter writer)
            throws IOException {
        final Cursor cursor =
                db.query(table, null, column + "=?", new String[] {uuid}, null, null, null);
        while (cursor != null && cursor.moveToNext()) {
            writer.beginObject();
            writer.name("table");
            writer.value(table);
            writer.name("values");
            writer.beginObject();
            for (int i = 0; i < cursor.getColumnCount(); ++i) {
                final String name = cursor.getColumnName(i);
                writer.name(name);
                final String value = cursor.getString(i);
                writer.value(value);
            }
            writer.endObject();
            writer.endObject();
        }
        if (cursor != null) {
            cursor.close();
        }
    }

    public static byte[] getKey(final String password, final byte[] salt)
            throws InvalidKeySpecException {
        final SecretKeyFactory factory;
        try {
            factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        return factory.generateSecret(new PBEKeySpec(password.toCharArray(), salt, 1024, 128))
                .getEncoded();
    }


    @Override
    public void onCreate() {
        mDatabaseBackend = DatabaseBackend.getInstance(getBaseContext());
        mAccounts = mDatabaseBackend.getAccounts();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        final SharedPreferences ReadableLogs = PreferenceManager.getDefaultSharedPreferences(this);
        ReadableLogsEnabled = ReadableLogs.getBoolean("export_plain_text_logs", getResources().getBoolean(R.bool.plain_text_logs));
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, Config.LOGTAG + ": ExportLogsService");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (RUNNING.compareAndSet(false, true)) {
            new Thread(() -> {
                if (intent == null) {
                    return;
                }
                Bundle extras = null;
                if (intent != null && intent.getExtras() != null) {
                    extras = intent.getExtras();
                }
                boolean notify = false;
                if (extras != null && extras.containsKey("NOTIFY_ON_BACKUP_COMPLETE")) {
                    notify = extras.getBoolean("NOTIFY_ON_BACKUP_COMPLETE");
                }
                boolean success;
                List<File> files;
                try {
                    exportSettings();
                    files = export(intent.getBooleanExtra("monocles_db", true));
                    success = files != null;
                } catch (final Exception e) {
                    Log.d(Config.LOGTAG, "unable to create backup", e);
                    success = false;
                    files = Collections.emptyList();
                }
                try {
                    if (ReadableLogsEnabled) {  // todo
                        List<Conversation> conversations = mDatabaseBackend.getConversations(Conversation.STATUS_AVAILABLE);
                        conversations.addAll(mDatabaseBackend.getConversations(Conversation.STATUS_ARCHIVED));
                        for (Conversation conversation : conversations) {
                            writeToFile(conversation);
                            Log.d(Config.LOGTAG, "Exporting readable logs for " + conversation.getJid());
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                stopForeground(true);
                RUNNING.set(false);
                if (success) {
                    notifySuccess(files, notify);
                    FileBackend.deleteOldBackups(new File(getBackupDirectory(null)), this.mAccounts);
                } else {
                    notifyError();
                }
                WakeLockHelper.release(wakeLock);
                stopSelf();
            }).start();
            return START_STICKY;
        } else {
            Log.d(Config.LOGTAG, "ExportBackupService. ignoring start command because already running");
        }
        return START_NOT_STICKY;
    }

    private void messageExport(final SQLiteDatabase db, final String uuid, final JsonWriter writer, final Progress progress)
                    throws IOException {
        Cursor cursor;
        if (runsTwentySix()) {
            try {
                // not select and create column Message.FILE_DELETED to be compareable with conversations
                // in C Message.DELETED = Message.FILE_DELETED in PAM so do not select this column, too.
                cursor = db.rawQuery("select messages." + String.join(", messages.", new String[]{
                        Message.UUID, Message.CONVERSATION, Message.TIME_SENT, Message.COUNTERPART, Message.TRUE_COUNTERPART,
                        Message.BODY, Message.ENCRYPTION, Message.STATUS, Message.TYPE, Message.RELATIVE_FILE_PATH,
                        Message.SERVER_MSG_ID, Message.FINGERPRINT, Message.CARBON, Message.EDITED, Message.READ,
                        Message.OOB, Message.ERROR_MESSAGE, Message.READ_BY_MARKERS, Message.MARKABLE,
                        Message.REMOTE_MSG_ID, Message.CONVERSATION
                }) + " from messages join conversations on conversations.uuid=messages.conversationUuid where conversations.accountUuid=?", new String[]{uuid});
            } catch (Exception e) {
                e.printStackTrace();
                cursor = null;
            }
        } else {
            cursor = db.rawQuery("select messages.* from messages join conversations on conversations.uuid=messages.conversationUuid where conversations.accountUuid=?", new String[]{uuid});
        }
        int size = cursor != null ? cursor.getCount() : 0;
        Log.d(Config.LOGTAG, "exporting " + size + " messages for account " + uuid);
        int i = 0;
        int p = 0;
        try {
            while (cursor != null && cursor.moveToNext()) {
                writer.beginObject();
                writer.name("table");
                writer.value(Message.TABLENAME);
                writer.name("values");
                writer.beginObject();
                for (int j = 0; j < cursor.getColumnCount(); ++j) {
                    final String name = cursor.getColumnName(j);
                    writer.name(name);
                    final String value = cursor.getString(j);
                    writer.value(value);
                }
                writer.endObject();
                writer.endObject();
                final int percentage = i * 100 / size;
                if (p < percentage) {
                    p = percentage;
                    notificationManager.notify(EXPORT_BACKUP_NOTIFICATION_ID, progress.build(p));
                }
                i++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void messageExportmonocles(final SQLiteDatabase db, final String uuid, final JsonWriter writer, final Progress progress) throws IOException {
        Cursor cursor = db.rawQuery("select mmessages.* from messages join monocles.messages mmessages using (uuid) join conversations on conversations.uuid=messages.conversationUuid where conversations.accountUuid=?", new String[]{uuid});
        int size = cursor != null ? cursor.getCount() : 0;
        Log.d(Config.LOGTAG, "exporting " + size + " monocles messages for account " + uuid);
        int i = 0;
        int p = 0;
        while (cursor != null && cursor.moveToNext()) {
            writer.beginObject();
            writer.name("table");
            writer.value("monocles." + Message.TABLENAME);
            writer.name("values");
            writer.beginObject();
            for (int j = 0; j < cursor.getColumnCount(); ++j) {
                final String name = cursor.getColumnName(j);
                writer.name(name);
                final String value = cursor.getString(j);
                writer.value(value);
            }
            writer.endObject();
            writer.endObject();
            final int percentage = i * 100 / size;
            if (p < percentage) {
                p = percentage;
                notificationManager.notify(EXPORT_BACKUP_NOTIFICATION_ID, progress.build(p));
            }
            i++;
        }
        if (cursor != null) {
            cursor.close();
        }

        cursor = db.rawQuery("select webxdc_updates.* from " + Conversation.TABLENAME + " join monocles.webxdc_updates webxdc_updates on " + Conversation.TABLENAME + ".uuid=webxdc_updates." + Message.CONVERSATION + " where conversations.accountUuid=?", new String[]{uuid});
        size = cursor != null ? cursor.getCount() : 0;
        Log.d(Config.LOGTAG, "exporting " + size + " WebXDC updates for account " + uuid);
        while (cursor != null && cursor.moveToNext()) {
            writer.beginObject();
            writer.name("table");
            writer.value("monocles.webxdc_updates");
            writer.name("values");
            writer.beginObject();
            for (int j = 0; j < cursor.getColumnCount(); ++j) {
                final String name = cursor.getColumnName(j);
                writer.name(name);
                final String value = cursor.getString(j);
                writer.value(value);
            }
            writer.endObject();
            writer.endObject();
            final int percentage = i * 100 / size;
            if (p < percentage) {
                p = percentage;
                notificationManager.notify(EXPORT_BACKUP_NOTIFICATION_ID, progress.build(p));
            }
            i++;
        }
        if (cursor != null) {
            cursor.close();
        }
    }

    private List<File> export(boolean withmonoclesDb) throws Exception {
        wakeLock.acquire(15 * 60 * 1000L /*15 minutes*/);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getBaseContext(), NotificationService.BACKUP_CHANNEL_ID);
        mBuilder.setContentTitle(getString(R.string.notification_create_backup_title))
                .setSmallIcon(R.drawable.ic_archive_white_24dp)
                .setProgress(1, 0, false);
        startForeground(EXPORT_BACKUP_NOTIFICATION_ID, mBuilder.build());
        int count = 0;
        final int max = this.mAccounts.size();
        final SecureRandom secureRandom = new SecureRandom();
        final List<File> files = new ArrayList<>();
        Log.d(Config.LOGTAG, "starting backup for " + max + " accounts");
        Log.d(Config.LOGTAG, "backup settings " + exportSettings());
        for (final Account account : this.mAccounts) {
            try {
                final String password = account.getPassword();
                if (Strings.nullToEmpty(password).trim().isEmpty()) {
                    Log.d(Config.LOGTAG, String.format("skipping backup for %s because password is empty. unable to encrypt", account.getJid().asBareJid()));
                    continue;
                }
                Log.d(Config.LOGTAG, String.format("exporting data for account %s (%s)", account.getJid().asBareJid(), account.getUuid()));
                final byte[] IV = new byte[12];
                final byte[] salt = new byte[16];
                secureRandom.nextBytes(IV);
                secureRandom.nextBytes(salt);
                final BackupFileHeader backupFileHeader = new BackupFileHeader(getString(R.string.app_name), account.getJid(), System.currentTimeMillis(), IV, salt);
                final Progress progress = new Progress(mBuilder, max, count);
                final File file = new File(getBackupDirectory(null), account.getJid().asBareJid().toEscapedString() + "_" + ((new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")).format(new Date())) + ".ceb");
                files.add(file);
                final File directory = file.getParentFile();
                if (directory != null && directory.mkdirs()) {
                    Log.d(Config.LOGTAG, "created backup directory " + directory.getAbsolutePath());
                }
                final FileOutputStream fileOutputStream = new FileOutputStream(file);
                final DataOutputStream dataOutputStream = new DataOutputStream(fileOutputStream);
                backupFileHeader.write(dataOutputStream);
                dataOutputStream.flush();

                final Cipher cipher = Compatibility.runsTwentyEight() ? Cipher.getInstance(CIPHERMODE) : Cipher.getInstance(CIPHERMODE, PROVIDER);
                final byte[] key = getKey(password, salt);
                Log.d(Config.LOGTAG, backupFileHeader.toString());
                SecretKeySpec keySpec = new SecretKeySpec(key, KEYTYPE);
                IvParameterSpec ivSpec = new IvParameterSpec(IV);
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
                CipherOutputStream cipherOutputStream = new CipherOutputStream(fileOutputStream, cipher);
                final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(cipherOutputStream);
                final JsonWriter jsonWriter =
                        new JsonWriter(
                                new OutputStreamWriter(gzipOutputStream, StandardCharsets.UTF_8));
                jsonWriter.beginArray();
                final SQLiteDatabase db = this.mDatabaseBackend.getReadableDatabase();
                final String uuid = account.getUuid();
                accountExport(db, uuid, jsonWriter);
                simpleExport(db, Conversation.TABLENAME, Conversation.ACCOUNT, uuid, jsonWriter);
                messageExport(db, uuid, jsonWriter, progress);
                if (withmonoclesDb) messageExportmonocles(db, uuid, jsonWriter, progress);
                for (String table : Arrays.asList(SQLiteAxolotlStore.PREKEY_TABLENAME, SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME, SQLiteAxolotlStore.SESSION_TABLENAME, SQLiteAxolotlStore.IDENTITIES_TABLENAME)) {
                    simpleExport(db, table, SQLiteAxolotlStore.ACCOUNT, uuid, jsonWriter);
                }
                jsonWriter.endArray();
                jsonWriter.flush();
                jsonWriter.close();
                mediaScannerScanFile(file);
                Log.d(Config.LOGTAG, "written backup to " + file.getAbsoluteFile());
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "backup for " + account.getJid() + " failed with " + e);
            }
            count++;
        }
        stopForeground(true);
        notificationManager.cancel(EXPORT_BACKUP_NOTIFICATION_ID);
        return files;
    }

    private boolean exportSettings() {
        boolean success = false;
        ObjectOutputStream output = null;
        try {
            final File file = new File(getBackupDirectory(null), "settings.dat");
            final File directory = file.getParentFile();
            if (directory != null && directory.mkdirs()) {
                Log.d(Config.LOGTAG, "created backup directory " + directory.getAbsolutePath());
            }
            output = new ObjectOutputStream(new FileOutputStream(file));
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            output.writeObject(pref.getAll());
            success = true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (output != null) {
                    output.flush();
                    output.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return success;
    }

    private void mediaScannerScanFile(final File file) {
        final Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.setData(Uri.fromFile(file));
        sendBroadcast(intent);
    }

    private void notifySuccess(final List<File> files, final boolean notify) {
        if (!notify) {
            return;
        }
        final String path = getBackupDirectory(null);
        PendingIntent openFolderIntent = null;
        for (final Intent intent : getPossibleFileOpenIntents(this, path)) {
            if (intent.resolveActivityInfo(getPackageManager(), 0) != null) {

                openFolderIntent = PendingIntent.getActivity(this, 189, intent, s()
                        ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                        : PendingIntent.FLAG_UPDATE_CURRENT);
                break;
            }
        }

        PendingIntent shareFilesIntent = null;
        if (files.size() > 0) {
            final Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            ArrayList<Uri> uris = new ArrayList<>();
            for (File file : files) {
                uris.add(FileBackend.getUriForFile(this, file));
            }
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setType(MIME_TYPE);
            final Intent chooser = Intent.createChooser(intent, getString(R.string.share_backup_files));
            shareFilesIntent = PendingIntent.getActivity(this, 190, chooser, s()
                    ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                    : PendingIntent.FLAG_UPDATE_CURRENT);
        }

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getBaseContext(), "backup");
        mBuilder.setContentTitle(getString(R.string.notification_backup_created_title))
                .setContentText(getString(R.string.notification_backup_created_subtitle, path))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_backup_created_subtitle, getBackupDirectory(null))))
                .setAutoCancel(true)
                .setContentIntent(openFolderIntent)
                .setSmallIcon(R.drawable.ic_archive_white_24dp);
        if (shareFilesIntent != null) {
            mBuilder.addAction(R.drawable.ic_share_white_24dp, getString(R.string.share_backup_files), shareFilesIntent);
        }

        try { Thread.sleep(500); } catch (final Exception e) { }
        notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    private void notifyError() {
        final String path = getBackupDirectory(null);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getBaseContext(), "backup");
        mBuilder.setContentTitle(getString(R.string.notification_backup_failed_title))
                .setContentText(getString(R.string.notification_backup_failed_subtitle, path))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_backup_failed_subtitle, getBackupDirectory(null))))
                .setAutoCancel(true)
                .setSmallIcon(R.drawable.ic_warning_white_24dp);
        notificationManager.notify(EXPORT_BACKUP_NOTIFICATION_ID, mBuilder.build());
    }

    private void writeToFile(Conversation conversation) {
        Jid accountJid = resolveAccountUuid(conversation.getAccountUuid());
        Jid contactJid = conversation.getJid();
        final File dir = new File(getAppLogsDirectory(), accountJid.asBareJid().toString());
        dir.mkdirs();

        BufferedWriter bw = null;
        try {
            for (Message message : mDatabaseBackend.getMessagesIterable(conversation)) {
                if (message == null)
                    continue;
                if (message.getType() == Message.TYPE_TEXT || message.hasFileOnRemoteHost()) {
                    String date = simpleDateFormat.format(new Date(message.getTimeSent()));
                    if (bw == null) {
                        bw = new BufferedWriter(new FileWriter(
                                new File(dir, contactJid.asBareJid().toString() + ".txt")));
                    }
                    String jid = null;
                    switch (message.getStatus()) {
                        case Message.STATUS_RECEIVED:
                            jid = getMessageCounterpart(message);
                            break;
                        case Message.STATUS_SEND:
                        case Message.STATUS_SEND_RECEIVED:
                        case Message.STATUS_SEND_DISPLAYED:
                        case Message.STATUS_SEND_FAILED:
                            jid = accountJid.asBareJid().toString();
                            break;
                    }
                    if (jid != null) {
                        String body = message.hasFileOnRemoteHost() ? message.getFileParams().url.toString() : message.getBody();
                        bw.write(String.format(MESSAGE_STRING_FORMAT, date, jid, body.replace("\\\n", "\\ \n").replace("\n", "\\ \n")));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (bw != null) {
                    bw.close();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    private String getMessageCounterpart(Message message) {
        String trueCounterpart = (String) message.getContentValues().get(Message.TRUE_COUNTERPART);
        if (trueCounterpart != null) {
            return trueCounterpart;
        } else {
            return message.getCounterpart().toString();
        }
    }

    private Jid resolveAccountUuid(String accountUuid) {
        for (Account account : mAccounts) {
            if (account.getUuid().equals(accountUuid)) {
                return account.getJid();
            }
        }
        return null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static class Progress {
        private final NotificationCompat.Builder builder;
        private final int max;
        private final int count;

        private Progress(NotificationCompat.Builder builder, int max, int count) {
            this.builder = builder;
            this.max = max;
            this.count = count;
        }

        private Notification build(int percentage) {
            builder.setProgress(max * 100, count * 100 + percentage, false);
            return builder.build();
        }
    }
}