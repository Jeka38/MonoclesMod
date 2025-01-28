package de.monocles.chat.services;

import android.os.AsyncTask;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import de.monocles.chat.Config;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.ProviderHelper;

public class ProviderService extends AsyncTask<String, Object, Boolean> {
    public static List<String> providers = new ArrayList<>();

    XmppConnectionService xmppConnectionService;
    private boolean mUseTor;
    private boolean mUseI2P;

    public ProviderService() {
    }

    public static List<String> getProviders() {
        final HashSet<String> provider = new HashSet<>(Config.DOMAIN.DOMAINS);
        if (!providers.isEmpty()) {
            provider.addAll(providers);
        }
        return new ArrayList<>(provider);
    }

    @Override
    protected Boolean doInBackground(String... params) {
        StringBuilder jsonString = new StringBuilder();
        boolean isError = false;
        mUseTor = xmppConnectionService != null && xmppConnectionService.useTorToConnect();
        mUseI2P = xmppConnectionService != null && xmppConnectionService.useI2PToConnect();

        if (!mUseTor && mUseI2P) {
            // Do not update providers if all connections tunneled to I2P (via settings checkbox) without Tor
            return true;
        }

        try {
            Log.d(Config.LOGTAG, "ProviderService: Updating provider list from " + Config.PROVIDER_URL);
            final InputStream is = HttpConnectionManager.open(Config.PROVIDER_URL, mUseTor, false);
            final BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                jsonString.append(line);
            }
            is.close();
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
            isError = true;
        }

        try {
            getProviderFromJSON(jsonString.toString());
        } catch (Exception e) {
            e.printStackTrace();
            isError = true;
        }
        if (isError) {
            Log.d(Config.LOGTAG, "ProviderService: Updating provider list failed");
        }
        return !isError;
    }

    private void getProviderFromJSON(String json) {
        List<ProviderHelper> providersList = new Gson().fromJson(json, new TypeToken<List<ProviderHelper>>() {
        }.getType());

        for (int i = 0; i < providersList.size(); i++) {
            final String provider = providersList.get(i).get_jid();
            if (provider.length() > 0) {
                if (!Config.DOMAIN.BLACKLISTED_DOMAINS.contains(provider)) {
                    Log.d(Config.LOGTAG, "ProviderService: Updating provider list. Adding " + provider);
                    providers.add(provider);
                }
            }
        }
    }
}