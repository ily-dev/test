// GatewayService.java
package org.gateway.android;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;


import com.meinname.ssh.Globals;

public class GatewayService extends Service {

    private static final String TAG = "GatewayService";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "🚀 GatewayService onCreate()");
        
        // ★ ★ ★ GATEWAY SERVER STARTEN ★ ★ ★
        if (Globals.GATEWAY_ENABLED) {
            Globals.showLog(TAG, "⚠️ Py4JGateway starten !!");
            Py4JGateway.startServer();
        }else{
            Globals.showLog(TAG, "⚠️ Py4JGateway deaktiviert !!");
        }
        
        
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "📨 GatewayService onStartCommand()");
        // Service läuft weiter, bis er explizit gestoppt wird
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "🛑 GatewayService onDestroy()");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Kein Binding nötig
    }
}