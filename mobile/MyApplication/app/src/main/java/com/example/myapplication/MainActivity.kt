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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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


import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Importaciones ya existentes...
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
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
    val predictor = remember { CarbonModelPredictor(context) }
    
    var predictionValues by remember { mutableStateOf<List<Float>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isPredicting by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose { predictor.close() }
    }

    LaunchedEffect(Unit) {
        try {
            val timeSteps = 336
            val features = 7
            val result = withContext(Dispatchers.Default) {
                // Datos simulados para visualizar la gráfica
                val dummyInput = Array(1) {
                    Array(timeSteps) {
                        FloatArray(features) { (10f + Math.random() * 40f).toFloat() }
                    }
                }
                predictor.predict(dummyInput)
            }
            predictionValues = result.toList()
        } catch (e: Exception) {
            errorMessage = "Error: ${e.message}"
            Log.e("ML_Error", "Error al predecir", e)
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

            // 1. Restauramos el Logo original
            Image(
                painter = painterResource(id = R.drawable.green_pause),
                contentDescription = "User Avatar",
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Restauramos el mensaje de bienvenida
            Text(
                text = "Welcome User!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 3. Área central para el modelo (ocupa el espacio disponible dinámicamente)
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
                        Text("Generando predicción...")
                    }
                } else if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "Error desconocido",
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (predictionValues.isNotEmpty()) {
                    // Contenedor del gráfico con fondo sutil
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                    ) {
                        // Llamada a la función del gráfico que ya tienes definida abajo
                        ProfessionalPredictionChart(values = predictionValues)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 4. Botón de Logout en su posición original
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(0.5f), // Ajusta el ancho para que quede estético
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                Text("Logout", fontSize = 16.sp)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun ProfessionalPredictionChart(values: List<Float>) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    
    // Configuración de estilos y colores
    val axisColor = MaterialTheme.colorScheme.onSurface
    val gridColor = axisColor.copy(alpha = 0.1f)
    val lineColor = MaterialTheme.colorScheme.primary
    val labelTextStyle = TextStyle(color = axisColor.copy(alpha = 0.8f), fontSize = 10.sp)
    val titleTextStyle = TextStyle(color = axisColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp) // Margen externo del gráfico
    ) {
        // 1. DEFINIR MÁRGENES DE LOS EJES (Espacio para etiquetas y títulos)
        // Usamos density para convertir DPs a Pixeles
        val leftAreaWidth = with(density) { 60.dp.toPx() }   // Espacio para valores Y y título Y
        val bottomAreaHeight = with(density) { 50.dp.toPx() } // Espacio para horas X y título X
        val topAreaHeight = with(density) { 20.dp.toPx() }
        val rightAreaWidth = with(density) { 20.dp.toPx() }

        // El área real donde se dibujará la línea
        val graphWidth = size.width - leftAreaWidth - rightAreaWidth
        val graphHeight = size.height - topAreaHeight - bottomAreaHeight

        if (values.isEmpty() || graphWidth <= 0 || graphHeight <= 0) return@Canvas

        // 2. ESCALADO DE DATOS (Añadimos márgenes para que la línea no toque los bordes)
        val rawMaxY = values.maxOrNull() ?: 1f
        val rawMinY = values.minOrNull() ?: 0f
        val yBuffer = (rawMaxY - rawMinY) * 0.15f // 15% de buffer arriba y abajo
        val minY = Math.max(0f, rawMinY - yBuffer) // No bajamos de 0 emisiones
        val maxY = rawMaxY + yBuffer
        val yRange = if (maxY == minY) 1f else maxY - minY

        val spaceX = graphWidth / (values.size - 1)

        // --- 3. DIBUJAR EJES Y CUADRÍCULA HORMIGONAL ---

        // Eje Y (Línea vertical izquierda)
        drawLine(
            color = axisColor,
            start = Offset(leftAreaWidth, topAreaHeight),
            end = Offset(leftAreaWidth, topAreaHeight + graphHeight),
            strokeWidth = 2f
        )
        // Eje X (Línea horizontal inferior)
        drawLine(
            color = axisColor,
            start = Offset(leftAreaWidth, topAreaHeight + graphHeight),
            end = Offset(leftAreaWidth + graphWidth, topAreaHeight + graphHeight),
            strokeWidth = 2f
        )

        // Cuadrícula Horizontal y Etiquetas del Eje Y
        val gridLines = 5 // Número de líneas horizontales de referencia
        for (i in 0..gridLines) {
            val ratio = i.toFloat() / gridLines
            // Calculamos la Y invertida (Canvas dibuja de arriba a abajo)
            val yLabelCoord = topAreaHeight + graphHeight - (ratio * graphHeight)

            // Dibujar línea de cuadrícula (punteada)
            if (i > 0) { // No dibujamos sobre el eje X
                drawLine(
                    color = gridColor,
                    start = Offset(leftAreaWidth, yLabelCoord),
                    end = Offset(leftAreaWidth + graphWidth, yLabelCoord),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            }

            // Etiqueta de valor (Emisiones)
            val value = minY + (ratio * yRange)
            val labelText = String.format(Locale.US, "%.1f", value)
            val textLayoutResult = textMeasurer.measure(AnnotatedString(labelText), style = labelTextStyle)
            
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(
                    x = leftAreaWidth - textLayoutResult.size.width - 8f, // 8px de separación
                    y = yLabelCoord - textLayoutResult.size.height / 2 // Centrado vertical
                )
            )
        }

        // --- 4. DIBUJAR ETIQUETAS DEL EJE X (HORAS) ---
        // Mostramos etiquetas cada 4 horas para no saturar (0, 4, 8... 23)
        for (index in values.indices) {
            if (index % 4 == 0 || index == values.size - 1) {
                val xLabelCoord = leftAreaWidth + index * spaceX
                
                // Dibujar pequeña marca (tick) en el eje
                drawLine(
                    color = axisColor,
                    start = Offset(xLabelCoord, topAreaHeight + graphHeight),
                    end = Offset(xLabelCoord, topAreaHeight + graphHeight + 5f),
                    strokeWidth = 2f
                )

                // Etiqueta de la hora
                val hourLabel = String.format(Locale.US, "%02dh", index)
                val textLayoutResult = textMeasurer.measure(AnnotatedString(hourLabel), style = labelTextStyle)

                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        x = xLabelCoord - textLayoutResult.size.width / 2, // Centrado horizontal
                        y = topAreaHeight + graphHeight + 8f // Abajo del eje
                    )
                )
            }
        }

        // --- 5. DIBUJAR TÍTULOS DE LOS EJES ---
        
        // Título Eje X
        val xTitleLayout = textMeasurer.measure(AnnotatedString("Day Time"), style = titleTextStyle)
        drawText(
            textLayoutResult = xTitleLayout,
            topLeft = Offset(
                x = leftAreaWidth + graphWidth / 2 - xTitleLayout.size.width / 2,
                y = size.height - xTitleLayout.size.height // Al final de la vista
            )
        )

        // Título Eje Y (Rotado)
        val yTitleLayout = textMeasurer.measure(AnnotatedString("gCO2eq / kWh"), style = titleTextStyle)
        // Para rotar texto en Canvas hay que usar el Canvas nativo de Android
        drawContext.canvas.nativeCanvas.apply {
            save()
            // Rotamos -90 grados alrededor del punto donde queremos el texto
            rotate(-90f, 15f, topAreaHeight + graphHeight / 2)
            
            // Dibujamos usando coordenadas relativas al punto de rotación
            drawText(
                yTitleLayout,
                topLeft = Offset(
                    x = 15f - yTitleLayout.size.width / 2,
                    y = topAreaHeight + graphHeight / 2 - yTitleLayout.size.height / 2
                )
            )
            restore()
        }


        // --- 6. DIBUJAR LA LÍNEA DE DATOS Y PUNTOS ---
        
        val path = Path()
        val filledPath = Path() // Para el degradado de fondo (opcional)
        
        filledPath.moveTo(leftAreaWidth, topAreaHeight + graphHeight) // Esquina inferior izquierda

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

            // Dibujar punto
            drawCircle(color = lineColor, radius = 5f, center = Offset(x, y))
            // Punto interior blanco para efecto "hueco" profesional
            drawCircle(color = Color.White, radius = 3f, center = Offset(x, y))
        }
        
        filledPath.lineTo(leftAreaWidth + graphWidth, topAreaHeight + graphHeight) // Esquina inferior derecha
        filledPath.close()

        // Dibujar relleno degradado (da mucha calidad visual)
        drawPath(
            path = filledPath,
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.2f), Color.Transparent),
                startY = topAreaHeight,
                endY = topAreaHeight + graphHeight
            )
        )

        // Dibujar la línea principal
        drawPath(
            path = path,
            color = lineColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 6f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
    }
}