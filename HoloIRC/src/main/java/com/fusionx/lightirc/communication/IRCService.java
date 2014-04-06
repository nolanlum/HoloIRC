package com.fusionx.lightirc.communication;

import com.fusionx.lightirc.misc.AppPreferences;
import com.fusionx.relay.Server;
import com.fusionx.relay.ServerConfiguration;
import com.fusionx.relay.connection.ConnectionManager;
import com.fusionx.relay.interfaces.Conversation;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Pair;

import java.util.HashMap;
import java.util.Map;

public class IRCService extends Service {

    private static final int SERVICE_ID = 1;

    private final Handler mHandler = new Handler();

    private final NewIRCBinder mBinder = new NewIRCBinder();

    private final AppPreferences mAppPreferences = new AppPreferences();

    private final Map<String, EventPriorityHelper> mEventHelperMap = new HashMap<>();

    private ConnectionManager mConnectionManager;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mConnectionManager = ConnectionManager.getConnectionManager(mAppPreferences);
        return START_STICKY;
    }

    public Server connectToServer(final ServerConfiguration.Builder builder) {
        startForeground(SERVICE_ID, getNotification());

        final Pair<Boolean, Server> pair = mConnectionManager.onConnectionRequested(builder
                .build(), mHandler);

        final boolean exists = pair.first;
        final Server server = pair.second;

        if (!exists) {
            final EventPriorityHelper eventPriorityHelper = new EventPriorityHelper(server);
            mEventHelperMap.put(server.getTitle(), eventPriorityHelper);
        }
        return server;
    }

    public Server getServerIfExists(final ServerConfiguration.Builder builder) {
        return mConnectionManager.getServerIfExists(builder.getTitle());
    }

    @Override
    public IBinder onBind(final Intent intent) {
        mConnectionManager = ConnectionManager.getConnectionManager(mAppPreferences);
        return mBinder;
    }

    public void requestDisconnectionFromServer(final Server server) {
        mEventHelperMap.remove(server.getTitle());
        final boolean finalServer = mConnectionManager.onDisconnectionRequested(server.getTitle());

        if (finalServer) {
            stopForeground(true);
        }
    }

    private Notification getNotification() {
        return null;
    }

    public EventPriorityHelper getEventHelper(String title) {
        return mEventHelperMap.get(title);
    }

    // Binder which returns this service
    public class NewIRCBinder extends Binder {

        public IRCService getService() {
            return IRCService.this;
        }
    }
}