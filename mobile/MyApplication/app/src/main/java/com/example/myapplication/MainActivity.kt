package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.data.api.RetrofitClient
import com.example.myapplication.data.local.TokenManager
import com.example.myapplication.data.model.LoginRequest
import com.example.myapplication.data.model.RegisterRequest
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import androidx.compose.runtime.DisposableEffect

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
    
    // Si ya hay un token, empezamos en Home
    val startDestination = if (tokenManager.getToken() != null) "home" else "welcome"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("welcome") { WelcomeScreen(
            onNavigateToLogin = { navController.navigate("login") },
            onNavigateToRegister = { navController.navigate("register") }
        ) }
        composable("login") { LoginScreen(
            onLoginSuccess = { 
                navController.navigate("home") {
                    popUpTo("welcome") { inclusive = true }
                }
            }
        ) }
        composable("register") { RegisterScreen(
            onRegisterSuccess = { 
                navController.navigate("home") {
                    popUpTo("welcome") { inclusive = true }
                }
            }
        ) }
        composable("home") { HomeScreen(
            onLogout = { 
                tokenManager.clearToken()
                navController.navigate("welcome") {
                    popUpTo("home") { inclusive = true }
                }
            }
        ) }
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
                                val request = LoginRequest(username= username, password = password)
                                val response = RetrofitClient.apiService.login(username, password)

                                Log.d("LoginRequest", "Request: $request")

                                Log.d("LoginResponse", "Response: $response")

                                
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
fun HomeScreen(onLogout: () -> Unit) {

    val context = LocalContext.current
    
    // 1. Inicializar el predictor y mantenerlo en memoria durante el ciclo de vida de la pantalla
    val predictor = remember { CarbonModelPredictor(context) }
    
    // 2. Estados para manejar el resultado y la carga
    var predictionResult by remember { mutableStateOf<String?>(null) }
    var isPredicting by remember { mutableStateOf(false) }

    // 3. Asegurar que el modelo se cierra de la memoria cuando salimos de la pantalla
    DisposableEffect(Unit) {
        onDispose {
            predictor.close()
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.green_pause),
                contentDescription = "User Avatar",
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Welcome User!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Leaderboard",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                fontSize = 16.sp
            )


            // --- NUEVO CÓDIGO DEL MODELO ---
            if (isPredicting) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                Button(
                    onClick = {
                        isPredicting = true
                        
                        try {
                            // Crear datos ficticios (dummy) con la forma [1, 336, 7]
                            val timeSteps = 336
                            val features = 7
                            
                            // Rellenamos el array con un valor de ejemplo (ej. 0.5f)
                            val dummyInput = Array(1) { 
                                Array(timeSteps) { 
                                    FloatArray(features) { 0.5f } 
                                } 
                            }

                            // Ejecutar predicción
                            val result = predictor.predict(dummyInput)
                            predictionResult = result.joinToString(separator = ", ") { "%.2f".format(it) }
                            
                        } catch (e: Exception) {
                            predictionResult = "Error: ${e.message}"
                            Log.e("ML_Error", "Error al predecir", e)
                        } finally {
                            isPredicting = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = Color.White
                    )
                ) {
                    Text("Ejecutar Modelo CNN")
                }
            }

            // Mostrar el resultado si existe
            predictionResult?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Resultado: $it",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                Text("Logout")
            }
        }
    }
}