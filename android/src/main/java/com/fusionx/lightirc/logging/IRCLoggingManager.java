package com.fusionx.lightirc.logging;

import com.fusionx.lightirc.model.EventDecorator;
import com.fusionx.lightirc.service.IRCService;
import com.fusionx.lightirc.util.EventUtils;

import co.fusionx.relay.base.IRCConnection;
import co.fusionx.relay.event.Event;
import co.fusionx.relay.logging.LoggingManager;
import co.fusionx.relay.logging.LoggingPreferences;

public class IRCLoggingManager extends LoggingManager {

    public IRCLoggingManager(final LoggingPreferences preferences) {
        super(preferences);
    }

    @Override
    public CharSequence getMessageFromEvent(final IRCConnection connection, final Event event) {
        EventDecorator decorator = IRCService.getEventCache(connection).get(event);
        return decorator.getMessage();
    }

    @Override
    protected boolean shouldLogEvent(final Event event) {
        return EventUtils.shouldStoreEvent(event);
    }
}