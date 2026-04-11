package de.monocles.mod;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class TdlibManager {

    private static TdlibManager instance;
    private Client client;

    private TdlibManager() {
    }

    public static synchronized TdlibManager getInstance() {
        if (instance == null) {
            instance = new TdlibManager();
        }
        return instance;
    }

    public void init(Client.ResultHandler updateHandler, Client.LogMessageHandler logMessageHandler) {
        if (client == null) {
            Client.setLogMessageHandler(0, logMessageHandler);
            client = Client.create(updateHandler, null, null);
            client.send(new TdApi.SetLogVerbosityLevel(0), null);
        }
    }

    public void send(TdApi.Function query, Client.ResultHandler resultHandler) {
        if (client != null) {
            client.send(query, resultHandler);
        }
    }

    public void stop() {
        if (client != null) {
            client.send(new TdApi.Close(), null);
            client = null;
        }
    }
}
