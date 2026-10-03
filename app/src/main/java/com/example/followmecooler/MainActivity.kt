
package com.example.followmecooler

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.Locale
import java.util.UUID


class MainActivity : ComponentActivity(), LocationListener {

    // ========================================================
    // BLUETOOTH
    // ========================================================

    private var bluetoothSocket: BluetoothSocket? = null

    private val sppUuid =
        UUID.fromString(
            "00001101-0000-1000-8000-00805F9B34FB"
        )

    private var bluetoothStatus by
    mutableStateOf("🔴 Desconectado")

    private var isConnected by
    mutableStateOf(false)


    // ========================================================
    // TERMINAL BLUETOOTH
    // ========================================================

    private var terminalText by
    mutableStateOf("")

    @Volatile
    private var bluetoothReaderRunning = false

    private val maxTerminalChars = 10000


    // ========================================================
    // GPS
    // ========================================================

    private lateinit var locationManager: LocationManager

    private var latitude by
    mutableStateOf<Double?>(null)

    private var longitude by
    mutableStateOf<Double?>(null)

    private var accuracy by
    mutableStateOf<Float?>(null)

    private var gpsStatus by
    mutableStateOf("⚪ GPS inactivo")

    private var followMode by
    mutableStateOf(false)


    // ========================================================
    // PERMISOS BLUETOOTH
    // ========================================================

    private val bluetoothPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.S
            ) {

                val connectGranted =
                    permissions[
                        Manifest.permission.BLUETOOTH_CONNECT
                    ] == true ||
                            ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) == PackageManager.PERMISSION_GRANTED

                val scanGranted =
                    permissions[
                        Manifest.permission.BLUETOOTH_SCAN
                    ] == true ||
                            ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.BLUETOOTH_SCAN
                            ) == PackageManager.PERMISSION_GRANTED

                bluetoothStatus =
                    if (
                        connectGranted &&
                        scanGranted
                    ) {
                        "🔴 Permisos Bluetooth concedidos"
                    } else {
                        "❌ Se necesitan permisos Bluetooth"
                    }
            }
        }


    // ========================================================
    // PERMISOS GPS
    // ========================================================

    private val locationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {

            if (hasLocationPermission()) {

                startLocationUpdates()

            } else {

                gpsStatus =
                    "❌ Permiso de ubicación rechazado"
            }
        }


    // ========================================================
    // ON CREATE
    // ========================================================

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        locationManager =
            getSystemService(
                Context.LOCATION_SERVICE
            ) as LocationManager

        requestBluetoothPermissions()

        setContent {

            MaterialTheme {

                CoolerApp(

                    bluetoothStatus =
                        bluetoothStatus,

                    isConnected =
                        isConnected,

                    gpsStatus =
                        gpsStatus,

                    latitude =
                        latitude,

                    longitude =
                        longitude,

                    accuracy =
                        accuracy,

                    followMode =
                        followMode,

                    terminalText =
                        terminalText,

                    onClearTerminal = {

                        terminalText = ""
                    },

                    onConnect = {

                        connectToHC05()
                    },

                    onManual = {

                        followMode = false

                        sendCommand(
                            "MODE:MANUAL"
                        )
                    },

                    // =================================================
                    // ORIENTACIÓN CORREGIDA SEGÚN ROBOT REAL
                    // =================================================

                    onForward = {

                        sendCommand(
                            "MOVE:100,0"
                        )
                    },

                    onBackward = {

                        sendCommand(
                            "MOVE:-100,0"
                        )
                    },

                    onLeft = {

                        sendCommand(
                            "MOVE:0,-100"
                        )
                    },

                    onRight = {

                        sendCommand(
                            "MOVE:0,100"
                        )
                    },

                    onFollow = {

                        startFollowMode()
                    },

                    onStop = {

                        followMode = false

                        sendCommand(
                            "STOP"
                        )
                    }
                )
            }
        }
    }


    // ========================================================
    // BLUETOOTH PERMISSIONS
    // ========================================================

    private fun hasBluetoothPermissions(): Boolean {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.S
        ) {

            return true
        }

        val connectGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

        val scanGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

        return connectGranted &&
                scanGranted
    }


    private fun requestBluetoothPermissions() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {

            if (!hasBluetoothPermissions()) {

                bluetoothPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    )
                )
            }
        }
    }


    // ========================================================
    // LOCATION PERMISSION
    // ========================================================

    private fun hasLocationPermission(): Boolean {

        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }


    private fun requestLocationPermission() {

        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }


    // ========================================================
    // GPS DEL TELÉFONO
    // ========================================================

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {

        if (!hasLocationPermission()) {

            requestLocationPermission()

            return
        }

        if (
            !locationManager.isProviderEnabled(
                LocationManager.GPS_PROVIDER
            )
        ) {

            gpsStatus =
                "❌ GPS del teléfono apagado"

            return
        }

        gpsStatus =
            "🟡 Esperando señal GPS..."

        try {

            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                this
            )

        } catch (e: Exception) {

            gpsStatus =
                "❌ Error GPS: " +
                        (e.message ?: "desconocido")
        }
    }


    // ========================================================
    // NUEVA UBICACIÓN DEL TELÉFONO
    // ========================================================

    override fun onLocationChanged(
        location: Location
    ) {

        latitude =
            location.latitude

        longitude =
            location.longitude

        accuracy =
            location.accuracy

        gpsStatus =
            "🟢 GPS activo"

        // En FOLLOW enviamos cada nueva ubicación
        // disponible al Arduino.

        if (
            followMode &&
            isConnected
        ) {

            sendGPSLocation(
                location.latitude,
                location.longitude,
                location.accuracy
            )
        }
    }


    // ========================================================
    // ENVIAR GPS DEL TELÉFONO
    // ========================================================

    private fun sendGPSLocation(
        lat: Double,
        lon: Double,
        acc: Float
    ) {

        val latText =
            String.format(
                Locale.US,
                "%.7f",
                lat
            )

        val lonText =
            String.format(
                Locale.US,
                "%.7f",
                lon
            )

        val accuracyText =
            String.format(
                Locale.US,
                "%.1f",
                acc
            )

        // Protocolo hacia Arduino:
        // GPS:latitud,longitud,precision_en_metros
        sendCommand(
            "GPS:$latText,$lonText,$accuracyText",
            showInStatus = false
        )
    }


    // ========================================================
    // FOLLOW MODE
    // ========================================================

    private fun startFollowMode() {

        if (!isConnected) {

            bluetoothStatus =
                "❌ Primero conecta el HC-05"

            return
        }

        if (!hasLocationPermission()) {

            requestLocationPermission()

            return
        }

        startLocationUpdates()

        followMode = true

        sendCommand(
            "MODE:FOLLOW"
        )

        // Si ya existe una ubicación,
        // enviarla inmediatamente.

        val lat = latitude
        val lon = longitude
        val acc = accuracy

        if (
            lat != null &&
            lon != null &&
            acc != null
        ) {

            sendGPSLocation(
                lat,
                lon,
                acc
            )
        }
    }


    // ========================================================
    // CONECTAR HC-05
    // ========================================================

    @SuppressLint("MissingPermission")
    private fun connectToHC05() {

        if (!hasBluetoothPermissions()) {

            bluetoothStatus =
                "❌ Faltan permisos Bluetooth"

            requestBluetoothPermissions()

            return
        }

        bluetoothStatus =
            "🟡 Buscando HC-05..."

        try {

            val bluetoothManager =
                getSystemService(
                    Context.BLUETOOTH_SERVICE
                ) as BluetoothManager

            val bluetoothAdapter =
                bluetoothManager.adapter

            if (bluetoothAdapter == null) {

                bluetoothStatus =
                    "❌ Este teléfono no tiene Bluetooth"

                return
            }

            if (!bluetoothAdapter.isEnabled) {

                bluetoothStatus =
                    "❌ Bluetooth está apagado"

                return
            }

            val pairedDevices =
                bluetoothAdapter.bondedDevices

            if (pairedDevices.isEmpty()) {

                bluetoothStatus =
                    "❌ No hay dispositivos emparejados"

                return
            }

            val hc05: BluetoothDevice? =
                pairedDevices.firstOrNull { device ->

                    val name =
                        try {

                            device.name

                        } catch (_: Exception) {

                            null
                        }

                    name?.contains(
                        "HC-05",
                        ignoreCase = true
                    ) == true
                }

            if (hc05 == null) {

                val deviceNames =
                    pairedDevices.joinToString(
                        ", "
                    ) {

                        try {

                            it.name
                                ?: "Dispositivo sin nombre"

                        } catch (_: Exception) {

                            "Desconocido"
                        }
                    }

                bluetoothStatus =
                    "❌ HC-05 no encontrado.\n" +
                            "Emparejados: $deviceNames"

                return
            }

            bluetoothStatus =
                "🟡 Conectando a ${hc05.name}..."

            Thread {

                try {

                    // Detener lector anterior antes
                    // de cerrar cualquier conexión.

                    bluetoothReaderRunning = false

                    try {

                        bluetoothSocket?.close()

                    } catch (_: Exception) {
                    }

                    bluetoothSocket = null

                    bluetoothAdapter.cancelDiscovery()

                    val socket =
                        hc05.createRfcommSocketToServiceRecord(
                            sppUuid
                        )

                    bluetoothSocket =
                        socket

                    socket.connect()

                    runOnUiThread {

                        isConnected = true

                        bluetoothStatus =
                            "🟢 Conectado a ${hc05.name}"

                        terminalText +=
                            "[APP] HC-05 conectado\n"
                    }

                    // Comenzar recepción Arduino -> Android

                    startBluetoothReader()

                } catch (e: Exception) {

                    bluetoothReaderRunning = false

                    try {

                        bluetoothSocket?.close()

                    } catch (_: Exception) {
                    }

                    bluetoothSocket = null

                    runOnUiThread {

                        isConnected = false

                        followMode = false

                        bluetoothStatus =
                            "❌ Error de conexión:\n" +
                                    (
                                            e.message
                                                ?: e.javaClass.simpleName
                                            )

                        appendTerminal(
                            "[APP] Error Bluetooth: ${
                                e.message
                                    ?: e.javaClass.simpleName
                            }\n"
                        )
                    }
                }

            }.start()

        } catch (e: SecurityException) {

            isConnected = false

            bluetoothStatus =
                "❌ Android bloqueó Bluetooth.\n" +
                        "Revisa los permisos."

        } catch (e: Exception) {

            isConnected = false

            bluetoothStatus =
                "❌ Error:\n" +
                        (
                                e.message
                                    ?: e.javaClass.simpleName
                                )
        }
    }


    // ========================================================
    // AGREGAR TEXTO A TERMINAL
    // ========================================================

    private fun appendTerminal(
        text: String
    ) {

        terminalText += text

        if (
            terminalText.length >
            maxTerminalChars
        ) {

            terminalText =
                terminalText.takeLast(
                    maxTerminalChars
                )
        }
    }


    // ========================================================
    // RECIBIR DATOS ARDUINO -> HC-05 -> ANDROID
    // ========================================================

    private fun startBluetoothReader() {

        if (bluetoothReaderRunning) {

            return
        }

        val socket =
            bluetoothSocket ?: return

        bluetoothReaderRunning = true

        Thread {

            try {

                val inputStream =
                    socket.inputStream

                val buffer =
                    ByteArray(1024)

                while (
                    socket.isConnected &&
                    bluetoothReaderRunning
                ) {

                    // Esta llamada espera hasta recibir
                    // información desde el HC-05.

                    val bytesRead =
                        inputStream.read(
                            buffer
                        )

                    if (bytesRead < 0) {

                        break
                    }

                    if (bytesRead > 0) {

                        val received =
                            String(
                                buffer,
                                0,
                                bytesRead,
                                Charsets.UTF_8
                            )

                        runOnUiThread {

                            appendTerminal(
                                received
                            )
                        }
                    }
                }

            } catch (e: IOException) {

                if (bluetoothReaderRunning) {

                    runOnUiThread {

                        appendTerminal(
                            "\n[RX] Conexión terminada: ${
                                e.message
                                    ?: "error de lectura"
                            }\n"
                        )
                    }
                }

            } catch (e: Exception) {

                if (bluetoothReaderRunning) {

                    runOnUiThread {

                        appendTerminal(
                            "\n[RX ERROR] ${
                                e.message
                                    ?: e.javaClass.simpleName
                            }\n"
                        )
                    }
                }

            } finally {

                bluetoothReaderRunning = false
            }

        }.start()
    }


    // ========================================================
    // ENVIAR COMANDOS ANDROID -> HC-05 -> ARDUINO
    // ========================================================

    private fun sendCommand(
        command: String,
        showInStatus: Boolean = true
    ) {

        val socket =
            bluetoothSocket

        if (
            socket == null ||
            !socket.isConnected
        ) {

            isConnected = false

            followMode = false

            bluetoothStatus =
                "❌ HC-05 no está conectado"

            return
        }

        Thread {

            try {

                val message =
                    "$command\n"

                socket.outputStream.write(
                    message.toByteArray(
                        Charsets.UTF_8
                    )
                )

                socket.outputStream.flush()

                if (showInStatus) {

                    runOnUiThread {

                        bluetoothStatus =
                            "🟢 HC-05 conectado\n" +
                                    "Enviado: $command"
                    }
                }

            } catch (e: IOException) {

                bluetoothReaderRunning = false

                try {

                    socket.close()

                } catch (_: Exception) {
                }

                bluetoothSocket =
                    null

                runOnUiThread {

                    isConnected = false

                    followMode = false

                    bluetoothStatus =
                        "❌ Se perdió la conexión:\n" +
                                (
                                        e.message
                                            ?: "Error desconocido"
                                        )

                    appendTerminal(
                        "\n[APP] Conexión Bluetooth perdida\n"
                    )
                }
            }

        }.start()
    }


    // ========================================================
    // CERRAR
    // ========================================================

    override fun onDestroy() {

        followMode = false

        bluetoothReaderRunning = false

        if (::locationManager.isInitialized) {

            try {

                locationManager.removeUpdates(
                    this
                )

            } catch (_: Exception) {
            }
        }

        try {

            bluetoothSocket?.close()

        } catch (_: Exception) {
        }

        bluetoothSocket = null

        super.onDestroy()
    }
}


// ============================================================
// INTERFAZ
// ============================================================

@Composable
fun CoolerApp(

    bluetoothStatus: String,

    isConnected: Boolean,

    gpsStatus: String,

    latitude: Double?,

    longitude: Double?,

    accuracy: Float?,

    followMode: Boolean,

    terminalText: String,

    onClearTerminal: () -> Unit,

    onConnect: () -> Unit,

    onManual: () -> Unit,

    onForward: () -> Unit,

    onBackward: () -> Unit,

    onLeft: () -> Unit,

    onRight: () -> Unit,

    onFollow: () -> Unit,

    onStop: () -> Unit
) {

    Column(

        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(24.dp),

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.spacedBy(16.dp)
    ) {

        // ====================================================
        // TITULO
        // ====================================================

        Text(
            text =
                "FOLLOW ME COOLER",

            style =
                MaterialTheme
                    .typography
                    .headlineMedium
        )

        HorizontalDivider()


        // ====================================================
        // BLUETOOTH
        // ====================================================

        Card(
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Column(
                modifier =
                    Modifier.padding(16.dp)
            ) {

                Text(
                    text =
                        "Bluetooth",

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Text(
                    text =
                        bluetoothStatus
                )
            }
        }


        Button(

            onClick =
                onConnect,

            enabled =
                !isConnected,

            modifier =
                Modifier.fillMaxWidth()
        ) {

            Text(

                if (isConnected)

                    "HC-05 CONECTADO"

                else

                    "CONECTAR HC-05"
            )
        }


        // ====================================================
        // GPS DEL TELÉFONO
        // ====================================================

        Card(
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Column(
                modifier =
                    Modifier.padding(16.dp)
            ) {

                Text(
                    text =
                        "GPS del teléfono",

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Text(
                    text =
                        gpsStatus
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Text(
                    text =
                        "Latitud: ${
                            latitude?.let {

                                String.format(
                                    Locale.US,
                                    "%.7f",
                                    it
                                )

                            } ?: "---"
                        }"
                )

                Text(
                    text =
                        "Longitud: ${
                            longitude?.let {

                                String.format(
                                    Locale.US,
                                    "%.7f",
                                    it
                                )

                            } ?: "---"
                        }"
                )

                Text(
                    text =
                        "Precisión: ${
                            accuracy?.let {

                                String.format(
                                    Locale.US,
                                    "%.1f m",
                                    it
                                )

                            } ?: "---"
                        }"
                )
                Spacer(
                    modifier =
                        Modifier.height(6.dp)
                )

                Text(
                    text =
                        when {
                            accuracy == null ->
                                "Calidad GPS: esperando medición"

                            accuracy <= 8.0f ->
                                "🟢 Calidad GPS apta para FOLLOW"

                            else ->
                                "🟠 Calidad GPS insuficiente para FOLLOW"
                        },

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall
                )

            }
        }


        // ====================================================
        // TERMINAL BLUETOOTH
        // ====================================================

        Card(
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Column(
                modifier =
                    Modifier.padding(16.dp)
            ) {

                Text(
                    text =
                        "Terminal Arduino",

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )

                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )

                Text(
                    text =
                        if (isConnected)
                            "🟢 Escuchando HC-05"
                        else
                            "⚪ HC-05 desconectado",

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                HorizontalDivider()

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                TerminalWindow(
                    terminalText =
                        terminalText
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                OutlinedButton(

                    onClick =
                        onClearTerminal,

                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        "LIMPIAR TERMINAL"
                    )
                }
            }
        }


        // ====================================================
        // FOLLOW
        // ====================================================

        Button(

            onClick =
                onFollow,

            enabled =
                isConnected &&
                        !followMode,

            modifier =
                Modifier.fillMaxWidth()
        ) {

            Text(

                if (followMode)

                    "📍 FOLLOW ACTIVO"

                else

                    "📍 INICIAR FOLLOW"
            )
        }


        if (followMode) {

            Text(
                text =
                    "🟢 El robot está en modo seguimiento",

                style =
                    MaterialTheme
                        .typography
                        .bodyMedium
            )
        }


        HorizontalDivider()


        // ====================================================
        // MANUAL
        // ====================================================

        Button(

            onClick =
                onManual,

            enabled =
                isConnected,

            modifier =
                Modifier.fillMaxWidth()
        ) {

            Text(
                "🎮 MODO MANUAL"
            )
        }


        // ====================================================
        // ADELANTE
        // ====================================================

        Button(

            onClick =
                onForward,

            enabled =
                isConnected &&
                        !followMode,

            modifier =
                Modifier.size(
                    width = 80.dp,
                    height = 60.dp
                )
        ) {

            Text("↑")
        }


        // ====================================================
        // IZQUIERDA / STOP / DERECHA
        // ====================================================

        Row(

            horizontalArrangement =
                Arrangement.spacedBy(10.dp),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Button(

                onClick =
                    onLeft,

                enabled =
                    isConnected &&
                            !followMode,

                modifier =
                    Modifier.size(
                        width = 80.dp,
                        height = 60.dp
                    )
            ) {

                Text("←")
            }


            Button(

                onClick =
                    onStop,

                enabled =
                    isConnected,

                colors =
                    ButtonDefaults
                        .buttonColors(
                            containerColor =
                                MaterialTheme
                                    .colorScheme
                                    .error
                        )
            ) {

                Text(
                    "STOP"
                )
            }


            Button(

                onClick =
                    onRight,

                enabled =
                    isConnected &&
                            !followMode,

                modifier =
                    Modifier.size(
                        width = 80.dp,
                        height = 60.dp
                    )
            ) {

                Text("→")
            }
        }


        // ====================================================
        // ATRÁS
        // ====================================================

        Button(

            onClick =
                onBackward,

            enabled =
                isConnected &&
                        !followMode,

            modifier =
                Modifier.size(
                    width = 80.dp,
                    height = 60.dp
                )
        ) {

            Text("↓")
        }


        Spacer(
            modifier =
                Modifier.height(16.dp)
        )


        Text(
            text =
                if (followMode)

                    "Modo automático GPS"

                else

                    "Control manual Bluetooth",

            style =
                MaterialTheme
                    .typography
                    .bodyMedium
        )


        Spacer(
            modifier =
                Modifier.height(30.dp)
        )
    }
}


// ============================================================
// VENTANA DE TERMINAL
// ============================================================

@Composable
fun TerminalWindow(
    terminalText: String
) {

    val scrollState =
        rememberScrollState()

    LaunchedEffect(
        terminalText
    ) {

        scrollState.animateScrollTo(
            scrollState.maxValue
        )
    }


    Surface(

        modifier =
            Modifier
                .fillMaxWidth()
                .height(220.dp),

        tonalElevation =
            2.dp,

        shape =
            MaterialTheme
                .shapes
                .small
    ) {

        Box(

            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(10.dp)
        ) {

            Text(

                text =
                    if (terminalText.isEmpty()) {

                        "Esperando datos del Arduino..."

                    } else {

                        terminalText
                    },

                fontFamily =
                    FontFamily.Monospace,

                style =
                    MaterialTheme
                        .typography
                        .bodySmall,

                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(
                            scrollState
                        )
            )
        }
    }
}
