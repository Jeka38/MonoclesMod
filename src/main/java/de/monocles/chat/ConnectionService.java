package de.monocles.chat;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;

import android.os.Build;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.StatusHints;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;

import android.Manifest;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.ServiceConnection;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import com.intentfilter.androidpermissions.PermissionManager;
import com.intentfilter.androidpermissions.NotificationSettings;
import com.intentfilter.androidpermissions.models.DeniedPermissions;
import io.michaelrocks.libphonenumber.android.NumberParseException;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.services.CallIntegration;
import eu.siacs.conversations.services.CallIntegrationConnectionService;
import eu.siacs.conversations.services.XmppConnectionService.XmppConnectionBinder;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.RtpSessionActivity;
import eu.siacs.conversations.utils.PhoneNumberUtilWrapper;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.jingle.JingleRtpConnection;
import eu.siacs.conversations.xmpp.jingle.Media;
import eu.siacs.conversations.xmpp.jingle.RtpEndUserState;
import static eu.siacs.conversations.services.EventReceiver.EXTRA_NEEDS_FOREGROUND_SERVICE;

@RequiresApi(Build.VERSION_CODES.M)
public class ConnectionService extends android.telecom.ConnectionService {
	public XmppConnectionService xmppConnectionService = null;
	protected ServiceConnection mConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			XmppConnectionBinder binder = (XmppConnectionBinder) service;
			xmppConnectionService = binder.getService();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			xmppConnectionService = null;
		}
	};

	@Override
	public void onCreate() {
		// From XmppActivity.connectToBackend
		Intent intent = new Intent(this, XmppConnectionService.class);
		intent.setAction(XmppConnectionService.ACTION_STARTING_CALL);
		intent.putExtra(EXTRA_NEEDS_FOREGROUND_SERVICE, true);
		try {
			startService(intent);
		} catch (IllegalStateException e) {
			Log.w("com.cheogram.android.ConnectionService", "unable to start service from " + getClass().getSimpleName());
		}
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onDestroy() {
		unbindService(mConnection);
	}

	@Override
	public Connection onCreateOutgoingConnection(
			PhoneAccountHandle phoneAccountHandle,
			ConnectionRequest request
	) {
		String[] gateway = phoneAccountHandle.getId().split("/", 2);

		String rawTel = "";
		if (request.getAddress() != null) {
			rawTel = request.getAddress().getSchemeSpecificPart();
		}
		String postDial = PhoneNumberUtils.extractPostDialPortion(rawTel);

		String tel = PhoneNumberUtils.extractNetworkPortion(rawTel);
		try {
			tel = PhoneNumberUtilWrapper.normalize(this, tel, true);
		} catch (IllegalArgumentException | NumberParseException e) {
			return Connection.createFailedConnection(
					new DisconnectCause(DisconnectCause.ERROR)
			);
		}

		if (xmppConnectionService == null) {
			return Connection.createFailedConnection(
					new DisconnectCause(DisconnectCause.ERROR)
			);
		}

		if (xmppConnectionService.getJingleConnectionManager().isBusy()) {
			return Connection.createFailedConnection(
					new DisconnectCause(DisconnectCause.BUSY)
			);
		}

		Account account = xmppConnectionService.findAccountByJid(Jid.of(gateway[0]));
		if (account == null) {
			return Connection.createFailedConnection(
					new DisconnectCause(DisconnectCause.ERROR)
			);
		}

		Jid with = Jid.ofLocalAndDomain(tel, gateway[1]);
		CheogramConnection connection = new CheogramConnection(account, with, postDial);

		PermissionManager permissionManager = PermissionManager.getInstance(this);
		permissionManager.setNotificationSettings(
				new NotificationSettings.Builder()
						.withMessage(R.string.microphone_permission_for_call)
						.withSmallIcon(R.drawable.ic_notification).build()
		);

		Set<String> permissions = new HashSet<>();
		permissions.add(Manifest.permission.RECORD_AUDIO);
		permissionManager.checkPermissions(permissions, new PermissionManager.PermissionRequestListener() {
			@Override
			public void onPermissionGranted() {
				if (connection.getState() == Connection.STATE_DISCONNECTED) return;

				connection.setSessionId(xmppConnectionService.getJingleConnectionManager().proposeJingleRtpSession(
						account,
						with,
						ImmutableSet.of(Media.AUDIO)
				).sessionId);
			}

			@Override
			public void onPermissionDenied(DeniedPermissions deniedPermissions) {
				connection.close(new DisconnectCause(DisconnectCause.ERROR));
			}
		});

		connection.setInitializing();
		connection.setAddress(
				Uri.fromParts("tel", tel, null), // Normalized tel as tel: URI
				TelecomManager.PRESENTATION_ALLOWED
		);

		xmppConnectionService.setOnRtpConnectionUpdateListener(
				(XmppConnectionService.OnJingleRtpConnectionUpdate) connection
		);

		xmppConnectionService.setDiallerIntegrationActive(true);
		return connection;
	}

	@Override
	public Connection onCreateIncomingConnection(PhoneAccountHandle handle, ConnectionRequest request) {
		Bundle extras = request.getExtras();
		String accountJid = extras.getString("account");
		String withJid = extras.getString("with");
		String sessionId = extras.getString("sessionId");

		if (xmppConnectionService == null) {
			return Connection.createFailedConnection(
					new DisconnectCause(DisconnectCause.ERROR)
			);
		}

		Account account = xmppConnectionService.findAccountByJid(Jid.of(accountJid));
		Jid with = Jid.of(withJid);

		CheogramConnection connection = new CheogramConnection(account, with, null);
		connection.setSessionId(sessionId);
		connection.setAddress(
				Uri.fromParts("tel", with.getLocal(), null),
				TelecomManager.PRESENTATION_ALLOWED
		);
		connection.setCallerDisplayName(
				account.getRoster().getContact(with).getDisplayName(),
				TelecomManager.PRESENTATION_ALLOWED
		);
		connection.setRinging();

		xmppConnectionService.setOnRtpConnectionUpdateListener(connection);

		return connection;
	}

	public class CheogramConnection extends Connection implements XmppConnectionService.OnJingleRtpConnectionUpdate {
		protected Account account;
		protected Jid with;
		protected String sessionId = null;
		protected Stack<String> postDial = new Stack<>();
		protected Icon gatewayIcon;
		protected CallAudioState pendingState = null;
		protected WeakReference<JingleRtpConnection> rtpConnection = null;

		CheogramConnection(Account account, Jid with, String postDialString) {
			super();
			this.account = account;
			this.with = with;

			gatewayIcon = Icon.createWithBitmap(FileBackend.drawDrawable(xmppConnectionService.getAvatarService().get(
					account.getRoster().getContact(Jid.of(with.getDomain())),
					AvatarService.getSystemUiAvatarSize(xmppConnectionService),
					false
			)));

			if (postDialString != null) {
				for (int i = postDialString.length() - 1; i >= 0; i--) {
					postDial.push("" + postDialString.charAt(i));
				}
			}

			setCallerDisplayName(
					account.getDisplayName(),
					TelecomManager.PRESENTATION_ALLOWED
			);
			setAudioModeIsVoip(true);
			setConnectionCapabilities(
					Connection.CAPABILITY_CAN_SEND_RESPONSE_VIA_CONNECTION |
							Connection.CAPABILITY_MUTE
			);
			setRingbackRequested(true);
		}

		public void setSessionId(final String sessionId) {
			this.sessionId = sessionId;
		}

		@Override
		public void onJingleRtpConnectionUpdate(final Account account, final Jid with, final String sessionId, final RtpEndUserState state) {
			Log.d("com.cheogram.android.CheogramConnection", "onJingleRtpConnectionUpdate: " + with + " " + sessionId + " (== " + this.sessionId + " )? " + state);
			if (sessionId == null || !sessionId.equals(this.sessionId)) return;
			if (rtpConnection == null) {
				this.with = with; // Store full JID of connection
				findRtpConnection();
			}

			String statusLabel = null;

			if (state == RtpEndUserState.FINDING_DEVICE) {
				setInitialized();
			} else if (state == RtpEndUserState.RINGING) {
				setDialing();
			} else if (state == RtpEndUserState.INCOMING_CALL) {
				setRinging();
			} else if (state == RtpEndUserState.CONNECTING) {
				xmppConnectionService.setDiallerIntegrationActive(true);
				setActive();
				statusLabel = getString(R.string.rtp_state_connecting);
			} else if (state == RtpEndUserState.CONNECTED) {
				xmppConnectionService.setDiallerIntegrationActive(true);
				setActive();
				postDial();
			} else if (state == RtpEndUserState.DECLINED_OR_BUSY) {
				close(new DisconnectCause(DisconnectCause.BUSY));
			} else if (state == RtpEndUserState.ENDED) {
				close(new DisconnectCause(DisconnectCause.LOCAL));
			} else if (state == RtpEndUserState.RETRACTED) {
				close(new DisconnectCause(DisconnectCause.CANCELED));
			} else if (RtpSessionActivity.END_CARD.contains(state)) {
				close(new DisconnectCause(DisconnectCause.ERROR));
			}

			setStatusHints(new StatusHints(statusLabel, gatewayIcon, null));
		}

		@Override
		public void onAudioDeviceChanged(CallIntegration.AudioDevice selectedAudioDevice, Set<CallIntegration.AudioDevice> availableAudioDevices) {
			if (Build.VERSION.SDK_INT < 26) return;

			if (pendingState != null) {
				Log.d("com.cheogram.android.CheogramConnection", "Try with pendingState: " + pendingState);
				onCallAudioStateChanged(pendingState);
				return;
			}

			Log.d("com.cheogram.android.CheogramConnection", "onAudioDeviceChanged: " + selectedAudioDevice);

			switch(selectedAudioDevice) {
				case SPEAKER_PHONE:
					setAudioRoute(CallAudioState.ROUTE_SPEAKER);
					break;
				case WIRED_HEADSET:
					setAudioRoute(CallAudioState.ROUTE_WIRED_HEADSET);
					break;
				case EARPIECE:
					setAudioRoute(CallAudioState.ROUTE_EARPIECE);
					break;
				case BLUETOOTH:
					setAudioRoute(CallAudioState.ROUTE_BLUETOOTH);
					break;
				default:
					setAudioRoute(CallAudioState.ROUTE_WIRED_OR_EARPIECE);
			}
		}

		@Override
		public void onCallAudioStateChanged(CallAudioState state) {
			pendingState = null;
			if (rtpConnection == null || rtpConnection.get() == null) {
				pendingState = state;
				return;
			}

			Log.d("com.cheogram.android.CheogramConnection", "onCallAudioStateChanged: " + state);
			rtpConnection.get().callIntegration.onCallAudioStateChanged(state);

			try {
				rtpConnection.get().setMicrophoneEnabled(!state.isMuted());
			} catch (final IllegalStateException e) {
				pendingState = state;
				Log.w("com.cheogram.android.CheogramConnection", "Could not set microphone mute to " + (state.isMuted() ? "true" : "false") + ": " + e.toString());
			}
		}

		@Override
		public void onAnswer() {
			// For incoming calls, a connection update may not have been triggered before answering
			// so we have to acquire the rtp connection object here
			findRtpConnection();
			if (rtpConnection == null || rtpConnection.get() == null) {
				close(new DisconnectCause(DisconnectCause.CANCELED));
			} else {
				rtpConnection.get().acceptCall();
			}
		}

		@Override
		public void onReject() {
			findRtpConnection();
			if (rtpConnection != null && rtpConnection.get() != null) {
				try {
					rtpConnection.get().rejectCall();
				} catch (final IllegalStateException e) {
					Log.w("com.cheogram.android.CheogramConnection", e.toString());
				}
			}
			close(new DisconnectCause(DisconnectCause.LOCAL));
		}

		// Set the connection to the disconnected state and clean up the resources
		// Note that we cannot do this from onStateChanged() because calling destroy
		// there seems to trigger a deadlock somewhere in the telephony stack.
		public void close(DisconnectCause reason) {
			setDisconnected(reason);
			destroy();
			xmppConnectionService.setDiallerIntegrationActive(false);
			xmppConnectionService.removeRtpConnectionUpdateListener(this);
		}

		@Override
		public void onDisconnect() {
			if (rtpConnection == null || rtpConnection.get() == null) {
				xmppConnectionService.getJingleConnectionManager().retractSessionProposal(account, with.asBareJid());
				close(new DisconnectCause(DisconnectCause.LOCAL));
			} else {
				rtpConnection.get().endCall();
			}
		}

		@Override
		public void onAbort() {
			onDisconnect();
		}

		@Override
		public void onPlayDtmfTone(char c) {
			if (rtpConnection == null || rtpConnection.get() == null) {
				postDial.push("" + c);
				return;
			}

			rtpConnection.get().applyDtmfTone("" + c);
		}

		@Override
		public void onPostDialContinue(boolean c) {
			if (c) postDial();
		}

		protected void findRtpConnection() {
			if (rtpConnection != null) return;

			rtpConnection = xmppConnectionService.getJingleConnectionManager().findJingleRtpConnection(account, with, sessionId);
		}

		protected void sleep(int ms) {
			try {
				Thread.sleep(ms);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}

		protected void postDial() {
			while (!postDial.empty()) {
				String next = postDial.pop();
				if (next.equals(";")) {
					Vector<String> v = new Vector<>(postDial);
					Collections.reverse(v);
					setPostDialWait(Joiner.on("").join(v));
					return;
				} else if (next.equals(",")) {
					sleep(2000);
				} else {
					rtpConnection.get().applyDtmfTone(next);
					sleep(100);
				}
			}
		}
	}
}
