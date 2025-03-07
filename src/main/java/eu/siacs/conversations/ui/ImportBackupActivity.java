package eu.siacs.conversations.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import eu.siacs.conversations.utils.Compatibility;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.snackbar.Snackbar;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityImportBackupBinding;
import eu.siacs.conversations.databinding.DialogEnterPasswordBinding;
import eu.siacs.conversations.services.ImportBackupService;
import eu.siacs.conversations.ui.adapter.BackupFileAdapter;
import eu.siacs.conversations.utils.ThemeHelper;
import eu.siacs.conversations.utils.BackupFileHeader;


public class ImportBackupActivity extends XmppActivity implements ServiceConnection, ImportBackupService.OnBackupFilesLoaded, BackupFileAdapter.OnItemClickedListener, ImportBackupService.OnBackupProcessed {

    private ActivityImportBackupBinding binding;

    private BackupFileAdapter backupFileAdapter;
    private ImportBackupService service;
    private boolean mLoadingState = false;


    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_import_backup);
        setSupportActionBar((Toolbar) binding.toolbar.getRoot());
        setLoadingState(savedInstanceState != null && savedInstanceState.getBoolean("loading_state", false));
        this.backupFileAdapter = new BackupFileAdapter();
        this.binding.list.setAdapter(this.backupFileAdapter);
        this.backupFileAdapter.setOnItemClickedListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.import_backup, menu);
        final MenuItem openBackup = menu.findItem(R.id.action_open_backup_file);
        openBackup.setVisible(!this.mLoadingState);
        return true;
    }

    @Override
    protected void refreshUiReal() {
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        bundle.putBoolean("loading_state", this.mLoadingState);
        super.onSaveInstanceState(bundle);
    }

    @Override
    public void onStart() {
        super.onStart();
        final int theme = ThemeHelper.find(this);
        if (this.mTheme != theme) {
            recreate();
        } else {
            bindService(new Intent(this, ImportBackupService.class), this, Context.BIND_AUTO_CREATE);
        }
        final Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && !this.mLoadingState) {
            Uri uri = intent.getData();
            if (uri != null) {
                openBackupFileFromUri(uri, true);
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (this.service != null) {
            this.service.removeOnBackupProcessedListener(this);
        }
        unbindService(this);
    }

    @Override
    protected void onBackendConnected() {
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        ImportBackupService.ImportBackupServiceBinder binder = (ImportBackupService.ImportBackupServiceBinder) service;
        this.service = binder.getService();
        this.service.addOnBackupProcessedListener(this);
        setLoadingState(this.service.getLoadingState());
        this.service.loadBackupFiles(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        this.service = null;
    }

    @Override
    public void onBackupFilesLoaded(final List<ImportBackupService.BackupFile> files) {
        runOnUiThread(() -> {
            if (files.size() >= 1) {
                this.binding.hint.setVisibility(View.GONE);
                this.binding.list.setVisibility(View.VISIBLE);
                backupFileAdapter.setFiles(files);
            } else {
                this.binding.hint.setVisibility(View.VISIBLE);
                if (Compatibility.runsThirty()) {
                    this.binding.hint.setText(getString(R.string.import_backup_description));
                } else {
                    this.binding.hint.setText(getString(R.string.no_backup_available));
                }
                this.binding.list.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onClick(final ImportBackupService.BackupFile backupFile) {
        showEnterPasswordDialog(backupFile, false);
    }


    /***
     * Restore settings from a backup file
     * @param uri
     */
    @SuppressWarnings (value="unchecked")
    private void restoreSettingsFromFile(Uri uri) {
        try {
            SharedPreferences current_prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            SharedPreferences.Editor editor = current_prefs.edit();
            InputStream inputStream = getContentResolver().openInputStream(uri);
            ObjectInputStream ois = new ObjectInputStream(inputStream);
            HashMap<String, ?> backup_prefs = (HashMap<String, ?>) ois.readObject();

            for (Map.Entry<String, ?> entry : backup_prefs.entrySet()) {
                String k = entry.getKey();
                Object v = entry.getValue();

                if(v instanceof Boolean) {
                    editor.putBoolean(k, (Boolean) v);
                } else if(v instanceof Float) {
                    editor.putFloat(k, (Float) v);
                } else if(v instanceof Integer) {
                    editor.putInt(k, (Integer) v);
                } else if(v instanceof Long) {
                    editor.putLong(k, (Long) v);
                } else if (v instanceof String) {
                    editor.putString(k, v.toString());
                } else {
                    editor.putStringSet(k, (Set<String>)v);
                }
            }

            editor.apply(); // this may fail...
            ois.close();
            inputStream.close();
            showSnackbarAndFinishActivity(R.string.settings_restore_message_success, 2000, null);
        } catch (IOException iox) {
            Log.d(Config.LOGTAG, "Failed to open settings backup file: " + iox.getMessage());
            showSnackbarAndFinishActivity(R.string.settings_restore_message_failure, 5000, iox.getMessage());
        } catch(ClassNotFoundException cnfx) {
            Log.d(Config.LOGTAG, "Failed to parse settings backup file: " + cnfx.getMessage());
            showSnackbarAndFinishActivity(R.string.settings_restore_message_failure, 5000, cnfx.getMessage());
        }
    }

    /***
     * Show a Snackbar and finish the current activity (returning to the previous) after the given timeout
     * @param resourceID
     * @param timeoutMillis
     * @param msg Additional error message
     */
    private void showSnackbarAndFinishActivity(int resourceID, int timeoutMillis, String msg) {
        Resources res = getResources();
        Snackbar sb = null;

        if(msg == null) {
            sb = Snackbar.make(binding.coordinator, res.getString(resourceID), Snackbar.LENGTH_LONG);
        } else {
            sb = Snackbar.make(binding.coordinator, String.format(res.getString(resourceID), msg), Snackbar.LENGTH_LONG);
        }

        sb.setDuration(timeoutMillis);
        sb.addCallback(new Snackbar.Callback() {
            public void onDismissed(Snackbar snackbar, int event) {
                if (event == Snackbar.Callback.DISMISS_EVENT_TIMEOUT) {
                    finish();
                }
            }
        });
        sb.show();
    }


    /***
     * TODO: make this function distinguish between a .ceb file and a settings file
     * @param uri
     * @param finishOnCancel
     */

    private void openBackupFileFromUri(final Uri uri, final boolean finishOnCancel) {
        new Thread(() -> {
            try {
                // check if setting
                if(isUserData(uri)) {
                    final ImportBackupService.BackupFile backupFile = ImportBackupService.BackupFile.read(this, uri);
                    runOnUiThread(() -> showEnterPasswordDialog(backupFile, finishOnCancel));
                } else {
                    restoreSettingsFromFile(uri);
                }
            } catch (final BackupFileHeader.OutdatedBackupFileVersion e) {
                Snackbar.make(binding.coordinator, R.string.outdated_backup_file_format, Snackbar.LENGTH_LONG).show();
            } catch (final IOException | IllegalArgumentException e) {
                Log.d(Config.LOGTAG, "unable to open backup file " + uri, e);
                runOnUiThread(() -> Snackbar.make(binding.coordinator, R.string.not_a_backup_file, Snackbar.LENGTH_LONG).show());
            }
        }).start();
    }

    private boolean isUserData(Uri uri) {
        try {
            DataInputStream inputStream = new DataInputStream(getContentResolver().openInputStream(uri));
            BackupFileHeader header = BackupFileHeader.read(inputStream);
            if (header.getJid() == null) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void showEnterPasswordDialog(final ImportBackupService.BackupFile backupFile, final boolean finishOnCancel) {
        final DialogEnterPasswordBinding enterPasswordBinding = DataBindingUtil.inflate(LayoutInflater.from(this), R.layout.dialog_enter_password, null, false);
        Log.d(Config.LOGTAG, "attempting to import " + backupFile.getUri());
        enterPasswordBinding.explain.setText(getString(R.string.enter_password_to_restore, backupFile.getHeader().getJid().toString()));
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(enterPasswordBinding.getRoot());
        builder.setTitle(R.string.enter_password);
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> {
            if (finishOnCancel) {
                finish();
            }
        });
        builder.setPositiveButton(R.string.restore, null);
        builder.setCancelable(false);
        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener((d) -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                final String password = enterPasswordBinding.accountPassword.getEditableText().toString();
                if (password.isEmpty()) {
                    enterPasswordBinding.accountPasswordLayout.setError(getString(R.string.please_enter_password));
                    return;
                }
                final Uri uri = backupFile.getUri();
                Intent intent = new Intent(this, ImportBackupService.class);
                intent.setAction(Intent.ACTION_SEND);
                intent.putExtra("password", password);
                if ("file".equals(uri.getScheme())) {
                    intent.putExtra("file", uri.getPath());
                } else {
                    intent.setData(uri);
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                setLoadingState(true);
                ContextCompat.startForegroundService(this, intent);
                d.dismiss();
            });
        });
        dialog.show();
    }

    private void setLoadingState(final boolean loadingState) {
        binding.coordinator.setVisibility(loadingState ? View.GONE : View.VISIBLE);
        binding.inProgress.setVisibility(loadingState ? View.VISIBLE : View.GONE);
        setTitle(loadingState ? R.string.restoring_backup : R.string.restore_backup);
        configureActionBar(getSupportActionBar(), !loadingState);
        this.mLoadingState = loadingState;
        invalidateOptionsMenu();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (resultCode == RESULT_OK) {
            if (requestCode == 0xbac) {
                openBackupFileFromUri(intent.getData(), false);
            }
        }
    }

    @Override
    public void onAccountAlreadySetup() {
        runOnUiThread(() -> {
            setLoadingState(false);
            Snackbar.make(binding.coordinator, R.string.account_already_setup, Snackbar.LENGTH_LONG).show();
        });
    }

    @Override
    public void onBackupRestored() {
        runOnUiThread(this::restart);
    }

    private void restart() {
        Log.d(Config.LOGTAG, "Restarting " + getBaseContext().getPackageManager().getLaunchIntentForPackage(getBaseContext().getPackageName()));
        Intent intent = getBaseContext().getPackageManager().getLaunchIntentForPackage(getBaseContext().getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
        System.exit(0);
    }

    @Override
    public void onBackupDecryptionFailed() {
        runOnUiThread(() -> {
            setLoadingState(false);
            Snackbar.make(binding.coordinator, R.string.unable_to_decrypt_backup, Snackbar.LENGTH_LONG).show();
        });
    }

    @Override
    public void onBackupRestoreFailed() {
        runOnUiThread(() -> {
            setLoadingState(false);
            Snackbar.make(binding.coordinator, R.string.unable_to_restore_backup, Snackbar.LENGTH_LONG).show();
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_open_backup_file) {
            openBackupFile();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openBackupFile() {
        Intent intent;
        if (Compatibility.runsThirty()) {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        } else {
            intent = new Intent(Intent.ACTION_GET_CONTENT);
        }
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.open_backup)), 0xbac);
    }
}