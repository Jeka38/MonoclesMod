package de.monocles.mod;

import android.content.Context;
import org.drinkless.tdlib.TdApi;
import java.io.File;

public class AuthorizationHandler {

    public interface AuthorizationListener {
        void onAuthorizationStateUpdated(TdApi.AuthorizationState authorizationState);
        void onReady();
        void onError(String message);
        void onWaitPhoneNumber();
        void onWaitCode();
        void onWaitPassword();
        void onWaitRegistration();
    }

    private final Context context;
    private final AuthorizationListener listener;

    public AuthorizationHandler(Context context, AuthorizationListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void handleUpdate(TdApi.Update update) {
        if (update instanceof TdApi.UpdateAuthorizationState) {
            TdApi.AuthorizationState state = ((TdApi.UpdateAuthorizationState) update).authorizationState;
            listener.onAuthorizationStateUpdated(state);
            handleState(state);
        }
    }

    private void handleState(TdApi.AuthorizationState state) {
        if (state instanceof TdApi.AuthorizationStateWaitTdlibParameters) {
            TdApi.SetTdlibParameters parameters = new TdApi.SetTdlibParameters();
            parameters.databaseDirectory = new File(context.getFilesDir(), "tdlib/db").getAbsolutePath();
            parameters.filesDirectory = new File(context.getFilesDir(), "tdlib/files").getAbsolutePath();
            parameters.useMessageDatabase = true;
            parameters.useSecretChats = false;
            parameters.apiId = 94575; // Placeholder API ID
            parameters.apiHash = "a3406de8d171abc4223c6c553b6608f0"; // Placeholder API Hash
            parameters.systemLanguageCode = "en";
            parameters.deviceModel = android.os.Build.MODEL;
            parameters.systemVersion = android.os.Build.VERSION.RELEASE;
            parameters.applicationVersion = "1.0";
            parameters.enableStorageOptimizer = true;

            TdlibManager.getInstance().send(parameters, result -> {
                if (result instanceof TdApi.Error) {
                    listener.onError(((TdApi.Error) result).message);
                }
            });
        } else if (state instanceof TdApi.AuthorizationStateWaitPhoneNumber) {
            listener.onWaitPhoneNumber();
        } else if (state instanceof TdApi.AuthorizationStateWaitCode) {
            listener.onWaitCode();
        } else if (state instanceof TdApi.AuthorizationStateWaitPassword) {
            listener.onWaitPassword();
        } else if (state instanceof TdApi.AuthorizationStateWaitRegistration) {
            listener.onWaitRegistration();
        } else if (state instanceof TdApi.AuthorizationStateReady) {
            listener.onReady();
        }
    }

    public void setPhoneNumber(String phoneNumber) {
        TdlibManager.getInstance().send(new TdApi.SetAuthenticationPhoneNumber(phoneNumber, null), result -> {
            if (result instanceof TdApi.Error) {
                listener.onError(((TdApi.Error) result).message);
            }
        });
    }

    public void setAuthenticationCode(String code) {
        TdlibManager.getInstance().send(new TdApi.CheckAuthenticationCode(code), result -> {
            if (result instanceof TdApi.Error) {
                listener.onError(((TdApi.Error) result).message);
            }
        });
    }

    public void setPassword(String password) {
        TdlibManager.getInstance().send(new TdApi.CheckAuthenticationPassword(password), result -> {
            if (result instanceof TdApi.Error) {
                listener.onError(((TdApi.Error) result).message);
            }
        });
    }

    public void registerUser(String firstName, String lastName) {
        TdlibManager.getInstance().send(new TdApi.RegisterUser(firstName, lastName), result -> {
            if (result instanceof TdApi.Error) {
                listener.onError(((TdApi.Error) result).message);
            }
        });
    }
}
