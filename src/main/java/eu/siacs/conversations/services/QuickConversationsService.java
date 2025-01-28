package eu.siacs.conversations.services;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import android.content.Intent;
import android.os.SystemClock;
import android.net.Uri;
import android.util.Log;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.android.PhoneNumberContact;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.utils.SerialSingleThreadExecutor;
import eu.siacs.conversations.xmpp.Jid;

public class QuickConversationsService extends AbstractQuickConversationsService {

    protected final AtomicInteger mRunningSyncJobs = new AtomicInteger(0);
    protected final SerialSingleThreadExecutor mSerialSingleThreadExecutor = new SerialSingleThreadExecutor(QuickConversationsService.class.getSimpleName());
    protected HashMap<String,Attempt> mLastSyncAttempt = new HashMap<>();

    QuickConversationsService(XmppConnectionService xmppConnectionService) {
        super(xmppConnectionService);
    }

    @Override
    public void considerSync() {
        considerSync(false);
    }

    @Override
    public void signalAccountStateChange() {

    }

    @Override
    public boolean isSynchronizing() {
        return mRunningSyncJobs.get() > 0;
    }

    @Override
    public void considerSyncBackground(boolean force) {
        mRunningSyncJobs.incrementAndGet();
        mSerialSingleThreadExecutor.execute(() -> {
            considerSync(force);
            if (mRunningSyncJobs.decrementAndGet() == 0) {
                service.updateRosterUi(XmppConnectionService.UpdateRosterReason.INIT);
            }
        });
    }

    @Override
    public void handleSmsReceived(Intent intent) {
        Log.d(Config.LOGTAG,"ignoring received SMS");
    }

    protected static String getNumber(final List<String> gateways, final Contact contact) {
        final Jid jid = contact.getJid();
        if (jid.getLocal() != null && ("quicksy.im".equals(jid.getDomain()) || gateways.contains(jid.getDomain()))) {
            return jid.getLocal();
        }
        return null;
    }

    protected void refresh(Account account, final List<String> gateways, Collection<PhoneNumberContact> phoneNumberContacts) {
        for (Contact contact : account.getRoster().getWithSystemAccounts(PhoneNumberContact.class)) {
            final Uri uri = contact.getSystemAccount();
            if (uri == null) {
                continue;
            }
            final String number = getNumber(gateways, contact);
            final PhoneNumberContact phoneNumberContact = PhoneNumberContact.findByUriOrNumber(phoneNumberContacts, uri, number);
            final boolean needsCacheClean;
            if (phoneNumberContact != null) {
                if (!uri.equals(phoneNumberContact.getLookupUri())) {
                    Log.d(Config.LOGTAG, "lookupUri has changed from " + uri + " to " + phoneNumberContact.getLookupUri());
                }
                needsCacheClean = contact.setPhoneContact(phoneNumberContact);
            } else {
                needsCacheClean = contact.unsetPhoneContact(PhoneNumberContact.class);
                Log.d(Config.LOGTAG, uri.toString() + " vanished from address book");
            }
            if (needsCacheClean) {
                service.getAvatarService().clear(contact);
            }
        }
    }

    protected void considerSync(boolean forced) {
        ImmutableMap<String, PhoneNumberContact> allContacts = null;
        for (final Account account : ImmutableList.copyOf(service.getAccounts())) {
            List<String> gateways = gateways(account);
            if (gateways.size() < 1) continue;
            if (allContacts == null) allContacts = PhoneNumberContact.load(service);
            refresh(account, gateways, allContacts.values());
            if (!considerSync(account, gateways, allContacts, forced)) {
                service.syncRoster(account);
            }
        }
    }

    protected List<String> gateways(final Account account) {
        List<String> gateways = new ArrayList();
        for (final Contact contact : account.getRoster().getContacts()) {
            if (contact.showInRoster() && (contact.getPresences().anyIdentity("gateway", "pstn") || contact.getPresences().anyIdentity("gateway", "sms"))) {
                gateways.add(contact.getJid().asBareJid().toString());
            }
        }
        return gateways;
    }

    protected boolean considerSync(final Account account, final List<String> gateways, final Map<String, PhoneNumberContact> contacts, final boolean forced) {
        final int hash = Objects.hash(contacts.keySet(), gateways);
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": consider sync of " + hash);
        if (!mLastSyncAttempt.getOrDefault(account.getUuid(), Attempt.NULL).retry(hash) && !forced) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": do not attempt sync");
            return false;
        }
        mRunningSyncJobs.incrementAndGet();

        mLastSyncAttempt.put(account.getUuid(), Attempt.create(hash));
        final List<Contact> withSystemAccounts = account.getRoster().getWithSystemAccounts(PhoneNumberContact.class);
        for (Map.Entry<String, PhoneNumberContact> item : contacts.entrySet()) {
            PhoneNumberContact phoneContact = item.getValue();
            for(String gateway : gateways) {
                final Jid jid = Jid.ofLocalAndDomain(phoneContact.getPhoneNumber(), gateway);
                final Contact contact = account.getRoster().getContact(jid);
                boolean needsCacheClean = contact.setPhoneContact(phoneContact);
                needsCacheClean |= contact.setSystemTags(Collections.singleton(phoneContact.getTypeLabel()));
                if (needsCacheClean) {
                    service.getAvatarService().clear(contact);
                }
                withSystemAccounts.remove(contact);
            }
        }
        for (final Contact contact : withSystemAccounts) {
            final boolean needsCacheClean = contact.unsetPhoneContact(PhoneNumberContact.class);
            if (needsCacheClean) {
                service.getAvatarService().clear(contact);
            }
        }

        mRunningSyncJobs.decrementAndGet();
        service.syncRoster(account);
        service.updateRosterUi(XmppConnectionService.UpdateRosterReason.INIT);
        return true;
    }

    protected static class Attempt {
        private final long timestamp;
        private final int hash;

        private static final Attempt NULL = new Attempt(0, 0);

        private Attempt(long timestamp, int hash) {
            this.timestamp = timestamp;
            this.hash = hash;
        }

        public static Attempt create(int hash) {
            return new Attempt(SystemClock.elapsedRealtime(), hash);
        }

        public boolean retry(int hash) {
            return hash != this.hash || SystemClock.elapsedRealtime() - timestamp >= Config.CONTACT_SYNC_RETRY_INTERVAL;
        }
    }
}
