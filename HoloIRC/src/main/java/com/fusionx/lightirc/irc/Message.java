package com.fusionx.lightirc.irc;

import com.fusionx.lightirc.misc.AppPreferences;
import com.fusionx.lightirc.util.ColourParserUtils;

import android.text.Spanned;
import android.text.format.Time;

public class Message {

    public final String timestamp;

    public final Spanned message;

    public Message(final String message) {
        if (AppPreferences.timestamp) {
            final Time now = new Time();
            now.setToNow();
            this.timestamp = now.format("%H:%M");
        } else {
            this.timestamp = "";
        }
        this.message = ColourParserUtils.parseMarkup(message);
    }
}
