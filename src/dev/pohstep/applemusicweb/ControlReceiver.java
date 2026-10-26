package dev.pohstep.applemusicweb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Fallback control path: adb shell am broadcast -n dev.pohstep.applemusicweb/.ControlReceiver
 *   -a dev.pohstep.applemusicweb.CONTROL --es cmd play|pause|toggle|next|prev
 * (The running activity also registers the same action at runtime, so a plain implicit
 * broadcast works while the app is alive.)
 */
public class ControlReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        MainActivity a = MainActivity.instance;
        if (a != null) a.control(i.getStringExtra("cmd"));
    }
}
