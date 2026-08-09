// core/main/src/main/java/com/rk/terminal/ui/screens/terminal/TerminalScreen.kt
package com.rk.terminal.ui.screens.terminal

import android.widget.Toast
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.widget.doOnTextChanged
import androidx.navigation.NavController
import com.google.android.material.R
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.libcommons.application
import com.rk.libcommons.child
import com.rk.libcommons.dpToPx
import com.rk.libcommons.localDir
import com.rk.libcommons.pendingCommand
import com.rk.resources.strings
import com.rk.settings.Settings
import com.meinname.ssh.Globals
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.routes.MainActivityRoutes
import com.rk.terminal.ui.screens.settings.SettingsCard
import com.rk.terminal.ui.screens.terminal.virtualkeys.VirtualKeysConstants
import com.rk.terminal.ui.screens.terminal.virtualkeys.VirtualKeysInfo
import com.rk.terminal.ui.screens.terminal.virtualkeys.VirtualKeysListener
import com.rk.terminal.ui.screens.terminal.virtualkeys.VirtualKeysView
import com.termux.terminal.TerminalColors
import com.termux.view.TerminalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.lang.ref.WeakReference
import java.util.Properties
import android.content.Intent
import com.rk.terminal.ui.screens.terminal.DialogMode

// ============================================================
// ★ VARIABLEN (werden von TerminalLogik verwendet)
// ============================================================
var terminalView = WeakReference<TerminalView?>(null)
var virtualKeysView = WeakReference<VirtualKeysView?>(null)
var pythonTerminalView = WeakReference<TerminalView?>(null)  // ★ ★ ★ NEU ★ ★ ★

var darkText = mutableStateOf(Settings.blackTextColor)
var bitmap = mutableStateOf<ImageBitmap?>(null)

private val file = application!!.filesDir.child("font.ttf")
private var font = (if (file.exists() && file.canRead()){
    Typeface.createFromFile(file)
}else{
    Typeface.MONOSPACE
})

var showToolbar = mutableStateOf(Settings.toolbar)
var showVirtualKeys = mutableStateOf(Settings.virtualKeys)
var showHorizontalToolbar = mutableStateOf(Settings.toolbar)

var wallAlpha by mutableFloatStateOf(Settings.wallTransparency)



// ★ ★ ★ LIVE-LOGS AUS python_sdl2.log LESEN ★ ★ ★
fun updatePythonLogs() {
    val logFile = File("/sdcard/python_sdl2.log")
    if (!logFile.exists()) {
        pythonTerminalView.get()?.currentSession?.write("⚠️ python_sdl2.log nicht gefunden\n")
        return
    }
    
    try {
        // Letzte 30 Zeilen lesen
        val logs = logFile.readLines()
        val lastLines = logs.takeLast(30)
        val session = pythonTerminalView.get()?.currentSession
        
        // ★ ★ ★ TERMINAL LEEREN UND NEUE LOGS ANZEIGEN ★ ★ ★
        session?.write("\u001b[2J\u001b[H")  // Clear Screen
        session?.write("🐍 Python Live-Logs (python_sdl2.log)\n")
        session?.write("========================================\n")
        lastLines.forEach { line ->
            session?.write("$line\n")
        }
        session?.write("========================================\n")
        session?.write("📡 Aktualisiert: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}\n")
        
    } catch (e: Exception) {
        pythonTerminalView.get()?.currentSession?.write("❌ Fehler: ${e.message}\n")
    }
}


/**
 * Hilfsfunktion zum Starten einer Python-Activity (Kivy oder Qt)
 */
private fun startPythonActivity(
    context: Context,
    targetClass: Class<*>,
    entrypoint: String,
    appRootSubDir: String
) {
    try {
        val intent = Intent(context, targetClass)
        
        val appRoot = "${context.filesDir.absolutePath}/$appRootSubDir"
        val mFilesDir = context.filesDir.absolutePath
        
        intent.putExtra("ANDROID_ENTRYPOINT", entrypoint)
        intent.putExtra("PYTHON_ENTRYPOINT", entrypoint)
        intent.putExtra("entrypoint", entrypoint)
        intent.putExtra("ANDROID_ARGUMENT", appRoot)
        intent.putExtra("ANDROID_APP_PATH", appRoot)
        intent.putExtra("ANDROID_PRIVATE", mFilesDir)
        intent.putExtra("ANDROID_UNPACK", appRoot)
        intent.putExtra("PYTHONHOME", appRoot)
        intent.putExtra("PYTHONPATH", "$appRoot:$appRoot/lib")
        intent.putExtra("PYTHONOPTIMIZE", "2")
        
        intent.putExtra("QT_QPA_PLATFORM", "android")
        intent.putExtra("QT_ANDROID_APP", "true")
        
        intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        context.startActivity(intent)
        
        Globals.showLog("Info", "✅ ${targetClass.simpleName} gestartet mit $entrypoint")
        Toast.makeText(context, "${targetClass.simpleName} wird gestartet...", Toast.LENGTH_SHORT).show()
        
    } catch (e: ClassNotFoundException) {
        Globals.showLog("Error", "❌ ${targetClass.simpleName} nicht gefunden: ${e.message}")
        Toast.makeText(context, "Python-Umgebung nicht gefunden!", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Globals.showLog("Error", "❌ Fehler beim Starten: ${e.message}")
        Toast.makeText(context, "Fehler: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

// ============================================================
// ★ FARB-FUNKTIONEN
// ============================================================
inline fun getViewColor(): Int{
    return if (darkText.value){
        Color.BLACK
    }else{
        Color.WHITE
    }
}

inline fun getComposeColor(): androidx.compose.ui.graphics.Color{
    return if (darkText.value){
        androidx.compose.ui.graphics.Color.Black
    }else{
        androidx.compose.ui.graphics.Color.White
    }
}

// ============================================================
// ★ FONT-FUNKTIONEN
// ============================================================
suspend fun setFont(typeface: Typeface) = withContext(Dispatchers.Main){
    font = typeface
    terminalView.get()?.apply {
        setTypeface(typeface)
        onScreenUpdated()
    }
}

// ============================================================
// ★ UI-KOMPONENTEN
// ============================================================
@Composable
fun BackgroundImage() {
    bitmap.value?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(wallAlpha)
                .zIndex(-1f)
        )
    }
}

@Composable
fun SetStatusBarTextColor(isDarkIcons: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    SideEffect {
        WindowCompat.getInsetsController(window, view)?.isAppearanceLightStatusBars = isDarkIcons
    }
}

@Composable
fun SelectableCard(
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        label = "containerColor"
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (selected) 8.dp else 2.dp
        ),
        enabled = enabled,
        onClick = onSelect
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            content()
        }
    }
}

// ============================================================
// ★ SESSION LOGIK
// ============================================================
fun changeSession(mainActivityActivity: MainActivity, session_id: String) {
    terminalView.get()?.apply {
        val client = TerminalBackEnd(this, mainActivityActivity)
        val session =
            mainActivityActivity.sessionBinder!!.getSession(session_id)
                ?: mainActivityActivity.sessionBinder!!.createSession(
                    session_id,
                    client,
                    mainActivityActivity,workingMode = Settings.working_Mode
                )
        session.updateTerminalSessionClient(client)
        attachSession(session)
        setTerminalViewClient(client)
        post {
            val typedValue = TypedValue()
            context.theme.resolveAttribute(
                R.attr.colorOnSurface,
                typedValue,
                true
            )
            keepScreenOn = true
            requestFocus()
            isFocusableInTouchMode = true
            
            // ★ ★ ★ FARBE AKTUALISIEREN ★ ★ ★
            val color = getViewColor()  // ← Hier wird die Farbe geholt
            mEmulator?.mColors?.mCurrentColors?.apply {
                set(256, color)  // Vordergrund
                set(258, color)  // Hintergrund
            }
        }
        virtualKeysView.get()?.apply {
            virtualKeysViewClient =
                terminalView.get()?.mTermSession?.let { VirtualKeysListener(it) }
        }
    }
    mainActivityActivity.sessionBinder!!.getService().currentSession.value = Pair(session_id,mainActivityActivity.sessionBinder!!.getService().sessionList[session_id]!!)
}

// ============================================================
// ★ CONSTANTS
// ============================================================
const val VIRTUAL_KEYS =
    ("[" + "\n  [" + "\n    \"ESC\"," + "\n    {" + "\n      \"key\": \"/\"," + "\n      \"popup\": \"\\\\\"" + "\n    }," + "\n    {" + "\n      \"key\": \"-\"," + "\n      \"popup\": \"|\"" + "\n    }," + "\n    \"HOME\"," + "\n    \"UP\"," + "\n    \"END\"," + "\n    \"PGUP\"" + "\n  ]," + "\n  [" + "\n    \"TAB\"," + "\n    \"CTRL\"," + "\n    \"ALT\"," + "\n    \"LEFT\"," + "\n    \"DOWN\"," + "\n    \"RIGHT\"," + "\n    \"PGDN\"" + "\n  ]" + "\n]")

// ============================================================
// ★ MAIN TERMINALSCREEN (GUI)
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    modifier: Modifier = Modifier,
    mainActivityActivity: MainActivity,
    navController: NavController
) {
    val context = LocalContext.current
    val isDarkMode = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()

    // ★ DOWNLOADER-STATE
    var progress by remember { mutableFloatStateOf(0f) }
    var progressText by remember { mutableStateOf("Initialisiere...") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var setupComplete by remember { mutableStateOf(Rootfs.isDownloaded.value) }

    // ★ DIALOG-STATE
    var showAddDialog by remember { mutableStateOf(false) }

    // ★ ★ ★ Globals für Debug ★ ★ ★
    if (Globals.DEBUG) {
        Globals.showLog("TerminalScreen", "🖥️ TerminalScreen gestartet")
        Globals.showLog("TerminalScreen", "📌 Rootfs: ${Globals.ROOTFS_TYPE}")
        Globals.showLog("TerminalScreen", "📌 Init: ${Globals.INIT_SCRIPT}")
        Globals.showLog("TerminalScreen", "📌 WorkingDir: ${Rootfs.getWorkingDir()}")
    }
    
    // TerminalScreen.kt - in der Composable

    // ★ ★ ★ LIVE-LOGS ALLE 1 SEKUNDEN AKTUALISIEREN ★ ★ ★
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000) // 1 Sekunde
            updatePythonLogs()
        }
    }

    // ★ DOWNLOADER STARTEN
    LaunchedEffect(Unit) {
        if (!Rootfs.isDownloaded.value && !isDownloading) {
            isDownloading = true
            runDownloaderSetup(
                context = context,
                onProgress = { prog, msg ->
                    progress = prog
                    progressText = msg
                },
                onComplete = { success ->
                    isDownloading = false
                    if (success) {
                        Rootfs.refreshStatus()
                        setupComplete = true
                    }
                },
                onError = { error ->
                    isDownloading = false
                    errorMessage = error
                }
            )
        }
    }

    // ★ TERMINAL-INIT
    LaunchedEffect(Unit){
        withContext(Dispatchers.IO){
            if (context.filesDir.child("background").exists().not()){
                darkText.value = !isDarkMode
            }else if (bitmap.value == null){
                val fullBitmap = BitmapFactory.decodeFile(context.filesDir.child("background").absolutePath)?.asImageBitmap()
                if (fullBitmap != null) bitmap.value = fullBitmap
            }
        }

        scope.launch(Dispatchers.Main){
            virtualKeysView.get()?.apply {
                virtualKeysViewClient =
                    terminalView.get()?.mTermSession?.let {
                        VirtualKeysListener(it)
                    }
                buttonTextColor = getViewColor()
                reload(
                    VirtualKeysInfo(
                        VIRTUAL_KEYS,
                        "",
                        VirtualKeysConstants.CONTROL_CHARS_ALIASES
                    )
                )
            }

            terminalView.get()?.apply {
                onScreenUpdated()
                mEmulator?.mColors?.mCurrentColors?.apply {
                    set(256, getViewColor())
                    set(258, getViewColor())
                }
            }
        }
    }

    Box {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp
        val drawerWidth = (screenWidthDp * 0.84).dp

        BackHandler(enabled = drawerState.isOpen) {
            scope.launch {
                drawerState.close()
            }
        }

        if (drawerState.isClosed){
            SetStatusBarTextColor(isDarkIcons = darkText.value)
        }else{
            SetStatusBarTextColor(isDarkIcons = !isDarkMode)
        }

        // ★ ★ ★ DIALOG ANZEIGEN ★ ★ ★
        if (showAddDialog && Rootfs.isDownloaded.value) {
            DialogMode(
                mainActivityActivity = mainActivityActivity,
                onDismiss = {
                    showAddDialog = false
                }
            )
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen || !(showToolbar.value && (LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE || showHorizontalToolbar.value)),
            drawerContent = {
                if (Rootfs.isDownloaded.value) {
                    ModalDrawerSheet(modifier = Modifier.width(drawerWidth)) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(strings.session),
                                    style = MaterialTheme.typography.titleLarge
                                )

                                Row {
                                    val keyboardController = LocalSoftwareKeyboardController.current
                                    IconButton(onClick = {
                                        navController.navigate(MainActivityRoutes.Settings.route)
                                        keyboardController?.hide()
                                    }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Settings,
                                            contentDescription = null
                                        )
                                    }

                                    IconButton(onClick = {
                                        showAddDialog = true
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null
                                        )
                                    }
                                }
                            }

                            mainActivityActivity.sessionBinder?.getService()?.sessionList?.keys?.toList()?.let {
                                LazyColumn {
                                    items(it) { session_id ->
                                        SelectableCard(
                                            selected = session_id == mainActivityActivity.sessionBinder?.getService()?.currentSession?.value?.first,
                                            onSelect = {
                                                changeSession(
                                                    mainActivityActivity,
                                                    session_id
                                                )
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = session_id,
                                                    style = MaterialTheme.typography.bodyLarge
                                                )

                                                if (session_id != mainActivityActivity.sessionBinder?.getService()?.currentSession?.value?.first) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                    IconButton(
                                                        onClick = {
                                                            mainActivityActivity.sessionBinder?.terminateSession(
                                                                session_id
                                                            )
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.Delete,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            content = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BackgroundImage()
                    val color = getComposeColor()
                    
                    // ★ ★ ★ HAUPT-COLUMN MIT SPLIT-VIEW ★ ★ ★
                    Column {
                        // ★ TOPAPPBAR
                        if (showToolbar.value && (LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE || showHorizontalToolbar.value)){
                            TopAppBar(
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent
                                ),
                                title = {
                                    Column {
                                        val rootfsType = Globals.getRootfsDisplayName()
                                        Text(
                                            text = if (Rootfs.isDownloaded.value) "Shell" else "Downloader",
                                            color = color
                                        )
                                        Text(
                                            style = MaterialTheme.typography.bodySmall,
                                            text = if (Rootfs.isDownloaded.value) {
                                                val sessionText = mainActivityActivity.sessionBinder?.getService()?.currentSession?.value?.first ?: ""
                                                "$sessionText (${rootfsType}) - ${Rootfs.getWorkingDirDisplay()}"
                                            } else {
                                                Globals.ROOTFS_FILE
                                            },
                                            color = color
                                        )
                                    }
                                },
                                navigationIcon = {
                                    IconButton(onClick = {
                                        scope.launch { drawerState.open() }
                                    }) {
                                        Icon(Icons.Default.Menu, null, tint = color)
                                    }
                                },
                                actions = {
                                    if (Globals.DEBUG) {
                                        IconButton(
                                            onClick = {
                                                startPythonActivity(
                                                    context = context,
                                                    targetClass = Class.forName("org.kivy.android.PythonQActivity"),
                                                    entrypoint = "main2.pyc",
                                                    appRootSubDir = "qt"
                                                )
                                            }
                                        ) {
                                            Text(
                                                text = " 🚆  ",
                                                fontSize = 24.sp,
                                                color = color
                                            )
                                        }
                                    }
                                    
                                    if (Rootfs.isDownloaded.value) {
                                        // Python-Button
                                        IconButton(
                                            onClick = {
                                                startPythonActivity(
                                                    context = context,
                                                    targetClass = Class.forName("org.kivy.android.PythonActivity"),
                                                    entrypoint = "main.pyc",
                                                    appRootSubDir = "sdl2"
                                                )
                                            }
                                        ) {
                                            Text(
                                                text = "🤖",
                                                fontSize = 24.sp,
                                                color = color
                                            )
                                        }

                                        // Add-Button
                                        IconButton(
                                            onClick = {
                                                showAddDialog = true
                                            }
                                        ) {
                                            Icon(Icons.Default.Add, null, tint = color)
                                        }
                                    }
                                }
                            )
                        }

                        val density = LocalDensity.current
                        
                        // ★ ★ ★ SPLIT-VIEW: 70% SHELL + 30% PYTHON OUTPUT ★ ★ ★
                        Column(
                            modifier = Modifier
                                .imePadding()
                                .navigationBarsPadding()
                                .padding(top = if (showToolbar.value){0.dp}else{
                                    with(density){
                                        TopAppBarDefaults.windowInsets.getTop(density).toDp()
                                    }
                                })
                                .weight(1f)
                        ) {
                            // ★ ★ ★ 1. HAUPT-TERMINAL (70%) ★ ★ ★
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(7f)
                            ) {
                                // TERMINAL ANSICHT
                                if (Rootfs.isDownloaded.value) {
                                    AndroidView(
                                        factory = { context ->
                                            TerminalView(context, null).apply {
                                                terminalView = WeakReference(this)
                                                setTextSize(
                                                    dpToPx(
                                                        Settings.terminal_font_size.toFloat(),
                                                        context
                                                    )
                                                )
                                                val client = TerminalBackEnd(this, mainActivityActivity)

                                                val session = if (pendingCommand != null) {
                                                    mainActivityActivity.sessionBinder!!.getService().currentSession.value = Pair(
                                                        pendingCommand!!.id, pendingCommand!!.workingMode)
                                                    mainActivityActivity.sessionBinder!!.getSession(
                                                        pendingCommand!!.id
                                                    )
                                                        ?: mainActivityActivity.sessionBinder!!.createSession(
                                                            pendingCommand!!.id,
                                                            client,
                                                            mainActivityActivity, workingMode = Settings.working_Mode
                                                        )
                                                } else {
                                                    mainActivityActivity.sessionBinder!!.getSession(
                                                        mainActivityActivity.sessionBinder!!.getService().currentSession.value.first
                                                    )
                                                        ?: mainActivityActivity.sessionBinder!!.createSession(
                                                            mainActivityActivity.sessionBinder!!.getService().currentSession.value.first,
                                                            client,
                                                            mainActivityActivity,workingMode = Settings.working_Mode
                                                        )
                                                }

                                                session.updateTerminalSessionClient(client)
                                                attachSession(session)
                                                setTerminalViewClient(client)
                                                setTypeface(font)

                                                post {
                                                    val color = getViewColor()
                                                    keepScreenOn = true
                                                    requestFocus()
                                                    isFocusableInTouchMode = true

                                                    mEmulator?.mColors?.mCurrentColors?.apply {
                                                        set(256, color)
                                                        set(258, color)
                                                    }

                                                    val colorsFile = localDir().child("colors.properties")
                                                    if (colorsFile.exists() && colorsFile.isFile){
                                                        val props = Properties()
                                                        FileInputStream(colorsFile).use { input ->
                                                            props.load(input)
                                                        }
                                                        TerminalColors.COLOR_SCHEME.updateWith(props)
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        update = { terminalView ->
                                            terminalView.onScreenUpdated()
                                            val color = getViewColor()
                                            terminalView.mEmulator?.mColors?.mCurrentColors?.apply {
                                                set(256, color)
                                                set(258, color)
                                            }
                                        },
                                    )
                                } else {
                                    // DOWNLOADER ANSICHT
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(80.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 6.dp
                                        )

                                        Spacer(modifier = Modifier.height(24.dp))

                                        Text(
                                            text = progressText,
                                            color = color,
                                            style = MaterialTheme.typography.headlineSmall,
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        LinearProgressIndicator(
                                            progress = progress,
                                            modifier = Modifier
                                                .fillMaxWidth(0.6f)
                                                .height(8.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = "${(progress * 100).toInt()}%",
                                            color = color.copy(alpha = 0.6f),
                                            style = MaterialTheme.typography.bodySmall
                                        )

                                        if (errorMessage != null) {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "❌ $errorMessage",
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.bodyMedium,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }

                            // ★ ★ ★ DIVIDER (Trennlinie) ★ ★ ★
                            Divider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                thickness = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // ★ ★ ★ 2. PYTHON-AUSGABE (30%) ★ ★ ★
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(3f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f))
                            ) {
                                // ★ ★ ★ KOPFZEILE ★ ★ ★
                                Text(
                                    text = "🐍 Python Output",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                                
                                // ★ ★ ★ PYTHON TERMINAL VIEW ★ ★ ★
                                AndroidView(
                                    factory = { ctx ->
                                        TerminalView(ctx, null).apply {
                                            pythonTerminalView = WeakReference(this)
                                            setTextSize(dpToPx(12f, ctx))
                                            
                                            // ★ ★ ★ PYTHON SESSION ERSTELLEN ★ ★ ★
                                            val pythonClient = TerminalBackEnd(this, mainActivityActivity)
                                            val pythonSession = PythonSession.createPythonSession(
                                                activity = mainActivityActivity,
                                                sessionClient = pythonClient,
                                                session_id = "python_log"
                                            )
                                            
                                            pythonSession.updateTerminalSessionClient(pythonClient)
                                            attachSession(pythonSession)
                                            setTerminalViewClient(pythonClient)
                                            setTypeface(font)
                                            
                                            // ★ ★ ★ TESTAUSGABE ★ ★ ★
                                            pythonSession.write("🐍 Python-Output bereit\n")
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}

// ★ ★ ★ DIVIDER-KOMPONENTE ★ ★ ★
@Composable
fun Divider(
    color: androidx.compose.ui.graphics.Color,
    thickness: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .height(thickness)
            .background(color)
    )
}