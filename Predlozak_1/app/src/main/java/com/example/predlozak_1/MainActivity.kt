package com.example.predlozak_1

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.predlozak_1.ui.theme.Predlozak_1Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.floatPreferencesKey
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlin.math.sqrt

val Context.dataStore by preferencesDataStore(name = "user_prefs")
val REZULTAT_KEY = stringPreferencesKey("rezultat_bmi")
suspend fun spremiRezultat(context: Context, tekst: String) {
    context.dataStore.edit { preferences ->
        preferences[REZULTAT_KEY] = tekst
    }
}

fun dohvatiRezultat(context: Context): Flow<String> {
    return context.dataStore.data.map { preferences ->
        preferences[REZULTAT_KEY] ?: ""
    }
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermission(this)
        setContent {
            val navController = rememberNavController()
            Predlozak_1Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(navController = navController, startDestination = "main_screen",
                        modifier = Modifier.padding(innerPadding)
                    ) { composable("main_screen") {
                        MainScreen(navController = navController)
                    }
                        composable("step_counter") {
                            StepCounter(navController = navController)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserPreview(heightCm: Int, weightKg: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    context.dataStore
    val db = FirebaseFirestore.getInstance()

    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    val rezultat by dohvatiRezultat(context).collectAsState(initial = "")
    var progress by remember { mutableStateOf(0f) }
    var newWeightInput by remember { mutableStateOf("") }
    var bmiNapredakTekst by remember { mutableStateOf("") }

    val heightMeters = heightCm / 100f
    val bmi = weightKg / (heightMeters * heightMeters)
    val idealBmi = 21.7f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {

        Image(
            painter = painterResource(id = R.drawable.fitness),
            contentDescription = "Pozadinska slika",
            contentScale = ContentScale.Crop,
            alpha = 0.1f,
            modifier = Modifier.fillMaxSize()
        )

        Column(modifier = Modifier.padding(16.dp)) {

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.profile_pic),
                    contentDescription = "Profilna slika",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = "Pozdrav, Miljenko", fontSize = 18.sp)
                    Text(text = when {
                        bmi < 18.5 -> "Prenizak BMI"
                        bmi in 18.5..24.9 -> "Idealan BMI"
                        else -> "Previsok BMI"
                    }, fontSize = 14.sp, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                scope.launch {
                    isLoading = true
                    delay(2000L)
                    val razlika = kotlin.math.abs(bmi - idealBmi)
                    val rezultat = "Udaljeni ste %.1f od idealnog BMI-a.".format(razlika)

                    spremiRezultat(context, rezultat)
                    isLoading = false
                }
            }) {
                Text("Izračunaj razliku od idealnog BMI")
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Text(text = rezultat)
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = newWeightInput,
                onValueChange = { newWeightInput = it },
                label = { Text("Unesite novu težinu (kg)") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = {
                scope.launch {
                    isLoading = true
                    delay(1000L)
                    val novaTezina = newWeightInput.toFloatOrNull()
                    if (novaTezina != null && novaTezina > 0f) {
                        val podatak = hashMapOf(
                            "Visina" to heightCm,
                            "Tezina" to novaTezina,
                            "timestamp" to FieldValue.serverTimestamp()
                        )
                        db.collection("BMI")
                            .add(podatak)
                            .addOnSuccessListener {
                                bmiNapredakTekst = "Podatak spremljen u Firebase!"
                            }
                            .addOnFailureListener {
                                bmiNapredakTekst = "Greška pri spremanju."
                            }
                        val noviBmi = novaTezina / (heightMeters * heightMeters)
                        val ukupnoZaSpustiti = (bmi - idealBmi).coerceAtLeast(0f)
                        val vecSpusteno = (bmi - noviBmi).coerceAtLeast(0f)
                        progress = if (ukupnoZaSpustiti > 0f) {
                            (vecSpusteno / ukupnoZaSpustiti).coerceIn(0f, 1f)
                        } else 1f
                        bmiNapredakTekst = "Novi BMI: %.1f – Napredak: %.0f%%".format(noviBmi, progress * 100)
                    } else {
                        bmiNapredakTekst = "Unos nije ispravan."
                    }
                    isLoading = false
                }
            }) {
                Text("Ažuriraj napredak")
            }
            Button(onClick = {
                db.collection("BMI").get()
                    .addOnSuccessListener { documents ->
                        val tezine = documents.mapNotNull { it.getDouble("Tezina") }
                        val visine = documents.mapNotNull { it.getDouble("Visina") }

                        if (tezine.isNotEmpty() && visine.isNotEmpty()) {
                            val maxTezina = tezine.maxOrNull()!!
                            val minTezina = tezine.minOrNull()!!
                            val visina = visine.first() / 100.0  // koristi prvu visinu kao pretpostavku

                            val maxBmi = maxTezina / (visina * visina)
                            val minBmi = minTezina / (visina * visina)

                            bmiNapredakTekst =
                                "Najveća težina: %.1f kg (BMI: %.1f)\nNajmanja težina: %.1f kg (BMI: %.1f)"
                                    .format(maxTezina, maxBmi, minTezina, minBmi)
                        } else {
                            bmiNapredakTekst = "Nema dovoljno podataka u bazi."
                        }
                    }
                    .addOnFailureListener {
                        bmiNapredakTekst = "Greška pri dohvaćanju podataka."
                    }
            }) {
                Text("Analiziraj podatke")
            }

            Spacer(modifier = Modifier.height(8.dp))


                Text(bmiNapredakTekst)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                )


        }
    }
}

@Composable
fun MainScreen(navController: NavController, modifier: Modifier = Modifier) {
    Box( modifier = modifier
        .fillMaxSize()){
        UserPreview(
            heightCm = 191,
            weightKg = 100,
            modifier = Modifier.fillMaxSize()
        )
        Button(
            onClick = { navController.navigate("step_counter") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Text("Idi na brojač koraka")
        }}
}
@Composable
fun StepCounter(navController: NavController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val stepCount = remember { mutableStateOf(0) }
    val lastSavedThreshold = remember { mutableStateOf(0) }

    val firestore = Firebase.firestore
    val lastStepTime = remember { mutableStateOf(0L) }

    val sensorEventListener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    val x = it.values[0]
                    val y = it.values[1]
                    val z = it.values[2]
                    val magnitude = sqrt(x * x + y * y + z * z)
                    val currentTime = System.currentTimeMillis()
                    if (magnitude > 12f && (currentTime - lastStepTime.value) > 250) {
                        stepCount.value += 1
                        lastStepTime.value = currentTime
                        if (stepCount.value / 50 > lastSavedThreshold.value / 50) {
                            lastSavedThreshold.value = stepCount.value

                            showStepNotification(context, stepCount.value)

                            firestore.collection("Koraci").add(
                                hashMapOf(
                                    "koraci" to stepCount.value,
                                    "timestamp" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
    }


    DisposableEffect(Unit) {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sensorManager.unregisterListener(sensorEventListener)
        }
    }


    Box(modifier = modifier
        .fillMaxSize()
        .background(Color.White),) {
        Image(
            painter = painterResource(id = R.drawable.fitness),
            contentDescription = "Pozadinska slika",
            contentScale = ContentScale.Crop,
            alpha = 0.1f,
            modifier = Modifier.fillMaxSize()
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) {
            Text("Broj koraka: ${stepCount.value}",color = Color.Black)
        }
        Button(
            onClick = { navController.navigate("main_screen") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Text("Idi na glavni ekran")
        }
    }
}

fun showStepNotification(context: Context, stepCount: Int) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channelId = "steps_channel"
        val channelName = "Steps Notifications"
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }

    val notification = NotificationCompat.Builder(context, "steps_channel")
        .setSmallIcon(R.drawable.profile_pic)
        .setContentTitle("Bravo!!!")
        .setContentText("Cestitamo presli ste ${stepCount} koraka")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()

    notificationManager.notify(1, notification)
}

private fun requestPermission(context: Context) {
    val permissionsToRequest = mutableListOf<String>()
    // Dozvola za praćenje aktivnosti (koraci)
    if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION )
        != PackageManager.PERMISSION_GRANTED ) {
        permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS ) !=
            PackageManager.PERMISSION_GRANTED ) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
