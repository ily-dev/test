package org.kivy.android;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;  // ★★★ IMPORT FÜR Build ★★★
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;  // ★★★ IMPORT FÜR Method ★★★
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.util.Log;

// ★ ★ ★ PY4J IMPORT ★ ★ ★
import org.gateway.android.Py4JGateway;

public class PythonQActivity extends Activity {
    private static final String TAG = "PythonQActivity";
    private ProgressDialog mProgressDialog;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private static PythonQActivity mActivity = null;
    private static Context appContext = null;
    private PowerManager.WakeLock mWakeLock = null;
    private Bundle mMetaData = null;
    private static PythonQActivity instance = null;
    public boolean mHasFocus = true;
    
    private File targetDir; // ★ Hier definieren

    // ------------------------------------------------------------------------
    // STATISCHE METHODEN & INSTANZEN
    // ------------------------------------------------------------------------
    public static PythonQActivity getInstance() { return instance; }

    public static void showLog(String tag, String message) {
        PythonService.showLog(tag, message);
    }
    
    public void setEnvironmentVariable(String key, String value) {
        try {
            android.system.Os.setenv(key, value, true);
        } catch (Exception e) {
            Log.e("Qt bootstrap", "Unable set environment variable:" + key + "=" + value);
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------------
    // APP-ROOT & ENTRYPOINT
    // ------------------------------------------------------------------------
    public String getAppRoot() {
        return getFilesDir().getAbsolutePath() + "/qt";
    }

    //geandert main2.pyc
    private String getEntryPoint(String search_dir) {
        List<String> entryPoints = new ArrayList<>();
        entryPoints.add("main2.pyc");
        for (String value : entryPoints) {
            File mainFile = new File(search_dir + "/" + value);
            if (mainFile.exists()) {
                return value;
            }
        }
        return "main2.py";
    }

    private static String getEntryPointStatic(String search_dir) {
        File dir = new File(search_dir);
        if (new File(dir, "main2.pyc").exists()) {
            return "main2.pyc";
        } else if (new File(dir, "main2.py").exists()) {
            return "main2.py";
        } else if (new File(dir, "service/main.pyc").exists()) {
            return "service/main.pyc";
        }
        return "main.pyc";
    }

    // ------------------------------------------------------------------------
    // PROGRESS DIALOG
    // ------------------------------------------------------------------------
    private void showProgressDialog(String title, String message) {
        new Thread(() -> {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (mProgressDialog == null) {
                    mProgressDialog = new ProgressDialog(this);
                    mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    mProgressDialog.setCancelable(false);
                    mProgressDialog.setMax(100);
                }
                mProgressDialog.setTitle(title);
                mProgressDialog.setMessage(message);
                mProgressDialog.setProgress(0);
                mProgressDialog.show();
                showLog(TAG, "✅ Spinner angezeigt");
            });
        }).start();
    }

    private void updateProgress(int percent, String message) {
        mHandler.post(() -> {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.setProgress(Math.min(percent, 100));
                mProgressDialog.setMessage("Entpacke Assets... " + percent + "%" + (message != null ? " - " + message : ""));
                showLog(TAG, "📊 Fortschritt: " + percent + "%");
            }
        });
    }

    private void hideProgressDialog() {
        mHandler.post(() -> {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
                showLog(TAG, "✅ Spinner ausgeblendet");
            }
        });
    }

    private void showInfiniteProgressDialog(String title, String message) {
        mHandler.post(() -> {
            if (mProgressDialog == null) {
                mProgressDialog = new ProgressDialog(this);
                mProgressDialog.setTitle(title);
                mProgressDialog.setMessage(message);
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
            }
            if (!mProgressDialog.isShowing()) {
                mProgressDialog.show();
            }
        });
    }

    private void updateProgressM(String title, String message) {
        mHandler.post(() -> {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.setTitle(title);
                mProgressDialog.setMessage("Entpacke Assets... " + (message != null ? " - " + message : ""));
                showLog(TAG, "📊 update progress");
            }
        });
    }
    
    
    private int calculateProgress(int fileCount, long totalBytes) {
        // Einfache Schätzung: 200 Dateien = 100%
        int maxFiles = 200;
        return Math.min(100, (fileCount * 100) / maxFiles);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }

    // ------------------------------------------------------------------------
    // LOADING SCREEN (Presplash)
    // ------------------------------------------------------------------------
    public static ImageView mImageView = null;
    public static View mLottieView = null;
    protected boolean mAppConfirmedActive = false;
    protected Timer loadingScreenRemovalTimer = null;

    protected void showLoadingScreen(View view) {
        try {
            if (view.getParent() == null) {
                ViewGroup decorView = (ViewGroup) getWindow().getDecorView();
                decorView.addView(view);
            }
        } catch (IllegalStateException e) {
            showLog(TAG, "Splash bereits vorhanden: " + e.getMessage());
        }
    }

    public void removeLoadingScreen() {
        runOnUiThread(() -> {
            View view = mLottieView != null ? mLottieView : mImageView;
            if (view != null && view.getParent() != null) {
                ((ViewGroup) view.getParent()).removeView(view);
                mLottieView = null;
                mImageView = null;
            }
        });
    }

    public void considerLoadingScreenRemoval() {
        if (loadingScreenRemovalTimer != null) return;
        runOnUiThread(() -> {
            if (mAppConfirmedActive && loadingScreenRemovalTimer == null) {
                TimerTask removalTask = new TimerTask() {
                    @Override
                    public void run() {
                        runOnUiThread(() -> {
                            if (PythonQActivity.this != null)
                                removeLoadingScreen();
                        });
                    }
                };
                loadingScreenRemovalTimer = new Timer();
                loadingScreenRemovalTimer.schedule(removalTask, 5000);
            }
        });
    }

    protected void setBackgroundColor(View view) {
        try {
            view.setBackgroundColor(Color.parseColor("#000000"));
        } catch (IllegalArgumentException e) {
            view.setBackgroundColor(Color.BLACK);
        }
    }

    protected View getLoadingScreen() {
        if (mLottieView != null || mImageView != null) {
            return mLottieView != null ? mLottieView : mImageView;
        }

        int presplashId = getResources().getIdentifier("presplash", "drawable", getPackageName());
        if (presplashId == 0) {
            showLog(TAG, "Kein presplash-Bild gefunden, verwende leeren Hintergrund");
            View dummy = new View(this);
            dummy.setBackgroundColor(Color.BLACK);
            return dummy;
        }

        InputStream is = getResources().openRawResource(presplashId);
        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeStream(is);
        } finally {
            try { if (is != null) is.close(); } catch (IOException e) { /* ignore */ }
        }

        mImageView = new ImageView(this);
        mImageView.setImageBitmap(bitmap);
        setBackgroundColor(mImageView);
        mImageView.setLayoutParams(
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );
        mImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return mImageView;
    }

    // ------------------------------------------------------------------------
    // LIFECYCLE
    // ------------------------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showLog(TAG, "PythonQActivity onCreate running (PHASE 1 - ENTPACKEN)");
        
        instance = this;
        mActivity = this;
        appContext = getApplicationContext();

        try {
            Py4JGateway.setActivity(this);
            showLog(TAG, "✅ Activity-Referenz an Py4JGateway übergeben");
        } catch (Exception e) {
            showLog(TAG, "❌ Fehler beim Setzen der Activity-Referenz: " + e.getMessage());
        }

        showInfiniteProgressDialog("Wird vorbereitet...", "Daten werden entpackt...");
        updateProgressM("private.tar", " ...wird entpackt!");

        this.showLoadingScreen(this.getLoadingScreen());

        new UnpackFilesTask().execute(getAppRoot());
    }
    
    // ★★★ ASYNCHRONES ENTPACKEN STARTEN ★★★
    private void startAsyncUnpack() {
        // 1. Dialog anzeigen
        showProgressDialog("Entpacke Dateien...", "Initialisiere...");
        
        // 2. private.tar asynchron entpacken
        PythonUtil.unpackAssetAsync(
            this,
            "private",
            targetDir,
            true,
            new AssetExtract.ProgressCallback() {
                @Override
                public void onProgress(int fileCount, long totalBytes, String currentFile) {
                    // ★★★ FORTSCHRITT AKTUALISIEREN ★★★
                    mHandler.post(() -> {
                        int percent = calculateProgress(fileCount, totalBytes);
                        String msg = "Datei " + fileCount + " (" + formatSize(totalBytes) + ")";
                        updateProgress(percent, msg);
                    });
                }

                @Override
                public void onComplete(boolean success, String message) {
                    mHandler.post(() -> {
                        if (success) {
                            // ★★★ NACH PRIVATE.TAR → PYBUNDLE ★★★
                            unpackPyBundleAsync();
                        } else {
                            hideProgressDialog();
                            Toast.makeText(PythonQActivity.this, 
                                "❌ Fehler: " + message, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        );
    }
    
    // ★★★ PYBUNDLE ASYNCHRON ENTPACKEN ★★★
    private void unpackPyBundleAsync() {
        updateProgressDialog(50, "Entpacke PyBundle...");
        
        PythonUtil.unpackPyBundleAsync(
            this,
            "libpybundle",
            targetDir,
            true,
            new AssetExtract.ProgressCallback() {
                @Override
                public void onProgress(int fileCount, long totalBytes, String currentFile) {
                    mHandler.post(() -> {
                        // Fortschritt 50%-100% skalieren
                        int percent = 50 + (calculateProgress(fileCount, totalBytes) / 2);
                        String msg = "PyBundle: " + fileCount + " Dateien";
                        updateProgress(percent, msg);
                    });
                }

                @Override
                public void onComplete(boolean success, String message) {
                    mHandler.post(() -> {
                        hideProgressDialog();
                        if (success) {
                            Toast.makeText(PythonQActivity.this, 
                                "✅ Alle Dateien entpackt!", Toast.LENGTH_SHORT).show();
                            // ★★★ PYTHON STARTEN ★★★
                            startPython();
                        } else {
                            Toast.makeText(PythonQActivity.this, 
                                "❌ Fehler: " + message, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        );
    }

    @Override
    protected void onPause() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        showLog(TAG, "onPause()");
        super.onPause();
    }

    @Override
    protected void onResume() {
        if (mWakeLock != null) {
            mWakeLock.acquire();
        }
        showLog(TAG, "onResume()");
        super.onResume();
        considerLoadingScreenRemoval();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        mHasFocus = hasFocus;
        considerLoadingScreenRemoval();
    }

    @Override
    protected void onDestroy() {
        showLog(TAG, "onDestroy");
        super.onDestroy();
    }

    public void appConfirmedActive() {
        if (!mAppConfirmedActive) {
            showLog(TAG, "appConfirmedActive() -> preparing loading screen removal");
            mAppConfirmedActive = true;
            considerLoadingScreenRemoval();
        }
    }

    // ------------------------------------------------------------------------
    // UNPACK TASK
    // ------------------------------------------------------------------------
    private class UnpackFilesTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            File app_root_file = new File(params[0]);
            showLog(TAG, "Ready to unpack");

            PythonUtil.unpackAsset(mActivity, "private", app_root_file, true);
            updateProgressM("PyBundleQt", " entpacken .... !");

            PythonUtil.unpackPyBundle(
                    mActivity,
                    getApplicationInfo().nativeLibraryDir + "/" + "libpybundleQt",
                    app_root_file,
                    false
            );

            updateProgressM("Entpacken", " abgeschlossen!");
            hideProgressDialog();

            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            showLog(TAG, "✅ onPostExecute - Entpacken abgeschlossen!");
            
            // ★★★ 1. UMWELTVARIABLEN SETZEN ★★★
            String app_root_dir = getAppRoot();
            String entry_point = getEntryPoint(app_root_dir);
            
            setEnvironmentVariable("ANDROID_ENTRYPOINT", entry_point);
            setEnvironmentVariable("ANDROID_ARGUMENT", app_root_dir);
            setEnvironmentVariable("ANDROID_APP_PATH", app_root_dir);
            
            setEnvironmentVariable("ANDROID_PRIVATE", getFilesDir().getAbsolutePath());
            
            setEnvironmentVariable("ANDROID_UNPACK", app_root_dir);
            
            setEnvironmentVariable("PYTHONHOME", app_root_dir);
            setEnvironmentVariable("PYTHONPATH", app_root_dir + ":" + app_root_dir + "/lib");
            
            setEnvironmentVariable("PYTHONOPTIMIZE", "2");
            
            showLog(TAG, "✅ Umgebungsvariablen gesetzt:");
            
            
            
            showLog(TAG, "   ANDROID_ARGUMENT = " + System.getProperty("ANDROID_ARGUMENT"));

            // ★★★ 2. WAKE-LOCK AKTIVIEREN ★★★
            try {
                mMetaData = getPackageManager()
                        .getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA)
                        .metaData;
                if (mMetaData.getInt("wakelock") == 1) {
                    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                    mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "Screen On");
                    mWakeLock.acquire();
                }
            } catch (PackageManager.NameNotFoundException e) {
                showLog(TAG, "MetaData nicht gefunden");
            }

            // ★★★ 3. QtActivity STARTEN (PHASE 2) ★★★
            startQtActivity();
        }
    }

    // ------------------------------------------------------------------------
    // ★★★ PHASE 2: QtActivityBase STARTEN ★★★
    // ------------------------------------------------------------------------
    private void startQtActivity() {
        showLog(TAG, "🚀 Starte QtActivityBase (PHASE 2 - Qt-Start)");
        
        try {
            Class<?> qtActivityClass = Class.forName("org.qtproject.qt.android.QtActivityBase");
            //org.qtproject.qt.android.QtActivityBase
            
            Intent intent = new Intent(this, qtActivityClass);
            
            // Umgebungsvariablen als Extras übergeben
            intent.putExtra("ANDROID_ENTRYPOINT", System.getProperty("ANDROID_ENTRYPOINT"));
            intent.putExtra("ANDROID_ARGUMENT", System.getProperty("ANDROID_ARGUMENT"));
            intent.putExtra("ANDROID_APP_PATH", System.getProperty("ANDROID_APP_PATH"));
            intent.putExtra("ANDROID_PRIVATE", System.getProperty("ANDROID_PRIVATE"));
            intent.putExtra("ANDROID_UNPACK", System.getProperty("ANDROID_UNPACK"));
            intent.putExtra("PYTHONHOME", System.getProperty("PYTHONHOME"));
            intent.putExtra("PYTHONPATH", System.getProperty("PYTHONPATH"));
            intent.putExtra("PYTHONOPTIMIZE", System.getProperty("PYTHONOPTIMIZE"));
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            finish();
            startActivity(intent);
            
            showLog(TAG, "✅ QtActivity erfolgreich gestartet!");
            
        } catch (ClassNotFoundException e) {
            showLog(TAG, "❌ QtActivity nicht gefunden: " + e.getMessage());
            showLog(TAG, "⚠️ Fallback: Python direkt starten (ohne Qt)");
            startPythonNative(this);
        } catch (Exception e) {
            showLog(TAG, "❌ Fehler beim Starten von QtActivity: " + e.getMessage());
            e.printStackTrace();
            startPythonNative(this);
        }
    }

    // ------------------------------------------------------------------------
    // FALLBACK: Python direkt starten (ohne Qt)
    // ------------------------------------------------------------------------
    public static void startPythonNative(Activity activity) {
        try {
            showLog(TAG, "🎾 Python native starten (Fallback ohne Qt)");
            if (activity == null) {
                showLog(TAG, "❌ Activity ist null!");
                return;
            }

            String app_root = activity.getFilesDir().getAbsolutePath() + "/qt";
            String entry_point = getEntryPointStatic(app_root);

            System.setProperty("ANDROID_ENTRYPOINT", entry_point);
            System.setProperty("ANDROID_ARGUMENT", app_root);
            System.setProperty("ANDROID_APP_PATH", app_root);
            System.setProperty("PYTHONHOME", app_root);
            System.setProperty("PYTHONPATH", app_root + ":" + app_root + "/lib");
            System.setProperty("PYTHONOPTIMIZE", "2");

            showLog(TAG, "✅ Environment gesetzt");

            try {
                Class<?> sdlClass = Class.forName("org.libsdl.app.SDLActivity");
                Method nativeRunMain = sdlClass.getMethod("nativeRunMain", String.class, String.class, Object[].class);
                nativeRunMain.invoke(null, app_root, entry_point, new Object[0]);
                showLog(TAG, "✅ Python erfolgreich über SDL gestartet");
            } catch (ClassNotFoundException e) {
                showLog(TAG, "❌ SDLActivity nicht gefunden: " + e.getMessage());
            } catch (NoSuchMethodException e) {
                showLog(TAG, "❌ nativeRunMain nicht gefunden: " + e.getMessage());
            } catch (Exception e) {
                showLog(TAG, "❌ Fehler beim Starten von Python: " + e.getMessage());
                e.printStackTrace();
            }

        } catch (Exception e) {
            showLog(TAG, "❌ Fehler beim Starten von Python: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------------
    // HILFSMETHODEN
    // ------------------------------------------------------------------------
    public void finishLoad() {
        // Leer – nur für Kompatibilität
    }

    public void toastError(final String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }

    // ------------------------------------------------------------------------
    // PERMISSIONS
    // ------------------------------------------------------------------------
    public interface PermissionsCallback {
        void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults);
    }

    private PermissionsCallback permissionCallback;
    private boolean havePermissionsCallback = false;

    public void addPermissionsCallback(PermissionsCallback callback) {
        permissionCallback = callback;
        havePermissionsCallback = true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (havePermissionsCallback) {
            permissionCallback.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public boolean checkCurrentPermission(String permission) {
        if (Build.VERSION.SDK_INT < 23) return true;
        try {
            Method method = Activity.class.getMethod("checkSelfPermission", String.class);
            Object result = method.invoke(this, permission);
            return Integer.parseInt(result.toString()) == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    public void requestPermissions(String[] permissions) {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                Method method = Activity.class.getMethod("requestPermissions", String[].class, int.class);
                method.invoke(this, permissions, 1);
            } catch (Exception e) {
                showLog(TAG, "requestPermissions fehlgeschlagen: " + e.getMessage());
            }
        }
    }

    public static void changeKeyboard(int inputType) {
        showLog(TAG, "changeKeyboard nicht implementiert für Qt");
    }
}