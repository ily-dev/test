// Py4JGateway.java - NEUE METHODE checkAllActivities()
package org.gateway.android;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.meinname.ssh.Globals;

import py4j.GatewayServer;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.Socket;

public class Py4JGateway {

    private static final String TAG = "Py4JGateway";
    
    // ★ ★ ★ GATEWAY STATUS ★ ★ ★
    private static boolean isGatewayRunning = false;
    private static boolean isGatewayInitialized = false;
    private static long gatewayStartTime = 0;
    private static long lastActivityTime = 0;
    private static int connectionCount = 0;
    private static String lastError = "Kein Fehler";
    private static String lastAction = "Keine Aktion";
    
    // ★ ★ ★ GATEWAY-SERVER INSTANZ ★ ★ ★
    private static GatewayServer gatewayServer = null;
    
    // ★ ★ ★ ACTIVITY-REFERENZ ★ ★ ★
    private static WeakReference<Activity> mActivityRef = null;
    private static String currentActivityName = "None";

    // ─── ALLE ACTIVITY-CLASS NAMEN ★ ★ ★
    private static final String[] ACTIVITY_CLASSES = {
        "org.kivy.android.PythonActivity",
        "org.kivy.android.PythonQActivity",
        "com.meinname.loginapp3.MyCustomActivity",
        "com.rk.terminal.ui.activities.terminal.MainActivity"
    };
    
    private static final String[] ACTIVITY_NAMES = {
        "1. PythonActivity (SDL2/Kivy)",
        "2. PythonQActivity (Qt/PySide6)",
        "3. MyCustomActivity (Login/Start)",
        "4. ReTerminal (Shell)"
    };

    // ─── ★ ★ ★ DEBUG: PORT PRÜFEN ★ ★ ★ ──────────────────────
    public static boolean isPortOpen() {
        try {
            Socket socket = new Socket("127.0.0.1", Globals.GATEWAY_PORT);
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    // ─── ★ ★ ★ DEBUG: LETZTE AKTIVITÄT ★ ★ ★ ──────────────
    public static String getLastActivity() {
        if (lastActivityTime == 0) {
            return "Keine Aktivität";
        }
        long diff = (System.currentTimeMillis() - lastActivityTime) / 1000;
        return "Letzte Aktivität: " + lastAction + " vor " + diff + "s";
    }
    
    // ─── ★ ★ ★ DEBUG: FEHLER-LOG ★ ★ ★ ──────────────────────
    public static void logError(String error) {
        lastError = error;
        Globals.showLog(TAG, "❌ ERROR: " + error);
        Log.e(TAG, "❌ ERROR: " + error);
        
        // ★ ★ ★ STACKTRACE AUFNEHMEN ★ ★ ★
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (int i = 0; i < Math.min(5, stackTrace.length); i++) {
            Globals.showLog(TAG, "  at " + stackTrace[i].toString());
        }
    }
    
    // ─── ★ ★ ★ DEBUG: AKTIVITÄT LOGGEN ★ ★ ★ ──────────────
    public static void logAction(String action) {
        lastAction = action;
        lastActivityTime = System.currentTimeMillis();
        connectionCount++;
        Globals.showLog(TAG, "🔍 ACTION: " + action + " (#" + connectionCount + ")");
    }

    // ─── ★ ★ ★ NEU: ALLE 4 ACTIVITIES PRÜFEN ★ ★ ★ ──────────
    public String checkAllActivities() {
        Globals.showLog(TAG, "🔍 checkAllActivities() aufgerufen");
        logAction("checkAllActivities");
        
        StringBuilder result = new StringBuilder();
        result.append("📊 ACTIVITY-STATUS:\n");
        result.append("========================================\n");
        
        // 1. Gateway-Status
        result.append("🔌 Gateway: ");
        if (isGatewayRunning) {
            result.append("✅ LÄUFT (Port " + Globals.GATEWAY_PORT + ")\n");
            result.append("   Port offen: " + (isPortOpen() ? "✅ JA" : "❌ NEIN") + "\n");
        } else {
            result.append("❌ NICHT GESTARTET!\n");
        }
        
        result.append("   Letzte Aktion: " + lastAction + "\n");
        result.append("   Verbindungen: " + connectionCount + "\n");
        if (lastError != null && !lastError.equals("Kein Fehler")) {
            result.append("   Letzter Fehler: " + lastError + "\n");
        }
        
        // 2. Gespeicherte Activity
        result.append("\n📌 Gespeicherte Activity: ");
        if (mActivityRef != null) {
            Activity act = mActivityRef.get();
            if (act != null && !act.isFinishing() && !act.isDestroyed()) {
                result.append("✅ " + act.getClass().getSimpleName() + " (gültig)\n");
            } else {
                result.append("⚠️ " + currentActivityName + " (ungültig oder zerstört)\n");
            }
        } else {
            result.append("❌ KEINE\n");
        }
        
        // 3. Alle 4 Activities prüfen
        result.append("\n📋 Alle 4 Activities:\n");
        result.append("----------------------------------------\n");
        
        int foundCount = 0;
        int validCount = 0;
        String activeActivity = "Keine";
        
        for (int i = 0; i < ACTIVITY_CLASSES.length; i++) {
            String className = ACTIVITY_CLASSES[i];
            String displayName = ACTIVITY_NAMES[i];
            
            result.append(displayName + ": ");
            
            try {
                Class<?> clazz = Class.forName(className);
                foundCount++;
                
                // Versuche getInstance()
                try {
                    Method getInstance = clazz.getMethod("getInstance");
                    Object instance = getInstance.invoke(null);
                    
                    if (instance != null && instance instanceof Activity) {
                        Activity activity = (Activity) instance;
                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                            result.append("✅ AKTIV (gültig)\n");
                            validCount++;
                            activeActivity = activity.getClass().getSimpleName();
                        } else {
                            result.append("⚠️ INSTANZ EXISTIERT, aber Activity beendet/zerstört\n");
                        }
                    } else {
                        result.append("⚠️ INSTANZ EXISTIERT, aber null oder keine Activity\n");
                    }
                } catch (NoSuchMethodException e) {
                    result.append("⚠️ KEINE getInstance() Methode\n");
                } catch (Exception e) {
                    result.append("⚠️ Fehler beim Aufruf: " + e.getMessage() + "\n");
                }
                
            } catch (ClassNotFoundException e) {
                result.append("❌ KLASSE NICHT GEFUNDEN (nicht geladen)\n");
            } catch (Exception e) {
                result.append("❌ Fehler: " + e.getMessage() + "\n");
            }
        }
        
        // 4. Zusammenfassung
        result.append("\n========================================\n");
        result.append("📊 ZUSAMMENFASSUNG:\n");
        result.append("   Gefundene Klassen: " + foundCount + "/4\n");
        result.append("   Gültige Activities: " + validCount + "/4\n");
        result.append("   Aktive Activity: " + activeActivity + "\n");
        result.append("   " + getLastActivity() + "\n");
        
        if (validCount > 0) {
            result.append("   ✅ Mindestens eine Activity ist aktiv!\n");
        } else {
            result.append("   ❌ KEINE Activity aktiv! Bitte starte eine Activity.\n");
        }
        
        // 5. Gateway-Status
        result.append("\n🔍 Gateway-Status: " + getGatewayStatus());
        
        Globals.showLog(TAG, result.toString());
        return result.toString();
    }

    // ─── SELBST-PRÜFUNG ──────────────────────────────────────
    public static boolean isGatewayActive() {
        if (!isGatewayRunning) {
            logError("Gateway läuft nicht!");
            return false;
        }
        if (mActivityRef == null) {
            logError("Keine Activity-Referenz!");
            return false;
        }
        Activity activity = mActivityRef.get();
        if (activity == null) {
            logError("Activity wurde eingesammelt (GC)!");
            return false;
        }
        if (activity.isFinishing() || activity.isDestroyed()) {
            logError("Activity ist beendet oder zerstört: " + activity.getClass().getSimpleName());
            return false;
        }
        return true;
    }
    
    // ─── GATEWAY STATUS ──────────────────────────────────────
    public static String getGatewayStatus() {
        if (!isGatewayRunning) {
            return "❌ Gateway NICHT gestartet (Fehler: " + lastError + ")";
        }
        if (mActivityRef == null || mActivityRef.get() == null) {
            return "⚠️ Gateway läuft, aber KEINE Activity-Referenz!";
        }
        Activity activity = mActivityRef.get();
        if (activity.isFinishing() || activity.isDestroyed()) {
            return "⚠️ Gateway läuft, aber Activity ist zerstört!";
        }
        return "✅ Gateway AKTIV (Activity: " + activity.getClass().getSimpleName() + ", Uptime: " + getUptime() + "s, Port: " + (isPortOpen() ? "✅ offen" : "❌ geschlossen") + ")";
    }
    
    // ─── UPTIME ──────────────────────────────────────────────
    public static long getUptime() {
        if (gatewayStartTime == 0) return 0;
        return (System.currentTimeMillis() - gatewayStartTime) / 1000;
    }

    // ─── AKTUELLE ACTIVITY HOLEN ──────────────────────────────
    private static Activity getValidActivity() {
        logAction("getValidActivity");
        
        if (!isGatewayRunning) {
            logError("Gateway läuft nicht!");
            return null;
        }
        
        if (mActivityRef != null) {
            Activity activity = mActivityRef.get();
            if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                return activity;
            } else {
                mActivityRef = null;
                logError("Gespeicherte Activity ungültig!");
            }
        }

        for (String className : ACTIVITY_CLASSES) {
            try {
                Class<?> clazz = Class.forName(className);
                try {
                    Method getInstance = clazz.getMethod("getInstance");
                    Object instance = getInstance.invoke(null);
                    
                    if (instance != null && instance instanceof Activity) {
                        Activity activity = (Activity) instance;
                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                            mActivityRef = new WeakReference<>(activity);
                            currentActivityName = activity.getClass().getSimpleName();
                            logAction("Activity gefunden: " + currentActivityName);
                            return activity;
                        }
                    }
                } catch (Exception e) {
                    // Ignorieren
                }
            } catch (Exception e) {
                // Ignorieren
            }
        }

        logError("Keine gültige Activity gefunden!");
        return null;
    }

    // ─── JAVA-METHODEN (VON PYTHON AUFRUFBAR) ──────────────────
    public String holeAndroidStatus() {
        logAction("holeAndroidStatus");
        return "✅ " + getGatewayStatus();
    }

    public String verarbeiteText(String eingabe) {
        logAction("verarbeiteText: " + (eingabe != null ? eingabe.substring(0, Math.min(20, eingabe.length())) : "null"));
        Globals.showLog(TAG, "📩 Text von Python empfangen: " + eingabe);
        
        if (eingabe == null || eingabe.trim().isEmpty()) {
            return "⚠️ Java hat einen leeren Text erhalten!";
        }
        
        return "✅ Java meldet: Text erfolgreich verarbeitet (Länge: " + eingabe.length() + " Zeichen).";
    }

    public String startPythonActivity() {
        logAction("startPythonActivity");
        return startActivity("org.kivy.android.PythonActivity", "main.pyc");
    }

    public String startPythonQActivity() {
        logAction("startPythonQActivity");
        return startActivity("org.kivy.android.PythonQActivity", "main2.pyc");
    }
    
    public String startTerminal() {
        logAction("startTerminal");
        return startActivity("com.rk.terminal.ui.activities.terminal.MainActivity", "");
    }

    private String startActivity(String className, String entrypoint) {
        logAction("startActivity: " + className);
        
        if (!isGatewayRunning) {
            String error = "❌ Gateway läuft NICHT! startServer() wurde nicht aufgerufen.";
            logError(error);
            return error;
        }
        
        Activity activity = getValidActivity();
        
        if (activity == null) {
            String error = "❌ Keine Activity-Referenz! Bitte rufe setActivity() auf.";
            logError(error);
            return error;
        }
        
        try {
            Class<?> activityClass = Class.forName(className);
            Intent intent = new Intent(activity, activityClass);
            
            if (entrypoint != null && !entrypoint.isEmpty()) {
                intent.putExtra("ANDROID_ENTRYPOINT", entrypoint);
                intent.putExtra("PYTHON_ENTRYPOINT", entrypoint);
                intent.putExtra("entrypoint", entrypoint);
            }
            
            activity.startActivity(intent);
            logAction("Activity gestartet: " + className);
            return "✅ " + className + " gestartet";
            
        } catch (ClassNotFoundException e) {
            String error = "❌ Klasse nicht gefunden: " + className;
            logError(error);
            return error;
        } catch (Exception e) {
            String error = "❌ Fehler: " + e.getMessage();
            logError(error);
            return error;
        }
    }

    // ─── ACTIVITY-REFERENZ SETZEN ──────────────────────────────
    public static void setActivity(Activity activity) {
        logAction("setActivity: " + (activity != null ? activity.getClass().getSimpleName() : "null"));
        
        if (activity != null) {
            mActivityRef = new WeakReference<>(activity);
            currentActivityName = activity.getClass().getSimpleName();
            Globals.showLog(TAG, "✅ Activity-Referenz GESETZT: " + currentActivityName);
        } else {
            mActivityRef = null;
            currentActivityName = "None";
            logError("Activity-Referenz auf null gesetzt!");
        }
    }

    // ─── ★ ★ ★ WATCHDOG: PORT ÜBERWACHEN ★ ★ ★ ──────────────
    public static void startWatchdog() {
        new Thread(() -> {
            while (isGatewayRunning) {
                try {
                    Thread.sleep(5000); // Alle 5 Sekunden prüfen
                    
                    if (!isPortOpen()) {
                        logError("🚨 PORT GESCHLOSSEN! Gateway läuft aber Port ist zu!");
                        logError("Letzte Aktion: " + lastAction);
                        logError("Letzte Activity: " + currentActivityName);
                        
                        // Versuche neu zu starten
                        Globals.showLog(TAG, "🔄 Versuche Gateway neu zu starten...");
                        restartServer();
                    }
                } catch (Exception e) {
                    // Ignorieren
                }
            }
        }).start();
    }
    
    // ─── ★ ★ ★ GATEWAY NEU STARTEN ★ ★ ★ ────────────────────
    public static void restartServer() {
        logAction("restartServer");
        Globals.showLog(TAG, "🔄 Gateway-Neustart wird durchgeführt...");
        
        // Alten Server stoppen
        if (gatewayServer != null) {
            try {
                gatewayServer.shutdown();
                Globals.showLog(TAG, "✅ Alter Gateway-Server gestoppt");
            } catch (Exception e) {
                Globals.showLog(TAG, "⚠️ Fehler beim Stoppen: " + e.getMessage());
            }
        }
        
        isGatewayRunning = false;
        
        // Neu starten
        startServer();
    }

    // ─── SERVER STARTEN ──────────────────────────────────────
    public static void startServer() {
        Globals.showLog(TAG, "🚀 Gateway-Server wird gestartet...");
        logAction("startServer");
        
        isGatewayInitialized = true;
        gatewayStartTime = System.currentTimeMillis();
        lastError = "Kein Fehler";
        
        new Thread(() -> {
            try {
                Py4JGateway gatewayInstance = new Py4JGateway();
                gatewayServer = new GatewayServer(gatewayInstance, Globals.GATEWAY_PORT);
                gatewayServer.start();
                
                isGatewayRunning = true;
                
                Globals.showLog(TAG, "✅ Gateway Server auf Port " + Globals.GATEWAY_PORT + " gestartet!");
                Log.d(TAG, "✅ Gateway Server auf Port " + Globals.GATEWAY_PORT + " gestartet!");
                
                // ★ ★ ★ WATCHDOG STARTEN ★ ★ ★
                startWatchdog();
                
            } catch (Exception e) {
                isGatewayRunning = false;
                String error = "❌ Fehler beim Starten des Gateway Servers: " + e.getMessage();
                logError(error);
                Log.e(TAG, error, e);
            }
        }).start();
    }
    
    // ─── ★ ★ ★ SERVER STOPPEN (NUR BEIM APP-EXIT) ★ ★ ★ ──
    public static void stopServer() {
        logAction("stopServer");
        Globals.showLog(TAG, "🛑 Gateway-Server wird gestoppt...");
        
        isGatewayRunning = false;
        isGatewayInitialized = false;
        gatewayStartTime = 0;
        
        if (gatewayServer != null) {
            try {
                gatewayServer.shutdown();
                Globals.showLog(TAG, "✅ Gateway-Server gestoppt");
            } catch (Exception e) {
                Globals.showLog(TAG, "⚠️ Fehler beim Stoppen: " + e.getMessage());
            }
            gatewayServer = null;
        }
        
        mActivityRef = null;
        currentActivityName = "None";
    }
}