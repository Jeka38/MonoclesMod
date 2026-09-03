package eu.siacs.conversations.services;

import android.app.PendingIntent;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Edit;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.UiCallback;

public class LiveLocationManager {

    private final XmppConnectionService service;
    private final Handler handler;
    private final Map<String, LiveLocation> sessions = new HashMap<>();

    public LiveLocationManager(final XmppConnectionService service) {
        this.service = service;
        this.handler = new Handler(Looper.getMainLooper());
    }

    private static class LiveLocation {
        final Conversation conversation;
        final long intervalMillis;
        final long endAt;
        Message message;
        Runnable ticker;

        LiveLocation(final Conversation conversation, final long intervalMillis, final long endAt) {
            this.conversation = conversation;
            this.intervalMillis = intervalMillis;
            this.endAt = endAt;
        }
    }

    public boolean isActive(final Conversation conversation) {
        return sessions.containsKey(conversation.getUuid());
    }

    public long remainingSeconds(final Conversation conversation) {
        final LiveLocation liveLocation = sessions.get(conversation.getUuid());
        if (liveLocation == null) {
            return -1;
        }
        final long remaining = (liveLocation.endAt - System.currentTimeMillis()) / 1000L;
        return Math.max(0, remaining);
    }

    public boolean isActive() {
        return !sessions.isEmpty();
    }

    public void start(final Conversation conversation, final int durationMinutes, final int intervalSeconds) {
        stop(conversation);
        final long endAt = System.currentTimeMillis() + durationMinutes * 60_000L;
        final long intervalMillis = intervalSeconds * 1000L;
        final LiveLocation liveLocation = new LiveLocation(conversation, intervalMillis, endAt);
        sessions.put(conversation.getUuid(), liveLocation);
        service.startForcingForegroundNotification();
        final Uri geo = currentGeo();
        if (geo == null) {
            stop(conversation);
            return;
        }
        service.attachLocationToConversation(conversation, geo, null, new UiCallback<Message>() {
            @Override
            public void success(Message message) {
                liveLocation.message = message;
                scheduleTicker(liveLocation);
            }

            @Override
            public void error(int errorCode, Message object) {
                stop(conversation);
            }

            @Override
            public void userInputRequired(PendingIntent pi, Message object) {
                stop(conversation);
            }

            @Override
            public void progress(int progress) {
            }

            @Override
            public void showToast() {
            }
        });
        Log.d(Config.LOGTAG, "live location started for " + conversation.getJid() + " for " + durationMinutes + "min every " + intervalSeconds + "s");
    }

    private void scheduleTicker(final LiveLocation liveLocation) {
        if (sessions.get(liveLocation.conversation.getUuid()) != liveLocation) {
            return;
        }
        final Runnable ticker = () -> tick(liveLocation);
        liveLocation.ticker = ticker;
        handler.postDelayed(ticker, liveLocation.intervalMillis);
    }

    private void tick(final LiveLocation liveLocation) {
        if (removeIfExpired(liveLocation)) {
            return;
        }
        final Uri geo = currentGeo();
        if (geo == null) {
            scheduleTicker(liveLocation);
            return;
        }
        if (liveLocation.message != null) {
            correctMessage(liveLocation.message, geo.toString());
        }
        scheduleTicker(liveLocation);
    }

    private void correctMessage(final Message message, final String body) {
        final String previousUuid = message.getUuid();
        final long timeSent = message.getTimeSent();
        message.setBody(body);
        message.setServerMsgId(null);
        message.setUuid(java.util.UUID.randomUUID().toString());
        final List<Edit> edits = message.getEditedList();
        if (edits.size() > 1) {
            final Edit first = edits.get(0);
            edits.clear();
            edits.add(first);
        }
        message.putEdited(previousUuid, null, body, timeSent);
        service.sendMessage(message);
    }

    private boolean removeIfExpired(final LiveLocation liveLocation) {
        if (System.currentTimeMillis() >= liveLocation.endAt) {
            sessions.remove(liveLocation.conversation.getUuid());
            if (sessions.isEmpty()) {
                service.stopForcingForegroundNotification();
            }
            Log.d(Config.LOGTAG, "live location ended for " + liveLocation.conversation.getJid());
            return true;
        }
        return false;
    }

    public void stop(final Conversation conversation) {
        final LiveLocation liveLocation = sessions.remove(conversation.getUuid());
        if (liveLocation != null && liveLocation.ticker != null) {
            handler.removeCallbacks(liveLocation.ticker);
        }
        if (sessions.isEmpty()) {
            service.stopForcingForegroundNotification();
        }
        Log.d(Config.LOGTAG, "live location stopped for " + conversation.getJid());
    }

    public void stopAll() {
        for (final LiveLocation liveLocation : sessions.values()) {
            if (liveLocation.ticker != null) {
                handler.removeCallbacks(liveLocation.ticker);
            }
        }
        sessions.clear();
        service.stopForcingForegroundNotification();
        Log.d(Config.LOGTAG, "live location stopped in all conversations");
    }

    private Uri currentGeo() {
        final Location location = getCurrentLocation();
        if (location == null) {
            return null;
        }
        final float accuracy = location.getAccuracy();
        if (accuracy > 0) {
            return Uri.parse(String.format("geo:%s,%s;u=%s", location.getLatitude(), location.getLongitude(), Math.round(accuracy)));
        } else {
            return Uri.parse(String.format("geo:%s,%s", location.getLatitude(), location.getLongitude()));
        }
    }

    private Location getCurrentLocation() {
        final LocationManager locationManager = (LocationManager) service.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            return null;
        }
        Location best = null;
        for (final String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                if (!locationManager.isProviderEnabled(provider)) {
                    continue;
                }
                final Location location = locationManager.getLastKnownLocation(provider);
                if (location == null) {
                    continue;
                }
                if (best == null || location.getTime() > best.getTime()) {
                    best = location;
                }
            } catch (SecurityException e) {
                Log.d(Config.LOGTAG, "live location: no permission to access " + provider);
            }
        }
        return best;
    }
}