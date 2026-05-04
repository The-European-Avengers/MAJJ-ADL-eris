package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.data.api.RetrofitClient
import com.example.myapplication.data.local.CarbonDataRepository
import com.example.myapplication.data.local.TokenManager
import com.example.myapplication.data.model.LeaderboardEntry
import com.example.myapplication.data.model.RegisterRequest
import com.example.myapplication.utils.NotificationInsightStore
import com.example.myapplication.utils.ChargingSessionStore
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.utils.ChargingDetector
import com.example.myapplication.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.util.Locale

private const val ROUTE_WELCOME = "welcome"
private const val ROUTE_LOGIN = "login"
private const val ROUTE_REGISTER = "register"
private const val ROUTE_APP_SHELL = "app-shell"
private const val ROUTE_HOME = "home"
private const val ROUTE_STREAK = "streak"
private const val ROUTE_LEADERBOARD = "leaderboard"
private const val USERNAME_HOME_AND_STREAK = "username1"
private const val USERNAME_HOME_AND_LEADERBOARD = "username2"
private const val USERNAME_FULL_MENU = "username4"

private data class BottomNavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomNavDestinations = listOf(
    BottomNavDestination(ROUTE_HOME, "Home", Icons.Filled.Home),
    BottomNavDestination(ROUTE_STREAK, "Streak", Icons.Filled.LocalFireDepartment),
    BottomNavDestination(ROUTE_LEADERBOARD, "Leaderboard", Icons.Filled.EmojiEvents)
)

private fun normalizeUsername(username: String?): String? = username?.trim()?.lowercase(Locale.ROOT)

private fun resolveCurrentUsername(tokenManager: TokenManager): String? {
    val usernameFromToken = tokenManager.getToken()
        ?.let { decodeUsernameFromJwt(it) }
        ?.takeIf { it.isNotBlank() }

    val usernameFromPrefs = tokenManager.getUsername()?.takeIf { it.isNotBlank() }

    return usernameFromToken ?: usernameFromPrefs
}

private fun bottomNavDestinationsFor(username: String?): List<BottomNavDestination> = when (normalizeUsername(username)) {
    USERNAME_HOME_AND_STREAK -> listOf(
        BottomNavDestination(ROUTE_HOME, "Home", Icons.Filled.Home),
        BottomNavDestination(ROUTE_STREAK, "Streak", Icons.Filled.LocalFireDepartment)
    )

    USERNAME_HOME_AND_LEADERBOARD -> listOf(
        BottomNavDestination(ROUTE_HOME, "Home", Icons.Filled.Home),
        BottomNavDestination(ROUTE_LEADERBOARD, "Leaderboard", Icons.Filled.EmojiEvents)
    )

    USERNAME_FULL_MENU -> bottomNavDestinations

    else -> listOf(
        BottomNavDestination(ROUTE_HOME, "Home", Icons.Filled.Home)
    )
}

/**
 * Decodes the `sub` (subject) claim from a JWT token without any external library.
 * A JWT is three base64url segments separated by '.'. The middle segment is the payload.
 */
fun decodeUsernameFromJwt(token: String): String? {
    return try {
        val payload = token.split(".").getOrNull(1) ?: return null
        val padded = payload
            .replace('-', '+')
            .replace('_', '/')
            .let { it + "=".repeat((4 - it.length % 4) % 4) }
        val decoded = android.util.Base64.decode(padded, android.util.Base64.DEFAULT)
        val json = String(decoded, Charsets.UTF_8)
        // Try "username" claim first (new tokens), ignore "sub" (it's the user ID)
        val usernameRegex = Regex(""""username"\s*:\s*"([^"]+)"""")
        usernameRegex.find(json)?.groupValues?.getOrNull(1)
    } catch (e: Exception) {
        null
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    NotificationPermissionRequester()
    val carbonDataRepository = remember { CarbonDataRepository(context) }

    LaunchedEffect(Unit) {
        val updated = carbonDataRepository.refreshCacheFromPublicApi()
        Log.d("AppNavigation", "Startup carbon cache refresh: $updated")
    }

    val startDestination = if (tokenManager.getToken() != null) ROUTE_APP_SHELL else ROUTE_WELCOME

    NavHost(navController = navController, startDestination = startDestination) {
        composable(ROUTE_WELCOME) {
            WelcomeScreen(
                onNavigateToLogin = { navController.navigate(ROUTE_LOGIN) },
                onNavigateToRegister = { navController.navigate(ROUTE_REGISTER) }
            )
        }
        composable(ROUTE_LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(ROUTE_APP_SHELL) {
                        popUpTo(ROUTE_WELCOME) { inclusive = true }
                    }
                }
            )
        }
        composable(ROUTE_REGISTER) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_REGISTER) { inclusive = true }
                    }
                }
            )
        }
        composable(ROUTE_APP_SHELL) {
            AuthenticatedAppShell(
                onLogout = {
                    tokenManager.clearToken()
                    navController.navigate(ROUTE_WELCOME) {
                        popUpTo(ROUTE_APP_SHELL) { inclusive = true }
                    }
                }
            )
        }
    }
}

@Composable
fun AuthenticatedAppShell(onLogout: () -> Unit) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    val navController = rememberNavController()
    var streak by rememberSaveable { mutableIntStateOf(0) }
    var totalScore by rememberSaveable { mutableFloatStateOf(0f) }
    var hasStreakData by rememberSaveable { mutableStateOf(false) }
    val username = remember { resolveCurrentUsername(tokenManager) }
    val availableDestinations = remember(username) { bottomNavDestinationsFor(username) }
    val actionDestinationRoute = remember(availableDestinations) {
        availableDestinations.firstOrNull { it.route != ROUTE_HOME }?.route
    }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route ?: ROUTE_HOME

    Scaffold(
        bottomBar = {
            NavigationBar {
                availableDestinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            if (currentRoute == destination.route) return@NavigationBarItem
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ROUTE_HOME) {
                HomeScreen(
                    onLogout = onLogout,
                    onActionSuccess = { updatedStreak, updatedScore ->
                        streak = updatedStreak
                        totalScore = updatedScore
                        hasStreakData = true
                        actionDestinationRoute?.let { targetRoute ->
                            navController.navigate(targetRoute) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
            composable(ROUTE_STREAK) {
                StreakScreen(
                    streak = streak,
                    totalScore = totalScore,
                    hasStreakData = hasStreakData,
                    onGoToHome = {
                        navController.navigate(ROUTE_HOME) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(ROUTE_LEADERBOARD) {
                LeaderboardScreen()
            }
        }
    }
}

@Composable
fun NotificationPermissionRequester() {
    val context = LocalContext.current
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        NotificationHelper.requestPermissionIfNeeded(context, permissionLauncher)
    }
}

@Composable
fun WelcomeScreen(onNavigateToLogin: () -> Unit, onNavigateToRegister: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.green_pause),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(150.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "GreenPause",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = onNavigateToLogin,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                Text("Sign In", fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onNavigateToRegister,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                )
            ) {
                Text("Sign Up", fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Login", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), enabled = !isLoading)
            Spacer(modifier = Modifier.height(32.dp))
            if (isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                Button(
                    onClick = {
                        if (username.isBlank() || password.isBlank()) {
                            Toast.makeText(context, "Please enter all details", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isLoading = true
                        scope.launch {
                            try {
                                val response = RetrofitClient.apiService.login(username, password)
                                if (response.isSuccessful && response.body() != null) {
                                    tokenManager.saveToken(response.body()!!.accessToken)
                                    tokenManager.saveUsername(username.trim())
                                    Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
                                    onLoginSuccess()
                                } else {
                                    Toast.makeText(context, "Invalid credentials", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Connection error", Toast.LENGTH_LONG).show()
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White)
                ) {
                    Text("Login", fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
fun RegisterScreen(onRegisterSuccess: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var is_active by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Sign Up", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), enabled = !isLoading)
            Spacer(modifier = Modifier.height(32.dp))
            if (isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                Button(
                    onClick = {
                        if (name.isBlank() || username.isBlank() || email.isBlank() || password.isBlank()) {
                            Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isLoading = true
                        scope.launch {
                            try {
                                val response = RetrofitClient.apiService.register(RegisterRequest(name, username, email, password, is_active))
                                if (response.isSuccessful) {
                                    Toast.makeText(context, "Registration successful!", Toast.LENGTH_SHORT).show()
                                    onRegisterSuccess()
                                } else {
                                    Toast.makeText(context, "Error: ${response.message()}", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Connection error", Toast.LENGTH_LONG).show()
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White)
                ) {
                    Text("Sign Up", fontSize = 18.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HomeScreen — chart always visible, insight card appears below it when charging
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    onActionSuccess: (Int, Float) -> Unit
) {
    val context = LocalContext.current
    val predictor = remember { CarbonModelPredictor(context) }
    val carbonDataRepository = remember { CarbonDataRepository(context) }
    val coroutineScope = rememberCoroutineScope()
    val tokenManager = remember { TokenManager(context) }
    val notificationInsightStore = remember { NotificationInsightStore(context) }
    val chargingSessionStore = remember { ChargingSessionStore(context) }
    val authToken = tokenManager.getToken()?.let { "Bearer $it" } ?: ""
    val username = remember {
        resolveCurrentUsername(tokenManager) ?: "User"
    }

    val notificationHelper = remember { NotificationHelper(context) }
    val chargingDetector = remember { ChargingDetector(context) }
    val isCharging by chargingDetector.observeChargingState()
        .collectAsState(initial = chargingDetector.isCurrentlyCharging())
    var previousChargingState by remember { mutableStateOf<Boolean?>(null) }

    var predictionValues  by remember { mutableStateOf<List<Float>>(emptyList()) }
    var chargingAnalysis  by remember { mutableStateOf<ChargingAnalysis?>(null) }
    var errorMessage      by remember { mutableStateOf<String?>(null) }
    var isPredicting      by remember { mutableStateOf(true) }
    var isSubmitting      by remember { mutableStateOf(false) }
    var isRefreshingCache by remember { mutableStateOf(false) }

    // Timestamps for real-session emission calculation
    var plugInTimeMs      by remember { mutableStateOf(chargingSessionStore.getPlugInTimeMs()) }
    var predictionHourRef by remember { mutableIntStateOf(chargingSessionStore.getPredictionHourRef() ?: LocalTime.now().hour) }
    var lastSession       by remember { mutableStateOf<ChargingSession?>(null) }

    LaunchedEffect(isCharging) {
        val wasCharging = previousChargingState
        previousChargingState = isCharging

        if (isCharging && plugInTimeMs == null) {
            plugInTimeMs = chargingSessionStore.getPlugInTimeMs()
            predictionHourRef = chargingSessionStore.getPredictionHourRef() ?: predictionHourRef
        }

        if (wasCharging == false && isCharging) {
            val now = System.currentTimeMillis()
            plugInTimeMs      = now
            predictionHourRef = LocalTime.now().hour
            chargingSessionStore.saveSessionStart(now, predictionHourRef)
            notificationHelper.showChargingWarningNotification()
        }

        if (wasCharging == true && !isCharging) {
            val plugIn = plugInTimeMs ?: chargingSessionStore.getPlugInTimeMs()
            val sessionPredictionHour = chargingSessionStore.getPredictionHourRef() ?: predictionHourRef
            if (plugIn != null && predictionValues.isNotEmpty()) {
                lastSession = calculateSessionEmission(
                    plugInMs            = plugIn,
                    plugOutMs           = System.currentTimeMillis(),
                    predictionValues    = predictionValues,
                    predictionStartHour = sessionPredictionHour
                )
            }
            plugInTimeMs = null
            chargingSessionStore.clearSessionStart()
        }
    }

    DisposableEffect(Unit) { onDispose { predictor.close() } }

    LaunchedEffect(authToken) {
        if (authToken.isEmpty()) {
            isPredicting = false
            errorMessage = "Sesion no valida. Inicia sesion de nuevo."
            return@LaunchedEffect
        }
        try {
            val predictions = withContext(Dispatchers.IO) {
                val processor = FeatureProcessor()
                var dataSource = "cache"
                var records = carbonDataRepository.getCachedRecords()

                if (records.size < 360) {
                    dataSource = "cache-refresh"
                    carbonDataRepository.refreshCacheFromPublicApi(daysBack = 20)
                    records = carbonDataRepository.getCachedRecords()
                }
                if (records.size < 360) {
                    dataSource = "asset-fallback"
                    val fallbackCsv = context.assets.open("DK-2025-hourly.csv")
                        .bufferedReader().use { it.readText() }
                    records = processor.parseCsvToRecords(fallbackCsv)
                }

                val diagnostics = carbonDataRepository.getCacheDiagnostics()
                Log.d("CACHE_DEBUG", "source=$dataSource rows=${diagnostics.recordCount} gap=${diagnostics.largestGapHours}h")

                val inputTensor = processor.processData(records)
                    ?: throw Exception("El historial es demasiado corto. Se requieren al menos 360 horas de datos.")

                val inferenceStartMs = System.currentTimeMillis()
                val predictions = predictor.predict(inputTensor)
                val inferenceMs = System.currentTimeMillis() - inferenceStartMs
                Log.d("ML_INFERENCE", "Inference time: ${inferenceMs}ms (${inferenceMs / 1000.0}s)")

                predictions
            }
            predictionValues  = predictions.toList()
            chargingAnalysis  = analyseChargingWindow(predictionValues)
            chargingAnalysis?.let { notificationInsightStore.saveAnalysisSnapshot(it) }
        } catch (e: Exception) {
            errorMessage = "Error: ${e.message}"
            Log.e("ML_Error", "Fallo durante el procesamiento o predicción", e)
        } finally {
            isPredicting = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // Single scrollable column — everything scrolls together
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.green_pause),
                    contentDescription = "User Avatar",
                    modifier = Modifier.size(60.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Button(
                    onClick = onLogout,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Logout", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Welcome, $username!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = if (isCharging) "🔌 Charger connected" else "🔋 Battery (Disconnected)",
                fontSize = 16.sp,
                color = if (isCharging) Color(0xFF4CAF50) else Color.Gray,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            Button(
                onClick = {
                    isRefreshingCache = true
                    coroutineScope.launch {
                        try {
                            val success = carbonDataRepository.refreshCacheFromPublicApi(daysBack = 20)
                            if (success) {
                                Toast.makeText(context, "Cache refreshed!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Cache refresh failed - check logs", Toast.LENGTH_LONG).show()
                            }
                        } finally {
                            isRefreshingCache = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                enabled = !isRefreshingCache && !isPredicting,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF4CAF50))
            ) {
                if (isRefreshingCache) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("🔄 Refresh Data", fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chart with fixed height — always rendered, scrolls with page
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, Color.LightGray.copy(alpha = 0.5f)
                )
            ) {
                when {
                    isPredicting -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    errorMessage != null -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = errorMessage ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                    predictionValues.isNotEmpty() -> ProfessionalPredictionChart(
                        values      = predictionValues,
                        startHour   = (LocalTime.now().hour + 1) % 24
                    )
                }
            }

            // Charging insight card — appears below chart when plugged in
            AnimatedVisibility(
                visible = isCharging && chargingAnalysis != null,
                enter = fadeIn() + expandVertically()
            ) {
                chargingAnalysis?.let { ChargingInsightCard(it) }
            }

            // Last session emission card — appears after unplugging
            AnimatedVisibility(
                visible = !isCharging && lastSession != null,
                enter = fadeIn() + expandVertically()
            ) {
                lastSession?.let { ChargingSessionCard(it) }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    coroutineScope.launch {
                        isSubmitting = true
                        try {
                            val response = RetrofitClient.apiService
                                .registerPredictionActivity(authToken, 50f)
                            if (response.isSuccessful && response.body() != null) {
                                val body = response.body()!!
                                onActionSuccess(body.current_streak, body.total_score)
                            } else {
                                errorMessage = "Server error: ${response.code()}"
                            }
                        } catch (e: Exception) {
                            errorMessage = "Network error while saving points."
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = isCharging && !isSubmitting && !isPredicting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text("Delay Charging (+50 points)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun StreakScreen(
    streak: Int,
    totalScore: Float,
    hasStreakData: Boolean,
    onGoToHome: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (hasStreakData) Arrangement.Top else Arrangement.Center
        ) {
            Text(
                text = "Streak",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (hasStreakData) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Current streak: $streak days",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "This view updates after you complete the charging action from Home.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No streak data yet",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Complete an action from Home to see your updated streak and points here.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onGoToHome) {
                    Text("Go to Home")
                }
            }
        }
    }
}

@Composable
fun LeaderboardScreen() {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    val authToken = tokenManager.getToken()?.let { "Bearer $it" } ?: ""

    var leaderboard by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(authToken) {
        if (authToken.isEmpty()) {
            isLoading = false
            errorMessage = "Session expired. Please sign in again."
            return@LaunchedEffect
        }

        isLoading = true
        errorMessage = null

        try {
            val response = RetrofitClient.apiService.getLeaderboard(authToken, 10)
            if (response.isSuccessful) {
                leaderboard = response.body() ?: emptyList()
            } else {
                errorMessage = "Could not load leaderboard right now."
            }
        } catch (_: Exception) {
            errorMessage = "Could not load leaderboard right now."
        } finally {
            isLoading = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(
                text = "Leaderboard",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "See how your score compares with other users.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            when {
                isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                errorMessage != null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                leaderboard.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No leaderboard data available yet.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    itemsIndexed(leaderboard) { index, entry ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 18.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "#${index + 1}",
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(36.dp)
                                    )
                                    Text(entry.username, fontSize = 16.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Streak: ${entry.current_streak}")
                                    Text(
                                        text = "${entry.total_score} pts",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
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

// ─────────────────────────────────────────────────────────────────────────────
// Chart (unchanged)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalTextApi::class)
@Composable
fun ProfessionalPredictionChart(values: List<Float>, startHour: Int = 0) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val axisColor = MaterialTheme.colorScheme.onSurface
    val gridColor = axisColor.copy(alpha = 0.1f)
    val lineColor = MaterialTheme.colorScheme.primary
    val labelTextStyle = TextStyle(color = axisColor.copy(alpha = 0.8f), fontSize = 10.sp)
    val titleTextStyle = TextStyle(color = axisColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)

    Canvas(modifier = Modifier.fillMaxSize().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)) {
        val leftAreaWidth    = with(density) { 48.dp.toPx() }
        val bottomAreaHeight = with(density) { 36.dp.toPx() }
        val topAreaHeight    = with(density) { 12.dp.toPx() }
        val rightAreaWidth   = with(density) { 8.dp.toPx() }

        val graphWidth  = size.width  - leftAreaWidth - rightAreaWidth
        val graphHeight = size.height - topAreaHeight - bottomAreaHeight

        if (values.isEmpty() || graphWidth <= 0 || graphHeight <= 0) return@Canvas

        val rawMaxY = values.maxOrNull() ?: 1f
        val rawMinY = values.minOrNull() ?: 0f
        val yBuffer = (rawMaxY - rawMinY) * 0.15f
        val minY = maxOf(0f, rawMinY - yBuffer)
        val maxY = rawMaxY + yBuffer
        val yRange = if (maxY == minY) 1f else maxY - minY
        val spaceX = if (values.size > 1) graphWidth / (values.size - 1) else 0f

        // Axes
        drawLine(axisColor, Offset(leftAreaWidth, topAreaHeight), Offset(leftAreaWidth, topAreaHeight + graphHeight), 2f)
        drawLine(axisColor, Offset(leftAreaWidth, topAreaHeight + graphHeight), Offset(leftAreaWidth + graphWidth, topAreaHeight + graphHeight), 2f)

        // Y-axis grid + labels
        for (i in 0..5) {
            val ratio = i.toFloat() / 5
            val yCoord = topAreaHeight + graphHeight - (ratio * graphHeight)
            if (i > 0) {
                drawLine(gridColor, Offset(leftAreaWidth, yCoord), Offset(leftAreaWidth + graphWidth, yCoord), 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
            }
            val label = textMeasurer.measure(AnnotatedString(String.format(Locale.US, "%.1f", minY + ratio * yRange)), style = labelTextStyle)
            drawText(label, topLeft = Offset(leftAreaWidth - label.size.width - 8f, yCoord - label.size.height / 2))
        }

        // X-axis labels — real clock times every 2 hours, wrapping midnight
        for (index in values.indices) {
            if (index % 2 == 0 || index == values.size - 1) {
                val xCoord = leftAreaWidth + index * spaceX
                drawLine(axisColor, Offset(xCoord, topAreaHeight + graphHeight), Offset(xCoord, topAreaHeight + graphHeight + 5f), 2f)
                val clockHour = (startHour + index) % 24
                val label = textMeasurer.measure(AnnotatedString(String.format(Locale.US, "%02d", clockHour)), style = labelTextStyle)
                drawText(label, topLeft = Offset(xCoord - label.size.width / 2, topAreaHeight + graphHeight + 8f))
            }
        }

        // X title
        val xTitle = textMeasurer.measure(AnnotatedString("Time of Day"), style = titleTextStyle)
        drawText(xTitle, topLeft = Offset(leftAreaWidth + graphWidth / 2 - xTitle.size.width / 2, size.height - xTitle.size.height))

        // Y title (rotated)
        val yTitle = textMeasurer.measure(AnnotatedString("gCO2eq / kWh"), style = titleTextStyle)
        drawContext.canvas.nativeCanvas.apply {
            save()
            rotate(-90f, 15f, topAreaHeight + graphHeight / 2)
            drawText(yTitle, topLeft = Offset(15f - yTitle.size.width / 2, topAreaHeight + graphHeight / 2 - yTitle.size.height / 2))
            restore()
        }

        // Line + fill
        val path = Path()
        val filledPath = Path().also { it.moveTo(leftAreaWidth, topAreaHeight + graphHeight) }

        values.forEachIndexed { index, value ->
            val x = leftAreaWidth + index * spaceX
            val y = topAreaHeight + graphHeight - ((value - minY) / yRange * graphHeight)
            if (index == 0) { path.moveTo(x, y); filledPath.lineTo(x, y) }
            else { path.lineTo(x, y); filledPath.lineTo(x, y) }
            drawCircle(lineColor, 5f, Offset(x, y))
            drawCircle(Color.White, 3f, Offset(x, y))
        }

        filledPath.lineTo(leftAreaWidth + graphWidth, topAreaHeight + graphHeight)
        filledPath.close()

        drawPath(filledPath, brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(lineColor.copy(alpha = 0.2f), Color.Transparent),
            startY = topAreaHeight, endY = topAreaHeight + graphHeight
        ))
        drawPath(path, lineColor, style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}