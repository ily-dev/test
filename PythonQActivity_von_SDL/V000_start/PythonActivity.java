package org.kivy.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.Toast;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import org.kivy.android.launcher.Project;
import org.libsdl.app.SDLActivity;
import org.renpy.android.ResourceManager;
import org.kivy.android.PythonService;

import android.app.ProgressDialog;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CountDownLatch;

// ★ ★ ★ PY4J IMPORT ★ ★ ★
import org.gateway.android.Py4JGateway;

import android.view.WindowManager;
import android.view.Gravity;

import android.view.MotionEvent;

//qt 

public class PythonActivity extends SDLActivity {
    private static final String TAG = "PythonActivity";
    //private ProgressDialog mProgressDialog;
    //private Handler mHandler = new Handler(Looper.getMainLooper());
    
    private ProgressDialog mProgressDialog;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    
    // ─── DIALOG IN EINEM THREAD STARTEN UND WARTEN ──────────────
    CountDownLatch latch = new CountDownLatch(1);
    

    public static PythonActivity mActivity = null;

    private ResourceManager resourceManager = null;
    private Bundle mMetaData = null;
    private PowerManager.WakeLock mWakeLock = null;
    private static Context appContext = null;  // ← Neu!
    
    
    // ★ ★ ★ STATISCHE INSTANZ ★ ★ ★
    private static PythonActivity instance = null;
    
    // ★ ★ ★ getInstance() METHODE ★ ★ ★
    public static PythonActivity getInstance() {
        return instance;
    }
    
    
    //SDL2 auf app entpacken
    public String getAppRoot() {
        String app_root = getFilesDir().getAbsolutePath() + "/sdl2";
        return app_root;
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
                showLog(TAG, "✅ Spinner angezeigt");
                latch.countDown(); // Dialog wurde angezeigt!
            });
        }).start();
        
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();
            
            // ★ ★ ★ TOUCH-POSITION AUSGEBEN ★ ★ ★
            showLog(TAG, "👆 Touch erkannt: x=" + x + ", y=" + y);
            
            switchToReTerminal();
            
            // ★ ★ ★ PRÜFEN OB BEREICH TRANSPARENT IST ★ ★ ★
            // Dazu brauchen wir den Pixel an der Touch-Position
            checkIfTransparent((int)x, (int)y);
        }
        return super.onTouchEvent(event);
    }
    
    // PythonActivity.java
    private void checkIfTransparent(int x, int y) {
        try {
            // ★ ★ ★ 1. VIEW AM TOUCH-PUNKT FINDEN ★ ★ ★
            View view = getWindow().getDecorView().findViewById(android.R.id.content);
            
            // ★ ★ ★ 2. BITMAP DES VIEWS ERSTELLEN ★ ★ ★
            view.setDrawingCacheEnabled(true);
            Bitmap bitmap = view.getDrawingCache();
            
            if (bitmap != null && x < bitmap.getWidth() && y < bitmap.getHeight()) {
                // ★ ★ ★ 3. PIXEL AN DER TOUCH-POSITION LESEN ★ ★ ★
                int pixel = bitmap.getPixel(x, y);
                
                // ★ ★ ★ 4. ALPHA-WERT PRÜFEN (0 = voll transparent, 255 = voll deckend) ★ ★ ★
                int alpha = android.graphics.Color.alpha(pixel);
                
                if (alpha < 10) {
                    showLog(TAG, "🔲 Bereich ist TRANSPARENT (alpha=" + alpha + ")");
                } else {
                    showLog(TAG, "🟦 Bereich ist NICHT transparent (alpha=" + alpha + ")");
                    showLog(TAG, "   Farbe: #" + Integer.toHexString(pixel));
                }
            }
            
            view.setDrawingCacheEnabled(false);
            
        } catch (Exception e) {
            showLog(TAG, "❌ Fehler beim Prüfen der Transparenz: " + e.getMessage());
        }
    }
    
    private void switchToReTerminal() {
            try {
                // ★ ★ ★ MIT FLAG_ACTIVITY_NEW_TASK ★ ★ ★
                Intent intent = new Intent(getContext(), com.rk.terminal.ui.activities.terminal.MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                //intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                getContext().startActivity(intent);
                showLog(TAG, "✅ ReTerminal aktiviert");
            } catch (Exception e) {
                showLog(TAG, "❌ Fehler: " + e.getMessage());
            }
        }
        
    // ─── FORTSCHRITT AKTUALISIEREN ──────────────────────────
    private void updateProgress(int percent, String message) {
        mHandler.post(() -> {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.setProgress(Math.min(percent, 100));
                mProgressDialog.setMessage("Entpacke Assets... " + percent + "%" + (message != null ? " - " + message : ""));
                showLog(TAG, "📊 Fortschritt: " + percent + "%");
            }
        });
    }
    
    // ─── DIALOG AUSBLENDEN ──────────────────────────────────
    
    private void hideProgressDialog() {
        mHandler.post(() -> {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
                showLog(TAG, "✅ Spinner ausgeblendet");
            }
        });
    }
    

    // ─── SPLASH STICKY DEAKTIVIEREN ──────────────────────────
    private void hideSplashAndSticky() {
        try {
            //hideSplashScreen(2000);
            showLog(TAG, "✅ Splash Screen ausgeblendet (Sticky deaktiviert)");
        } catch (Exception e) {
            showLog(TAG, "⚠️ Splash Screen konnte nicht ausgeblendet werden: " + e.getMessage());
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
                showLog(TAG, "📊 update progress");
            }
        });
    }
    
    /*
    // Methode zum Schließen des Dialogs
    private void hideProgressDialog() {
        mHandler.post(() -> {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
        });
    }
    
    */
    
    

    public void moveWindow(int xOffset, int yOffset) {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        
        // Position setzen (x, y in Pixeln)
        params.x = xOffset;
        params.y = yOffset;
        
        // Gravity bestimmt den Bezugspunkt (z.B. oben links)
        params.gravity = Gravity.TOP | Gravity.START;
        
        // Änderungen anwenden
        getWindow().setAttributes(params);
    }
    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        showLog(TAG, "PythonActivity onCreate running");
        resourceManager = new ResourceManager(this);

        showLog(TAG, "About to do super onCreate");
        super.onCreate(savedInstanceState);
        showLog(TAG, "Did super onCreate");
        
        instance = this;
        
        // Beispiel: 1000 Pixel breit, 600 Pixel hoch
        getWindow().setLayout(1080, 1200);
        
        
         // Position anpassen (100px nach rechts / -100px nach oben)
        moveWindow(0, -10);

        
        // 1. Endlos-ProgressDialog sofort anzeigen (Dauerschleife)
        showInfiniteProgressDialog("Wird vorbereitet...", "Daten werden entpackt...");
    
        // 1. Sofort den Lade-Dialog anzeigen
        //showProgressDialog();
        
        // ★ ★ ★ PY4J: ACTIVITY-REFERENZ AN GATEWAY ÜBERGEBEN ★ ★ ★
        try {
            Py4JGateway.setActivity(this);
            showLog(TAG, "✅ Activity-Referenz an Py4JGateway übergeben");
        } catch (Exception e) {
            showLog(TAG, "❌ Fehler beim Setzen der Activity-Referenz: " + e.getMessage());
        }

        this.mActivity = this;
        appContext = getApplicationContext();  // ← Neu!
        
        //ic rausgenommen
        //hier wird preshplash angezeigt
        this.showLoadingScreen(this.getLoadingScreen());
        
        updateProgressM("private.tar", " ...wird entpackt!");
        
        //dann entpacken
        new UnpackFilesTask().execute(getAppRoot());
        
}
        
    
    // ============================================================
    // NEUE STATISCHE METHODE: Startet Python mit Activity
    // ============================================================
    public static void startPython() {
        try {
            // Benutze appContext statt mActivity
            Context ctx = appContext;
            
            if (ctx == null) {
                // Fallback: Versuche mActivity
                ctx = mActivity;
            }
            
            if (ctx != null) {
                Intent intent = new Intent(ctx, PythonActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                showLog(TAG, "✅ startPython wird ausgeführt!");
                ctx.startActivity(intent);
            } else {
                showLog(TAG, "❌ Context ist null - kann nicht starten");
            }
        } catch (Exception e) {
            e.printStackTrace();
            showLog(TAG, "❌ startPython failed: " + e.getMessage());
        }
    }
    
    // ============================================================
    // NEUE STATISCHE METHODE: Startet Python mit Activity
    // ============================================================
    // PythonActivity.java
    public static void startPythonNative(Activity activity) {
    try {
        showLog(TAG, "🎾 Python native starten (mit Activity)");
        
        // ★ 1. Prüfung: Activity
        if (activity == null) {
            showLog(TAG, "❌ Activity ist null!");
            return;
        }
        showLog(TAG, "✅ Activity ist gültig");
        
        // ★ 2. App-Root
        String app_root = activity.getFilesDir().getAbsolutePath() + "/sdl2";
        showLog(TAG, "📌 app_root: " + app_root);
        
        // ★ 3. Entrypoint
        String entry_point = getEntryPointStatic(app_root);
        showLog(TAG, "📌 entry_point: " + entry_point);
        
        // ★ 4. Environment setzen
        showLog(TAG, "📌 Setze Environment...");
        
        //SDLActivity auf QtActivity
       
         SDLActivity.nativeSetenv("ANDROID_ENTRYPOINT", entry_point);
        SDLActivity.nativeSetenv("ANDROID_ARGUMENT", app_root);
        SDLActivity.nativeSetenv("ANDROID_APP_PATH", app_root);
        SDLActivity.nativeSetenv("PYTHONHOME", app_root);
        SDLActivity.nativeSetenv("PYTHONPATH", app_root + ":" + app_root + "/lib");
        SDLActivity.nativeSetenv("PYTHONOPTIMIZE", "2");
        showLog(TAG, "✅ Environment gesetzt");
        
        // ★ 5. Python starten
        showLog(TAG, "📌 Starte Python (nativeRunMain)...");
        SDLActivity.nativeRunMain(app_root, entry_point, new Object[0]);
        
        showLog(TAG, "✅ Python native gestartet");
        
    } catch (NullPointerException e) {
        showLog(TAG, "❌ NullPointerException: " + e.getMessage());
        showLog(TAG, "   Stacktrace: " + e.getStackTrace()[0].toString());
        e.printStackTrace();
        
    } catch (IllegalArgumentException e) {
        showLog(TAG, "❌ IllegalArgumentException: " + e.getMessage());
        showLog(TAG, "   Stacktrace: " + e.getStackTrace()[0].toString());
        e.printStackTrace();
        
    } catch (SecurityException e) {
        showLog(TAG, "❌ SecurityException: " + e.getMessage());
        showLog(TAG, "   Stacktrace: " + e.getStackTrace()[0].toString());
        e.printStackTrace();
        
    } catch (UnsatisfiedLinkError e) {
        showLog(TAG, "❌ UnsatisfiedLinkError: nativeRunMain nicht verfügbar!");
        showLog(TAG, "   Message: " + e.getMessage());
        showLog(TAG, "   Stacktrace: " + e.getStackTrace()[0].toString());
        e.printStackTrace();
        
    } catch (ClassCastException e) {
        showLog(TAG, "❌ ClassCastException: " + e.getMessage());
        showLog(TAG, "   Stacktrace: " + e.getStackTrace()[0].toString());
        e.printStackTrace();
        
    } catch (ArrayIndexOutOfBoundsException e) {
        showLog(TAG, "❌ ArrayIndexOutOfBoundsException: " + e.getMessage());
        showLog(TAG, "   Stacktrace: " + e.getStackTrace()[0].toString());
        e.printStackTrace();
        
    } catch (RuntimeException e) {
        showLog(TAG, "❌ RuntimeException: " + e.getMessage());
        showLog(TAG, "   Stacktrace: " + e.getStackTrace()[0].toString());
        e.printStackTrace();
        
    } catch (Exception e) {
        showLog(TAG, "❌ Allgemeiner Fehler: " + e.getMessage());
        showLog(TAG, "   Stacktrace: " + e.getStackTrace()[0].toString());
        e.printStackTrace();
        
    } catch (Throwable t) {
        // ★ Fängt ALLES ab (auch schwerwiegende Fehler)
        showLog(TAG, "❌ Schwerwiegender Fehler (Throwable): " + t.getMessage());
        showLog(TAG, "   Stacktrace: " + t.getStackTrace()[0].toString());
        t.printStackTrace();
    }
}
    
    // ★ Hilfsmethode: getEntryPoint (statisch)
    private static String getEntryPointStatic(String search_dir) {
        File dir = new File(search_dir);
        // Prüfe ob main.pyc oder main.py existiert
        if (new File(dir, "main2.pyc").exists()) {
            return "main2.pyc";
        } else if (new File(dir, "main2.py").exists()) {
            return "main2.py";
        } else if (new File(dir, "service/main.pyc").exists()) {
            return "service/main.pyc";
        }
        return "main.pyc";  // Fallback
    }
            

    public void loadLibraries() {
        String app_root = new String(getAppRoot());
        File app_root_file = new File(app_root);
        PythonUtil.loadLibraries(app_root_file, new File(getApplicationInfo().nativeLibraryDir));
    }
    
    // Von private zu public static
    public static void showLog(String tag, String message) {
    
    //"PythonUtil"

    // Dann einfach:
    PythonService.showLog( tag, message);
    
    }

    /** Show an error using a toast. (Only makes sense from non-UI threads.) */
    public void toastError(final String msg) {

        final Activity thisActivity = this;

        runOnUiThread(
                new Runnable() {
                    public void run() {
                        Toast.makeText(thisActivity, msg, Toast.LENGTH_LONG).show();
                    }
                });

        // Wait to show the error.
        synchronized (this) {
            try {
                this.wait(1000);
            } catch (InterruptedException e) {
            }
        }
    }

    private class UnpackFilesTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            File app_root_file = new File(params[0]);
            showLog(TAG, "Ready to unpack");
            PythonUtil.unpackAsset(mActivity, "private", app_root_file, true);
            
            //updateProgress(50, "private.tar entpackt!");
            updateProgressM("PyBundle", " entpacken .... !");
            
            PythonUtil.unpackPyBundle(
                    mActivity,
                    getApplicationInfo().nativeLibraryDir + "/" + "libpybundle",
                    app_root_file,
                    false);
            
            updateProgressM("Entpacken", " kivy ist abgeschlossent!");
            
            // 5. Aufräumen (Dialog weg, Splash weg)
            
            hideProgressDialog();
            
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
        
            
            showLog(TAG, "onPostExecute");
            // Figure out the directory where the game is. If the game was
            // given to us via an intent, then we use the scheme-specific
            // part of that intent to determine the file to launch. We
            // also use the android.txt file to determine the orientation.
            //
            // Otherwise, we use the public data, if we have it, or the
            // private data if we do not.
            mActivity.finishLoad();

            // finishLoad called setContentView with the SDL view, which
            // removed the loading screen. However, we still need it to
            // show until the app is ready to render, so pop it back up
            // on top of the SDL view.
            
            // ic rausgenommen Presplash geladen
            mActivity.showLoadingScreen(getLoadingScreen());

            String app_root_dir = getAppRoot();
            if (getIntent() != null
                    && getIntent().getAction() != null
                    && getIntent().getAction().equals("org.kivy.LAUNCH")) {
                File path = new File(getIntent().getData().getSchemeSpecificPart());

                Project p = Project.scanDirectory(path);
                String entry_point = getEntryPoint(p.dir);
                SDLActivity.nativeSetenv("ANDROID_ENTRYPOINT", p.dir + "/" + entry_point);
                SDLActivity.nativeSetenv("ANDROID_ARGUMENT", p.dir);
                SDLActivity.nativeSetenv("ANDROID_APP_PATH", p.dir);

                if (p != null) {
                    if (p.landscape) {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    } else {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    }
                }

                // Let old apps know they started.
                try {
                    FileWriter f = new FileWriter(new File(path, ".launch"));
                    f.write("started");
                    f.close();
                } catch (IOException e) {
                    // pass
                }
            } else {
                //vorher main.pyc
                //String entry_point = getEntryPoint(app_root_dir);
                
                //jetzt /main.pyc
                String entry_point =getEntryPoint(app_root_dir);
                
                //jetzt /service/main.pyc
                //String entry_point = getEntryPointStatic(app_root);
                
                SDLActivity.nativeSetenv("ANDROID_ENTRYPOINT", entry_point);
                SDLActivity.nativeSetenv("ANDROID_ARGUMENT", app_root_dir);
                SDLActivity.nativeSetenv("ANDROID_APP_PATH", app_root_dir);
            }

            String mFilesDirectory = mActivity.getFilesDir().getAbsolutePath();
            showLog(TAG, "Setting env vars for start.c and Python to use");
            
            //extra eingebaut ic wieder raus
            //SDLActivity.nativeSetenv("ANDROID_ENTRYPOINT", "main2.py");
            
            SDLActivity.nativeSetenv("ANDROID_PRIVATE", mFilesDirectory);
            SDLActivity.nativeSetenv("ANDROID_UNPACK", app_root_dir);
            SDLActivity.nativeSetenv("PYTHONHOME", app_root_dir);
            SDLActivity.nativeSetenv("PYTHONPATH", app_root_dir + ":" + app_root_dir + "/lib");
            SDLActivity.nativeSetenv("PYTHONOPTIMIZE", "2");
            
            
            
            // ★ Mit System.getenv() die tatsächlichen Werte auslesen
showLog(TAG, "📌 ANDROID_ENTRYPOINT: " + System.getenv("ANDROID_ENTRYPOINT"));
showLog(TAG, "📌 ANDROID_ARGUMENT: " + System.getenv("ANDROID_ARGUMENT"));
showLog(TAG, "📌 ANDROID_APP_PATH: " + System.getenv("ANDROID_APP_PATH"));
showLog(TAG, "📌 ANDROID_PRIVATE: " + System.getenv("ANDROID_PRIVATE"));
showLog(TAG, "📌 ANDROID_UNPACK: " + System.getenv("ANDROID_UNPACK"));
showLog(TAG, "📌 PYTHONHOME: " + System.getenv("PYTHONHOME"));
showLog(TAG, "📌 PYTHONPATH: " + System.getenv("PYTHONPATH"));
showLog(TAG, "📌 PYTHONOPTIMIZE: " + System.getenv("PYTHONOPTIMIZE"));

showLog(TAG, "========================================");



            try {
                showLog(TAG, "Access to our meta-data...");
                mActivity.mMetaData =
                        mActivity
                                .getPackageManager()
                                .getApplicationInfo(
                                        mActivity.getPackageName(), PackageManager.GET_META_DATA)
                                .metaData;

                PowerManager pm = (PowerManager) mActivity.getSystemService(Context.POWER_SERVICE);
                if (mActivity.mMetaData.getInt("wakelock") == 1) {
                    mActivity.mWakeLock =
                            pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "Screen On");
                    mActivity.mWakeLock.acquire();
                }
                if (mActivity.mMetaData.getInt("surface.transparent") != 0) {
                    showLog(TAG, "Surface will be transparent.");
                    getSurface().setZOrderOnTop(true);
                    getSurface().getHolder().setFormat(PixelFormat.TRANSPARENT);
                } else {
                    showLog(TAG, "Surface will NOT be transparent");
                }
            } catch (PackageManager.NameNotFoundException e) {
            }

            // Launch app if that hasn't been done yet:
            if (mActivity.mHasFocus
                    && (
                    // never went into proper resume state:
                    mActivity.mCurrentNativeState == NativeState.INIT
                            || (
                            // resumed earlier but wasn't ready yet
                            mActivity.mCurrentNativeState == NativeState.RESUMED
                                    && mActivity.mSDLThread == null))) {
                // Because sometimes the app will get stuck here and never
                // actually run, ensure that it gets launched if we're active:
                
                //ic rausgenommen hier startet python 
                //if (skipPython) {
                // Nur starten wenn nicht übersprungen
                mActivity.resumeNativeThread();
                //}
                
              
            }
        }

        @Override
        protected void onPreExecute() {}

        @Override
        protected void onProgressUpdate(Void... values) {}
    }

    public static ViewGroup getLayout() {
        return mLayout;
    }

    public static SurfaceView getSurface() {
        return mSurface;
    }

    // ----------------------------------------------------------------------------
    // Listener interface for onNewIntent
    //

    public interface NewIntentListener {
        void onNewIntent(Intent intent);
    }

    private List<NewIntentListener> newIntentListeners = null;

    public void registerNewIntentListener(NewIntentListener listener) {
        if (this.newIntentListeners == null)
            this.newIntentListeners =
                    Collections.synchronizedList(new ArrayList<NewIntentListener>());
        this.newIntentListeners.add(listener);
    }

    public void unregisterNewIntentListener(NewIntentListener listener) {
        if (this.newIntentListeners == null) return;
        this.newIntentListeners.remove(listener);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (this.newIntentListeners == null) return;
        this.onResume();
        synchronized (this.newIntentListeners) {
            Iterator<NewIntentListener> iterator = this.newIntentListeners.iterator();
            while (iterator.hasNext()) {
                (iterator.next()).onNewIntent(intent);
            }
        }
    }

    // ----------------------------------------------------------------------------
    // Listener interface for onActivityResult
    //

    public interface ActivityResultListener {
        void onActivityResult(int requestCode, int resultCode, Intent data);
    }

    private List<ActivityResultListener> activityResultListeners = null;

    public void registerActivityResultListener(ActivityResultListener listener) {
        if (this.activityResultListeners == null)
            this.activityResultListeners =
                    Collections.synchronizedList(new ArrayList<ActivityResultListener>());
        this.activityResultListeners.add(listener);
    }

    public void unregisterActivityResultListener(ActivityResultListener listener) {
        if (this.activityResultListeners == null) return;
        this.activityResultListeners.remove(listener);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (this.activityResultListeners == null) return;
        this.onResume();
        synchronized (this.activityResultListeners) {
            Iterator<ActivityResultListener> iterator = this.activityResultListeners.iterator();
            while (iterator.hasNext())
                (iterator.next()).onActivityResult(requestCode, resultCode, intent);
        }
    }

    public static void start_service(
            String serviceTitle, String serviceDescription, String pythonServiceArgument) {
        _do_start_service(serviceTitle, serviceDescription, pythonServiceArgument, true);
    }

    public static void start_service_not_as_foreground(
            String serviceTitle, String serviceDescription, String pythonServiceArgument) {
        _do_start_service(serviceTitle, serviceDescription, pythonServiceArgument, false);
    }

    public static void _do_start_service(
            String serviceTitle,
            String serviceDescription,
            String pythonServiceArgument,
            boolean showForegroundNotification) {
        Intent serviceIntent = new Intent(PythonActivity.mActivity, PythonService.class);
        String argument = PythonActivity.mActivity.getFilesDir().getAbsolutePath();
        String app_root_dir = PythonActivity.mActivity.getAppRoot();
        String entry_point = PythonActivity.mActivity.getEntryPoint(app_root_dir + "/service");
        serviceIntent.putExtra("androidPrivate", argument);
        serviceIntent.putExtra("androidArgument", app_root_dir);
        serviceIntent.putExtra("serviceEntrypoint", "service/" + entry_point);
        serviceIntent.putExtra("pythonName", "python");
        serviceIntent.putExtra("pythonHome", app_root_dir);
        serviceIntent.putExtra("pythonPath", app_root_dir + ":" + app_root_dir + "/lib");
        serviceIntent.putExtra(
                "serviceStartAsForeground", (showForegroundNotification ? "true" : "false"));
        serviceIntent.putExtra("serviceTitle", serviceTitle);
        serviceIntent.putExtra("serviceDescription", serviceDescription);
        serviceIntent.putExtra("pythonServiceArgument", pythonServiceArgument);
        PythonActivity.mActivity.startService(serviceIntent);
    }

    public static void stop_service() {
        Intent serviceIntent = new Intent(PythonActivity.mActivity, PythonService.class);
        PythonActivity.mActivity.stopService(serviceIntent);
    }

    /** Loading screen view * */
    public static ImageView mImageView = null;

    public static View mLottieView = null;

    /** Whether main routine/actual app has started yet * */
    protected boolean mAppConfirmedActive = false;

    /** Timer for delayed loading screen removal. * */
    protected Timer loadingScreenRemovalTimer = null;

    // Overridden since it's called often, to check whether to remove the
    // loading screen:
    @Override
    protected boolean sendCommand(int command, Object data) {
        boolean result = super.sendCommand(command, data);
        considerLoadingScreenRemoval();
        return result;
    }

    /** Confirm that the app's main routine has been launched. */
    @Override
    public void appConfirmedActive() {
        if (!mAppConfirmedActive) {
            showLog(TAG, "appConfirmedActive() -> preparing loading screen removal");
            mAppConfirmedActive = true;
            considerLoadingScreenRemoval();
        }
    }

    /**
     * This is called from various places to check whether the app's main routine has been launched
     * already, and if it has, then the loading screen will be removed.
     */
    public void considerLoadingScreenRemoval() {
        if (loadingScreenRemovalTimer != null) return;
        runOnUiThread(
                new Runnable() {
                    public void run() {
                        if (((PythonActivity) PythonActivity.mSingleton).mAppConfirmedActive
                                && loadingScreenRemovalTimer == null) {
                            // Remove loading screen but with a delay.
                            // (app can use p4a's android.loadingscreen module to
                            // do it quicker if it wants to)
                            // get a handler (call from main thread)
                            // this will run when timer elapses
                            TimerTask removalTask =
                                    new TimerTask() {
                                        @Override
                                        public void run() {
                                            // post a runnable to the handler
                                            runOnUiThread(
                                                    new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            PythonActivity activity =
                                                                    ((PythonActivity)
                                                                            PythonActivity
                                                                                    .mSingleton);
                                                            if (activity != null)
                                                                activity.removeLoadingScreen();
                                                        }
                                                    });
                                        }
                                    };
                            loadingScreenRemovalTimer = new Timer();
                            loadingScreenRemovalTimer.schedule(removalTask, 5000);
                        }
                    }
                });
    }

    public void removeLoadingScreen() {
        runOnUiThread(
                new Runnable() {
                    public void run() {
                        View view = mLottieView != null ? mLottieView : mImageView;
                        if (view != null && view.getParent() != null) {
                            ((ViewGroup) view.getParent()).removeView(view);
                            mLottieView = null;
                            mImageView = null;
                        }
                    }
                });
    }

    public String getEntryPoint(String search_dir) {
        /* Get the main file (.pyc|.py) depending on if we
         * have a compiled version or not.
         */
        List<String> entryPoints = new ArrayList<String>();
        entryPoints.add("main.pyc"); // python 3 compiled files
        for (String value : entryPoints) {
            File mainFile = new File(search_dir + "/" + value);
            if (mainFile.exists()) {
                return value;
            }
        }
        return "main.py";
    }

    protected void showLoadingScreen(View view) {
        try {
            if (mLayout == null) {
                setContentView(view);
            } else if (view.getParent() == null) {
                mLayout.addView(view);
            }
        } catch (IllegalStateException e) {
            // The loading screen can be attempted to be applied twice if app
            // is tabbed in/out, quickly.
            // (Gives error "The specified child already has a parent.
            // You must call removeView() on the child's parent first.")
        }
    }

    protected void setBackgroundColor(View view) {
        /*
         * Set the presplash loading screen background color
         * https://developer.android.com/reference/android/graphics/Color.html
         * Parse the color string, and return the corresponding color-int.
         * If the string cannot be parsed, throws an IllegalArgumentException exception.
         * Supported formats are: #RRGGBB #AARRGGBB or one of the following names:
         * 'red', 'blue', 'green', 'black', 'white', 'gray', 'cyan', 'magenta', 'yellow',
         * 'lightgray', 'darkgray', 'grey', 'lightgrey', 'darkgrey', 'aqua', 'fuchsia',
         * 'lime', 'maroon', 'navy', 'olive', 'purple', 'silver', 'teal'.
         */
        String backgroundColor = resourceManager.getString("presplash_color");
        if (backgroundColor != null) {
            try {
                view.setBackgroundColor(Color.parseColor(backgroundColor));
            } catch (IllegalArgumentException e) {
            }
        }
    }

    protected View getLoadingScreen() {
        // If we have an mLottieView or mImageView already, then do
        // nothing because it will have already been made the content
        // view or added to the layout.
        if (mLottieView != null || mImageView != null) {
            // we already have a splash screen
            return mLottieView != null ? mLottieView : mImageView;
        }

        // first try to load the lottie one
        try {
            mLottieView =
                    getLayoutInflater()
                            .inflate(
                                    this.resourceManager.getIdentifier("lottie", "layout"),
                                    mLayout,
                                    false);
            try {
                if (mLayout == null) {
                    setContentView(mLottieView);
                } else if (PythonActivity.mLottieView.getParent() == null) {
                    mLayout.addView(mLottieView);
                }
            } catch (IllegalStateException e) {
                // The loading screen can be attempted to be applied twice if app
                // is tabbed in/out, quickly.
                // (Gives error "The specified child already has a parent.
                // You must call removeView() on the child's parent first.")
            }
            setBackgroundColor(mLottieView);
            return mLottieView;
        } catch (NotFoundException e) {
            showLog("SDL", "couldn't find lottie layout or animation, trying static splash");
        }

        // no lottie asset, try to load the static image then
        int presplashId = this.resourceManager.getIdentifier("presplash", "drawable");
        InputStream is = this.getResources().openRawResource(presplashId);
        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeStream(is);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
            }
            ;
        }

        mImageView = new ImageView(this);
        mImageView.setImageBitmap(bitmap);
        setBackgroundColor(mImageView);

        mImageView.setLayoutParams(
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));
        mImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return mImageView;
    }

    @Override
    protected void onPause() {
        if (this.mWakeLock != null && mWakeLock.isHeld()) {
            this.mWakeLock.release();
        }

        showLog(TAG, "onPause()");
        try {
            super.onPause();
        } catch (UnsatisfiedLinkError e) {
            // Catch pause while still in loading screen failing to
            // call native function (since it's not yet loaded)
        }
    }

    @Override
    protected void onResume() {
        if (this.mWakeLock != null) {
            this.mWakeLock.acquire();
        }
        showLog(TAG, "onResume()");
        try {
            super.onResume();
        } catch (UnsatisfiedLinkError e) {
            // Catch resume while still in loading screen failing to
            // call native function (since it's not yet loaded)
        }
        considerLoadingScreenRemoval();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        try {
            super.onWindowFocusChanged(hasFocus);
        } catch (UnsatisfiedLinkError e) {
            // Catch window focus while still in loading screen failing to
            // call native function (since it's not yet loaded)
        }
        considerLoadingScreenRemoval();
    }

    /**
     * Used by android.permissions p4a module to register a call back after requesting runtime
     * permissions
     */
    public interface PermissionsCallback {
        void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults);
    }

    private PermissionsCallback permissionCallback;
    private boolean havePermissionsCallback = false;

    public void addPermissionsCallback(PermissionsCallback callback) {
        permissionCallback = callback;
        havePermissionsCallback = true;
        showLog(TAG, "addPermissionsCallback(): Added callback for onRequestPermissionsResult");
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        showLog(TAG, "onRequestPermissionsResult()");
        if (havePermissionsCallback) {
            showLog(TAG, "onRequestPermissionsResult passed to callback");
            permissionCallback.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /** Used by android.permissions p4a module to check a permission */
    public boolean checkCurrentPermission(String permission) {
        if (android.os.Build.VERSION.SDK_INT < 23) return true;

        try {
            java.lang.reflect.Method methodCheckPermission =
                    Activity.class.getMethod("checkSelfPermission", String.class);
            Object resultObj = methodCheckPermission.invoke(this, permission);
            int result = Integer.parseInt(resultObj.toString());
            if (result == PackageManager.PERMISSION_GRANTED) return true;
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
        }
        return false;
    }

    /** Used by android.permissions p4a module to request runtime permissions */
    public void requestPermissionsWithRequestCode(String[] permissions, int requestCode) {
        if (android.os.Build.VERSION.SDK_INT < 23) return;
        try {
            java.lang.reflect.Method methodRequestPermission =
                    Activity.class.getMethod("requestPermissions", String[].class, int.class);
            methodRequestPermission.invoke(this, permissions, requestCode);
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
        }
    }

    public void requestPermissions(String[] permissions) {
        requestPermissionsWithRequestCode(permissions, 1);
    }

    public static void changeKeyboard(int inputType) {
        if (SDLActivity.keyboardInputType != inputType) {
            SDLActivity.keyboardInputType = inputType;
            InputMethodManager imm =
                    (InputMethodManager)
                            getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.restartInput(mTextEdit);
        }
    }
}
