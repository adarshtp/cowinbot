package com.pixknot.cowinbot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {

    //private GlobalClass global;

    @Override
    public void onReceive(Context context, Intent intent) {
        //global = (GlobalClass) context.getApplicationContext();

        if (intent.getAction().equals(GlobalClass.ACTION_ALARM)) {
            String action = intent.getStringExtra("EXECUTE");
            Intent intentBotIntentService = new Intent(context.getApplicationContext(), BotService.class);
            intentBotIntentService.putExtra("ACTION", action);
            context.startService(intentBotIntentService);
        }
    }
}