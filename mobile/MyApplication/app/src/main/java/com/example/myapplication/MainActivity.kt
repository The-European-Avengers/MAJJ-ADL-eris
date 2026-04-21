package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
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
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.myapplication.data.api.RetrofitClient
import com.example.myapplication.data.local.CarbonDataRepository
import com.example.myapplication.data.local.TokenManager
import com.example.myapplication.data.model.RegisterRequest
import com.example.myapplication.data.model.LeaderboardEntry
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.utils.ChargingDetector
import com.example.myapplication.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    val carbonDataRepository = remember { CarbonDataRepository(context) }

    LaunchedEffect(Unit) {
        val updated = carbonDataRepository.refreshCacheFromPublicApi()
        Log.d("AppNavigation", "Startup carbon cache refresh: $updated")
    }
    
    // Si ya hay un token, empezamos en Home
    val startDestination = if (tokenManager.getToken() != null) "home" else "welcome"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("welcome") { 
            WelcomeScreen(
                onNavigateToLogin = { navController.navigate("login") },
                onNavigateToRegister = { navController.navigate("register") }
            ) 
        }
        composable("login") { 
            LoginScreen(
                onLoginSuccess = { 
                    navController.navigate("home") {
                        popUpTo("welcome") { inclusive = true }
                    }
                }
            ) 
        }
        composable("register") { 
            RegisterScreen(
                onRegisterSuccess = { 
                    navController.navigate("login") {
                        popUpTo("register") { inclusive = true }
                    }
                }
            ) 
        }
        composable("home") { 
            HomeScreen(
                onLogout = { 
                    tokenManager.clearToken()
                    navController.navigate("welcome") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                onActionSuccess = { streak, score ->
                    // Navegamos a la pantalla de recompensa pasando los puntos
                    navController.navigate("reward/$streak/$score")
                }
            ) 
        }
        composable(
            route = "reward/{streak}/{score}",
            arguments = listOf(
                navArgument("streak") { type = NavType.IntType },
                navArgument("score") { type = NavType.FloatType }
            )
        ) { backStackEntry ->
            val streak = backStackEntry.arguments?.getInt("streak") ?: 0
            val score = backStackEntry.arguments?.getFloat("score") ?: 0f
            
            RewardScreen(
                streak = streak,
                totalScore = score,
                onBackToHome = {
                    navController.popBackStack("home", inclusive = false)
                }
            )
        }
    }
}

@Composable
fun WelcomeScreen(onNavigateToLogin: () -> Unit, onNavigateToRegister: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        @Suppress("DEPRECATION")
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
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary))
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
            Text(
                text = "Login",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )

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
                                    val jwt = response.body()!!.accessToken
                                    tokenManager.saveToken(jwt)
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Sign Up",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )

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
                                val request = RegisterRequest(name, username, email, password, is_active)
                                val response = RetrofitClient.apiService.register(request)
                                
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Text("Sign Up", fontSize = 18.sp)
                }
            }
        }
    }
}

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
    val authToken = tokenManager.getToken()?.let { "Bearer $it" } ?: ""

    // Notificaciones y Permisos
    val notificationHelper = remember { NotificationHelper(context) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Permission denied for notifications", Toast.LENGTH_SHORT).show()
        }
    }

    // Pedir permiso al entrar si es Android 13+
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Detector de carga
    val chargingDetector = remember { ChargingDetector(context) }
    val isCharging by chargingDetector.observeChargingState().collectAsState(initial = chargingDetector.isCurrentlyCharging())

    // Efecto para lanzar notificación cuando se conecta el cargador
    LaunchedEffect(isCharging) {
        if (isCharging) {
            notificationHelper.showNotification(
                "¡Charger detected!",
                "You've plugged in your device. Is it a good time to charge it?"
            )
        }
    }

    var predictionValues by remember { mutableStateOf<List<Float>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isPredicting by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var cacheInfoText by remember { mutableStateOf("Cache: loading...") }
    var isRefreshingCache by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { predictor.close() }
    }

    LaunchedEffect(authToken) {
        if (authToken.isEmpty()) {
            isPredicting = false
            errorMessage = "Sesion no valida. Inicia sesion de nuevo."
            return@LaunchedEffect
        }
        try {
            val result = withContext(Dispatchers.IO) {
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
                        .bufferedReader()
                        .use { it.readText() }
                    records = processor.parseCsvToRecords(fallbackCsv)
                }

                val diagnostics = carbonDataRepository.getCacheDiagnostics()
                val refreshText = if (diagnostics.lastRefreshMs > 0L) {
                    val localDateTime = Instant.ofEpochMilli(diagnostics.lastRefreshMs)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()
                    localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                } else {
                    "never"
                }

                val cacheInfo = "Source: $dataSource | Rows: ${diagnostics.recordCount} | Gap(h): ${diagnostics.largestGapHours} | Last sync: $refreshText"
                Log.d(
                    "CACHE_DEBUG",
                    "source=$dataSource rows=${diagnostics.recordCount} first=${diagnostics.firstTimestamp} last=${diagnostics.lastTimestamp} gap=${diagnostics.largestGapHours}h avg=${diagnostics.avgCarbonIntensity} min=${diagnostics.minCarbonIntensity} max=${diagnostics.maxCarbonIntensity}"
                )

                val inputTensor = processor.processData(records)

                if (inputTensor != null) {
                    Pair(predictor.predict(inputTensor), cacheInfo)
                } else {
                    throw Exception("El historial es demasiado corto. Se requieren al menos 360 horas de datos.")
                }
            }
            predictionValues = result.first.toList()
            cacheInfoText = result.second
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
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
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Button(onClick = onLogout, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("Logout", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Welcome User!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Mostrar estado de carga
            Text(
                text = if (isCharging) "🔌 Charger connected" else "🔋 Battery (Disconnected)",
                fontSize = 16.sp,
                color = if (isCharging) Color(0xFF4CAF50) else Color.Gray,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(
                text = cacheInfoText,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )

            Button(
                onClick = {
                    isRefreshingCache = true
                    coroutineScope.launch {
                        try {
                            val success = carbonDataRepository.refreshCacheFromPublicApi(daysBack = 20)
                            if (success) {
                                Toast.makeText(context, "Cache refreshed!", Toast.LENGTH_SHORT).show()
                                val diagnostics = carbonDataRepository.getCacheDiagnostics()
                                val refreshText = if (diagnostics.lastRefreshMs > 0L) {
                                    Instant.ofEpochMilli(diagnostics.lastRefreshMs)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDateTime()
                                        .format(DateTimeFormatter.ofPattern("HH:mm"))
                                } else {
                                    "?"
                                }
                                cacheInfoText = "✓ Cache: ${diagnostics.recordCount} rows | Gap: ${diagnostics.largestGapHours}h | Sync: $refreshText"
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

            Spacer(modifier = Modifier.height(24.dp))

            // Área de la gráfica
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (isPredicting) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                } else if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "Error desconocido",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                } else if (predictionValues.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                    ) {
                        ProfessionalPredictionChart(values = predictionValues)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // BOTÓN DE ACCIÓN: Cargar más tarde
            Button(
                onClick = {
                    coroutineScope.launch {
                        isSubmitting = true
                        try {
                            val response = RetrofitClient.apiService.registerPredictionActivity(authToken, 50f)
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
                enabled = !isSubmitting && !isPredicting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.size(24.dp))
                } else {
                    Text("Delay Charging (+50 points)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun RewardScreen(
    streak: Int,
    totalScore: Float,
    onBackToHome: () -> Unit
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    val authToken = tokenManager.getToken()?.let { "Bearer $it" } ?: ""

    var leaderboard by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(authToken) {
        if (authToken.isNotEmpty()) {
            try {
                val response = RetrofitClient.apiService.getLeaderboard(authToken, 10)
                if (response.isSuccessful) {
                    leaderboard = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                // Silencioso o un Log
            } finally {
                isLoading = false
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Congratulations!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Streak: $streak days", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Total points: $totalScore pts", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text("Global Leaderboard", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Divider(modifier = Modifier.padding(vertical = 12.dp))

            if (isLoading) {
                CircularProgressIndicator()
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    itemsIndexed(leaderboard) { index, entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "#${index + 1}", 
                                    fontWeight = FontWeight.Bold, 
                                    modifier = Modifier.width(30.dp)
                                )
                                Text(text = entry.username, fontSize = 16.sp)
                            }
                            Row {
                                Text(text = "Streak: ${entry.current_streak}", modifier = Modifier.padding(end = 16.dp))
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

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onBackToHome,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Back to Home", fontSize = 16.sp)
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun ProfessionalPredictionChart(values: List<Float>) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    
    val axisColor = MaterialTheme.colorScheme.onSurface
    val gridColor = axisColor.copy(alpha = 0.1f)
    val lineColor = MaterialTheme.colorScheme.primary
    val labelTextStyle = TextStyle(color = axisColor.copy(alpha = 0.8f), fontSize = 10.sp)
    val titleTextStyle = TextStyle(color = axisColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val leftAreaWidth = with(density) { 60.dp.toPx() } 
        val bottomAreaHeight = with(density) { 50.dp.toPx() } 
        val topAreaHeight = with(density) { 20.dp.toPx() }
        val rightAreaWidth = with(density) { 20.dp.toPx() }

        val graphWidth = size.width - leftAreaWidth - rightAreaWidth
        val graphHeight = size.height - topAreaHeight - bottomAreaHeight

        if (values.isEmpty() || graphWidth <= 0 || graphHeight <= 0) return@Canvas

        val rawMaxY = values.maxOrNull() ?: 1f
        val rawMinY = values.minOrNull() ?: 0f
        val yBuffer = (rawMaxY - rawMinY) * 0.15f
        val minY = Math.max(0f, rawMinY - yBuffer)
        val maxY = rawMaxY + yBuffer
        val yRange = if (maxY == minY) 1f else maxY - minY

        val spaceX = if (values.size > 1) graphWidth / (values.size - 1) else 0f

        drawLine(
            color = axisColor,
            start = Offset(leftAreaWidth, topAreaHeight),
            end = Offset(leftAreaWidth, topAreaHeight + graphHeight),
            strokeWidth = 2f
        )
        drawLine(
            color = axisColor,
            start = Offset(leftAreaWidth, topAreaHeight + graphHeight),
            end = Offset(leftAreaWidth + graphWidth, topAreaHeight + graphHeight),
            strokeWidth = 2f
        )

        val gridLines = 5
        for (i in 0..gridLines) {
            val ratio = i.toFloat() / gridLines
            val yLabelCoord = topAreaHeight + graphHeight - (ratio * graphHeight)

            if (i > 0) {
                drawLine(
                    color = gridColor,
                    start = Offset(leftAreaWidth, yLabelCoord),
                    end = Offset(leftAreaWidth + graphWidth, yLabelCoord),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            }

            val value = minY + (ratio * yRange)
            val labelText = String.format(Locale.US, "%.1f", value)
            val textLayoutResult = textMeasurer.measure(AnnotatedString(labelText), style = labelTextStyle)
            
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(
                    x = leftAreaWidth - textLayoutResult.size.width - 8f,
                    y = yLabelCoord - textLayoutResult.size.height / 2
                )
            )
        }

        for (index in values.indices) {
            if (index % 4 == 0 || index == values.size - 1) {
                val xLabelCoord = leftAreaWidth + index * spaceX
                
                drawLine(
                    color = axisColor,
                    start = Offset(xLabelCoord, topAreaHeight + graphHeight),
                    end = Offset(xLabelCoord, topAreaHeight + graphHeight + 5f),
                    strokeWidth = 2f
                )

                val hourLabel = String.format(Locale.US, "%02dh", index)
                val textLayoutResult = textMeasurer.measure(AnnotatedString(hourLabel), style = labelTextStyle)

                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        x = xLabelCoord - textLayoutResult.size.width / 2,
                        y = topAreaHeight + graphHeight + 8f
                    )
                )
            }
        }

        val xTitleLayout = textMeasurer.measure(AnnotatedString("Day Time"), style = titleTextStyle)
        drawText(
            textLayoutResult = xTitleLayout,
            topLeft = Offset(
                x = leftAreaWidth + graphWidth / 2 - xTitleLayout.size.width / 2,
                y = size.height - xTitleLayout.size.height
            )
        )

        val yTitleLayout = textMeasurer.measure(AnnotatedString("gCO2eq / kWh"), style = titleTextStyle)
        drawContext.canvas.nativeCanvas.apply {
            save()
            rotate(-90f, 15f, topAreaHeight + graphHeight / 2)
            
            drawText(
                yTitleLayout,
                topLeft = Offset(
                    x = 15f - yTitleLayout.size.width / 2,
                    y = topAreaHeight + graphHeight / 2 - yTitleLayout.size.height / 2
                )
            )
            restore()
        }

        val path = Path()
        val filledPath = Path()
        
        filledPath.moveTo(leftAreaWidth, topAreaHeight + graphHeight)

        values.forEachIndexed { index, value ->
            val x = leftAreaWidth + index * spaceX
            val normalizedY = (value - minY) / yRange
            val y = topAreaHeight + graphHeight - (normalizedY * graphHeight)

            if (index == 0) {
                path.moveTo(x, y)
                filledPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                filledPath.lineTo(x, y)
            }

            drawCircle(color = lineColor, radius = 5f, center = Offset(x, y))
            drawCircle(color = Color.White, radius = 3f, center = Offset(x, y))
        }
        
        filledPath.lineTo(leftAreaWidth + graphWidth, topAreaHeight + graphHeight)
        filledPath.close()

        drawPath(
            path = filledPath,
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.2f), Color.Transparent),
                startY = topAreaHeight,
                endY = topAreaHeight + graphHeight
            )
        )

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 6f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
