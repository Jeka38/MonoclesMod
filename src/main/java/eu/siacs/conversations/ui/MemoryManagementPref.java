package eu.siacs.conversations.ui;

import static eu.siacs.conversations.utils.StorageHelper.getAppMediaDirectory;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.preference.Preference;
import android.util.AttributeSet;

import java.io.File;

import eu.siacs.conversations.R;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.utils.UIHelper;

public class MemoryManagementPref extends Preference {

    String mediaUsage = "...";
    String totalMemory = "...";


    public MemoryManagementPref(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        setSummary();
    }

    public MemoryManagementPref(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setSummary();
    }

    @Override
    protected void onClick() {
        super.onClick();
        final Intent intent = new Intent(getContext(), MemoryManagementActivity.class);
        getContext().startActivity(intent);
    }

    private void setSummary() {
        new getMemoryUsages(this.getContext()).execute();
    }

    public class getMemoryUsages extends AsyncTask<Void, Void, Void> {
        private Context mContext;

        public getMemoryUsages(Context context) {
            mContext = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            setSummary(getContext().getString(R.string.media_usage) + ": " + mediaUsage + "/" + totalMemory);
        }

        @Override
        protected Void doInBackground(Void... params) {
            totalMemory = UIHelper.filesizeToString(FileBackend.getDiskSize());
            mediaUsage = UIHelper.filesizeToString(FileBackend.getDirectorySize(new File(getAppMediaDirectory(mContext, null))));
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            setSummary(getContext().getString(R.string.media_usage) + ": " + mediaUsage + "/" + totalMemory);
        }
    }
}

