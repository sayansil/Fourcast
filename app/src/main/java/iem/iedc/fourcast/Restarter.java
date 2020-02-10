package iem.iedc.fourcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class Restarter extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("Broadcast Listened", "Service tried to stop");
        Toast.makeText(context, "Fourcast will run in background", Toast.LENGTH_SHORT).show();

        context.startForegroundService(new Intent(context, ApiService.class));
    }
}