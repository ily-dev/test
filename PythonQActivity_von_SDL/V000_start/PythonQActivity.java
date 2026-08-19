// PythonQActivity.java
package org.kivy.android;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import org.qtproject.qt.android.bindings.QtActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import java.util.concurrent.CountDownLatch;

import android.view.View;
import org.renpy.android.ResourceManager;

// ★ ★ ★ PY4J IMPORT ★ ★ ★
import org.gateway.android.Py4JGateway;

public class PythonQActivity extends QtActivity {

    private static final String TAG = "PythonQActivity";
    private ProgressDialog mProgressDialog;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    public static PythonQActivity mActivity = null;
    
    private ResourceManager resourceManager = null;

    private Bundle mMetaData = null;
    private PowerManager.WakeLock mWakeLock = null;
    
    private static Context appContext = null;  // ← Neu!
    
    // ─── DIALOG IN EINEM THREAD STARTEN UND WARTEN ──────────────
    CountDownLatch latch = new CountDownLatch(1);
    
    // ★ ★ ★ STATISCHE INSTANZ ★ ★ ★
    private static PythonQActivity instance = null;
    
    // ★ ★ ★ getInstance() METHODE ★ ★ ★
    public static PythonQActivity getInstance() {
        return instance;
    }
    
    /*
    //System.loadLibrary() vorladen
    static {
    try {
        // Versuche zuerst den Standardweg
        System.loadLibrary("ld-linux-aarch64");
        showLog("LoadLib ✅ ld-linux-aarch64.so geladen (System)");
    } catch (UnsatisfiedLinkError e) {
        showLog("LoadLib ⚠️ System.loadLibrary fehlgeschlagen, versuche absoluten Pfad...");
        try {
            // Pfad zum App-Files-Verzeichnis
            String libPath7 = "/data/data/com.meinname.loginapp8.debug/files/ld-linux-aarch64.so.1";
            System.load(libPath7);
            showLog("LoadLib ✅ ld-linux-aarch64.so geladen von: " + libPath7);
        } catch (UnsatisfiedLinkError e2) {
            showLog("LoadLib ❌ ld-linux-aarch64.so.1 konnte nicht geladen werden" + e2);
        }
    }
    
}
*/
    
    // ─── SHOWLOG ───────────────────────────────
    private static void showLog(String msg) {
        PythonService.showLog(TAG, msg);
    }

    //app ordner definieren vorher "/app"
    public String getAppRoot() {
        String app_root = getFilesDir().getAbsolutePath() + "/qt";
        return app_root;
    }

    public String getEntryPoint(String search_dir) {
        List<String> entryPoints = new ArrayList<String>();
        entryPoints.add("main2.pyc");
        for (String value : entryPoints) {
            File mainFile = new File(search_dir + "/" + value);
            if (mainFile.exists()) {
                return value;
            }
        }
        return "main2.py";
    }

    public void setEnvironmentVariable(String key, String value) {
        try {
            android.system.Os.setenv(key, value, true);
        } catch (Exception e) {
            Log.e("Qt bootstrap", "Unable set environment variable:" + key + "=" + value);
            e.printStackTrace();
        }
    }

    // ─── DIALOG IN EINEM THREAD STARTEN ──────────────────────
    private void showProgressDialog() {
        new Thread(() -> {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (mProgressDialog == null) {
                    mProgressDialog = new ProgressDialog(this);
                    mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    mProgressDialog.setCancelable(false);
                    mProgressDialog.setTitle("Initialisierung");
                    mProgressDialog.setMessage("Entpacke Assets... 0%");
                    mProgressDialog.setMax(100);
                    mProgressDialog.setProgress(0);
                }
                mProgressDialog.show();
                showLog("✅ Spinner angezeigt");
                latch.countDown(); // Dialog wurde angezeigt!
            });
        }).start();
        
    }

    // ─── FORTSCHRITT AKTUALISIEREN ──────────────────────────
    private void updateProgress(int percent, String message) {
        mHandler.post(() -> {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.setProgress(Math.min(percent, 100));
                mProgressDialog.setMessage("Entpacke Assets... " + percent + "%" + (message != null ? " - " + message : ""));
                showLog("📊 Fortschritt: " + percent + "%");
            }
        });
    }

    // ─── DIALOG AUSBLENDEN ──────────────────────────────
    private void hideProgressDialog() {
        mHandler.post(() -> {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
                showLog("✅ Spinner ausgeblendet");
            }
        });
    }

    // ─── SPLASH STICKY DEAKTIVIEREN ──────────────────────────
    private void hideSplashAndSticky() {
        try {
            hideSplashScreen(2000);
            showLog("✅ Splash Screen ausgeblendet (Sticky deaktiviert)");
        } catch (Exception e) {
            showLog("⚠️ Splash Screen konnte nicht ausgeblendet werden: " + e.getMessage());
        }
    }
    
    // Methode zum Starten des Endlos-Ladekreises
    private void showInfiniteProgressDialog(String title, String message) {
        mHandler.post(() -> {
            if (mProgressDialog == null) {
                mProgressDialog = new ProgressDialog(this);
                mProgressDialog.setTitle(title);
                mProgressDialog.setMessage(message);
                // Das sorgt für die Endlos-Dauerschleife (Spinner-Animation)
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                mProgressDialog.setIndeterminate(true); 
                mProgressDialog.setCancelable(false); // Nutzer kann den Dialog nicht wegklicken
            }
            if (!mProgressDialog.isShowing()) {
                mProgressDialog.show();
            }
        });
    }
    
    // neu nur String
    // ─── FORTSCHRITT AKTUALISIEREN ──────────────────────────
    private void updateProgressM(String title, String message) {
        mHandler.post(() -> {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.setTitle(title);
                mProgressDialog.setMessage("Entpacke Assets... " + (message != null ? " - " + message : ""));
                showLog( "📊 update progress");
            }
        });
    }
 
    @Override
public void onCreate(Bundle savedInstanceState) {
    
    showLog("🚀 onCreate - Activity wird erstellt...");
    
    super.onCreate(savedInstanceState);
    
    instance = this;
    
    // ★ ★ ★ PY4J: ACTIVITY-REFERENZ AN GATEWAY ÜBERGEBEN ★ ★ ★
    try {
            Py4JGateway.setActivity(this);
            showLog( "✅ Activity-Referenz an Py4JGateway übergeben");
        } catch (Exception e) 
        {
            showLog( "❌ Fehler beim Setzen der Activity-Referenz: " + e.getMessage());
        }
    
    this.mActivity = this;
    appContext = getApplicationContext();  // ← Neu!
    
    // 1. Sofort den Lade-Dialog anzeigen
    showInfiniteProgressDialog("Wird vorbereitet...", "Daten werden entpackt...");
    
    updateProgressM("private.tar", " ...wird entpackt!");
    
    // 2. Den gesamten Entpack- und Setup-Prozess in einen Hintergrund-Thread legen
    Thread extractionThread = new Thread(() -> {
        try {
            File app_root_file = new File(getAppRoot());

            updateProgressM("private.tar", " ...wird entpackt!");
            PythonUtil.unpackAsset(mActivity, "private2", app_root_file, true);
            
            updateProgressM("libpybundleQt.so", " ...wird entpackt!");
            PythonUtil.unpackPyBundle(
                    mActivity,
                    getApplicationInfo().nativeLibraryDir + "/" + "libpybundleQt",
                    app_root_file,
                    false);
                    
            updateProgressM("entpackt", " ...ist abgeschlossen!!");

            // Umgebungsvariablen setzen (ERST HIER, wenn die Dateien da sind!)
            String app_root_dir = getAppRoot();
            String mFilesDirectory = mActivity.getFilesDir().getAbsolutePath();
            String entry_point = getEntryPoint(app_root_dir);

            setEnvironmentVariable("ANDROID_ENTRYPOINT", entry_point);
            setEnvironmentVariable("ANDROID_ARGUMENT", app_root_dir);
            setEnvironmentVariable("ANDROID_APP_PATH", app_root_dir);
            setEnvironmentVariable("ANDROID_PRIVATE", mFilesDirectory);
            setEnvironmentVariable("ANDROID_UNPACK", app_root_dir);
            setEnvironmentVariable("PYTHONHOME", app_root_dir);
            setEnvironmentVariable("PYTHONPATH", app_root_dir + ":" + app_root_dir + "/lib");
            setEnvironmentVariable("PYTHONOPTIMIZE", "2");

            showLog("✅ Umgebungsvariablen gesetzt");
            
            updateProgressM("python", " ...wird gestartet....!!");
            
            
            hideProgressDialog();
            hideSplashAndSticky();
            
            
            showLog("✅ onCreate vollständig abgeschlossen");
            
        } catch (Exception e) {
            showLog("❌ Fehler beim Entpacken: " + e.getMessage());
            e.printStackTrace();
        }
    });

    // Thread starten
    extractionThread.start();
    
    
    try {
        // 3. WICHTIG: Hier wartet der Hauptthread, bis das Entpacken komplett beendet ist!
        extractionThread.join();
        showLog("✅ Entpack-Thread beendet, starte super.onCreate()");
        
        updateProgressM("python", " ...wird gestartet....!!");
    } catch (InterruptedException e) {
        e.printStackTrace();
    }
    
    
    updateProgressM("python", " ...wird gestartet....!!");

}

    @Override
    public void onDestroy() {
        showLog("🛑 onDestroy - Activity wird beendet...");
        Log.i("Destroy", "end of app");

        hideProgressDialog();
        super.onDestroy();

        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
            showLog("🔋 WakeLock freigegeben");
        }

        android.os.Process.killProcess(android.os.Process.myPid());
        showLog("✅ onDestroy abgeschlossen");
    }

    long lastBackClick = SystemClock.elapsedRealtime();

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (SystemClock.elapsedRealtime() - lastBackClick > 2000) {
            lastBackClick = SystemClock.elapsedRealtime();
            Toast.makeText(this, "Click again to close the app", Toast.LENGTH_LONG).show();
            showLog("⌨️ Back-Taste: Zurück drücken zum Schließen");
            return true;
        }
        lastBackClick = SystemClock.elapsedRealtime();
        return super.onKeyDown(keyCode, event);
    }

    // ─── LISTENER ───────────────────────────────

    public interface NewIntentListener {
        void onNewIntent(Intent intent);
    }

    private List<NewIntentListener> newIntentListeners = null;

    public void registerNewIntentListener(NewIntentListener listener) {
        if (this.newIntentListeners == null)
            this.newIntentListeners = Collections.synchronizedList(new ArrayList<NewIntentListener>());
        this.newIntentListeners.add(listener);
        showLog("📋 registerNewIntentListener");
    }

    public void unregisterNewIntentListener(NewIntentListener listener) {
        if (this.newIntentListeners == null) return;
        this.newIntentListeners.remove(listener);
        showLog("📋 unregisterNewIntentListener");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        showLog("📨 onNewIntent - Neuer Intent");
        if (this.newIntentListeners == null) return;
        this.onResume();
        synchronized (this.newIntentListeners) {
            Iterator<NewIntentListener> iterator = this.newIntentListeners.iterator();
            while (iterator.hasNext()) {
                (iterator.next()).onNewIntent(intent);
            }
        }
    }

    public interface ActivityResultListener {
        void onActivityResult(int requestCode, int resultCode, Intent data);
    }

    private List<ActivityResultListener> activityResultListeners = null;

    public void registerActivityResultListener(ActivityResultListener listener) {
        if (this.activityResultListeners == null)
            this.activityResultListeners = Collections.synchronizedList(new ArrayList<ActivityResultListener>());
        this.activityResultListeners.add(listener);
        showLog("📋 registerActivityResultListener");
    }

    public void unregisterActivityResultListener(ActivityResultListener listener) {
        if (this.activityResultListeners == null) return;
        this.activityResultListeners.remove(listener);
        showLog("📋 unregisterActivityResultListener");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        showLog("📨 onActivityResult - requestCode=" + requestCode + ", resultCode=" + resultCode);
        if (this.activityResultListeners == null) return;
        this.onResume();
        synchronized (this.activityResultListeners) {
            Iterator<ActivityResultListener> iterator = this.activityResultListeners.iterator();
            while (iterator.hasNext())
                (iterator.next()).onActivityResult(requestCode, resultCode, intent);
        }
    }

    // ─── SERVICE ──────────────────────────────

    public static void start_service(String serviceTitle, String serviceDescription, String pythonServiceArgument) {
        showLog("📨 start_service - Service wird gestartet...");
        _do_start_service(serviceTitle, serviceDescription, pythonServiceArgument, true);
    }

    public static void start_service_not_as_foreground(String serviceTitle, String serviceDescription, String pythonServiceArgument) {
        showLog("📨 start_service_not_as_foreground - Service wird gestartet (nicht im Vordergrund)...");
        _do_start_service(serviceTitle, serviceDescription, pythonServiceArgument, false);
    }

    public static void _do_start_service(
            String serviceTitle,
            String serviceDescription,
            String pythonServiceArgument,
            boolean showForegroundNotification) {
        if (PythonQActivity.mActivity == null) {
            Log.e(TAG, "❌ PythonQActivity.mActivity ist null!");
            return;
        }
        showLog("📨 _do_start_service - Service wird gestartet...");
        showLog("   serviceTitle: " + serviceTitle);
        showLog("   serviceDescription: " + serviceDescription);
        showLog("   pythonServiceArgument: " + pythonServiceArgument);
        showLog("   showForegroundNotification: " + showForegroundNotification);

        Intent serviceIntent = new Intent(PythonQActivity.mActivity, PythonService.class);
        String argument = PythonQActivity.mActivity.getFilesDir().getAbsolutePath();
        String app_root_dir = PythonQActivity.mActivity.getAppRoot();
        String entry_point = PythonQActivity.mActivity.getEntryPoint(app_root_dir + "/service");

        serviceIntent.putExtra("androidPrivate", argument);
        serviceIntent.putExtra("androidArgument", app_root_dir);
        serviceIntent.putExtra("serviceEntrypoint", "service/" + entry_point);
        serviceIntent.putExtra("pythonName", "python");
        serviceIntent.putExtra("pythonHome", app_root_dir);
        serviceIntent.putExtra("pythonPath", app_root_dir + ":" + app_root_dir + "/lib");
        serviceIntent.putExtra("serviceStartAsForeground", showForegroundNotification ? "true" : "false");
        serviceIntent.putExtra("serviceTitle", serviceTitle);
        serviceIntent.putExtra("serviceDescription", serviceDescription);
        serviceIntent.putExtra("pythonServiceArgument", pythonServiceArgument);
        PythonQActivity.mActivity.startService(serviceIntent);
        showLog("✅ Service gestartet");
    }

    public static void stop_service() {
        if (PythonQActivity.mActivity == null) {
            Log.e(TAG, "❌ PythonQActivity.mActivity ist null!");
            return;
        }
        showLog("⏹️ stop_service - Service wird gestoppt...");
        Intent serviceIntent = new Intent(PythonQActivity.mActivity, PythonService.class);
        PythonQActivity.mActivity.stopService(serviceIntent);
        showLog("✅ Service gestoppt");
    }
    
    
    
    // ============================================================
    // ★ ★ ★ NEU: startPythonQt() - VON JAVA AUFRUFBAR ★ ★ ★
    // ============================================================
    public static void startPythonQt() {
        showLog("🚀 startPythonQt() aufgerufen");
        
        if (instance == null) {
            showLog("❌ PythonQActivity instance ist null!");
            return;
        }
        
        // ★ ★ ★ 1. PYTHONQACTIVITY STARTEN ★ ★ ★
        try {
            instance.startQtPython();
        } catch (Exception e) {
            showLog( "❌ Fehler beim Starten von Qt Python: " + e.getMessage());
        }
    }
    
    // ─── INTERNE START-METHODE ──────────────────────────────
    private void startQtPython() {
        showLog( "🚀 startQtPython() - Interne Methode");
        
        try {
            // ★ ★ ★ 1. APP-ROOT VERZEICHNIS ★ ★ ★
            String app_root_dir = getAppRoot();
            String mFilesDirectory = mActivity.getFilesDir().getAbsolutePath();
            
            showLog( "📂 app_root_dir: " + app_root_dir);
            showLog( "📂 mFilesDirectory: " + mFilesDirectory);
            
            // ★ ★ ★ 2. ENTRYPOINT BESTIMMEN ★ ★ ★
            String entry_point = getEntryPoint(app_root_dir);
            showLog( "📄 entry_point: " + entry_point);
            
            // ★ ★ ★ 3. UMWELTVARIABLEN SETZEN ★ ★ ★
            setEnvironmentVariable("ANDROID_ENTRYPOINT", entry_point);
            setEnvironmentVariable("ANDROID_ARGUMENT", app_root_dir);
            setEnvironmentVariable("ANDROID_APP_PATH", app_root_dir);
            setEnvironmentVariable("ANDROID_PRIVATE", mFilesDirectory);
            setEnvironmentVariable("ANDROID_UNPACK", app_root_dir);
            setEnvironmentVariable("PYTHONHOME", app_root_dir);
            setEnvironmentVariable("PYTHONPATH", app_root_dir + ":" + app_root_dir + "/lib");
            setEnvironmentVariable("PYTHONOPTIMIZE", "2");
            
            
            // ★ ★ ★ 5. PYTHON STARTEN ★ ★ ★
            showLog( "🐍 Python wird gestartet...");
            
            // ★ ★ ★ 6. SUPER.ONCREATE() AUFRUFEN (Python startet) ★ ★ ★
            // super.onCreate(savedInstanceState); // Wird bereits aufgerufen
            
            showLog("✅ Umgebungsvariablen gesetzt");
            showLog("✅ Python Qt gestartet");
            
        } catch (Exception e) {
            showLog( "❌ Fehler in startQtPython: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    
    
    
    
    
    
}