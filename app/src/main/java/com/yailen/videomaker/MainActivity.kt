package com.yailen.videomaker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFFFF7043))) {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

private const val SAMPLE = """Hola, soy Yailen y hoy vamos a aprender los colores.
El sol es amarillo. El cielo es azul.
La hierba es verde y la manzana es roja.
¡Muy bien! Nos vemos en el próximo video."""

@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(SAMPLE) }
    var title by remember { mutableStateOf("Aprende con Yailen") }
    var portrait by remember { mutableStateOf(true) }
    var speed by remember { mutableFloatStateOf(0.9f) }
    var pitch by remember { mutableFloatStateOf(1.1f) }
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var useAi by remember { mutableStateOf(false) }
    var character by remember { mutableStateOf("") }
    var showItems by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { images = it }

    LaunchedEffect(busy) {
        val win = (ctx as? Activity)?.window
        if (busy) win?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else win?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Yailen Video Maker", style = MaterialTheme.typography.headlineMedium)
        Text("Texto → video MP4 con voz, subtítulos y movimiento. Gratis, sin marcas de agua, sin internet.")

        OutlinedTextField(
            value = text, onValueChange = { text = it },
            label = { Text("Guion (cada frase = una escena)") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp), enabled = !busy
        )
        OutlinedTextField(
            value = title, onValueChange = { title = it },
            label = { Text("Título superior (déjalo vacío para quitarlo)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = portrait, onClick = { portrait = true }, label = { Text("Vertical 9:16") }, enabled = !busy)
            FilterChip(selected = !portrait, onClick = { portrait = false }, label = { Text("Horizontal 16:9") }, enabled = !busy)
        }

        Text("Velocidad de voz: ${"%.1f".format(speed)}x")
        Slider(value = speed, onValueChange = { speed = it }, valueRange = 0.5f..1.5f, enabled = !busy)
        Text("Tono de voz: ${"%.1f".format(pitch)}")
        Slider(value = pitch, onValueChange = { pitch = it }, valueRange = 0.6f..1.6f, enabled = !busy)

        OutlinedButton(onClick = { picker.launch("image/*") }, enabled = !busy) {
            Text(if (images.isEmpty()) "Agregar mis imágenes (opcional)" else "${images.size} imágenes elegidas · cambiar")
        }
        if (images.isNotEmpty()) TextButton(onClick = { images = emptyList() }, enabled = !busy) { Text("Quitar imágenes") }

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Switch(checked = showItems, onCheckedChange = { showItems = it }, enabled = !busy)
            Spacer(Modifier.width(8.dp))
            Text("Mostrar colores y objetos que nombre el texto (rojo → círculo rojo, manzana → 🍎)")
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Switch(checked = useAi, onCheckedChange = { useAi = it }, enabled = !busy && images.isEmpty())
            Spacer(Modifier.width(8.dp))
            Text("Crear las ilustraciones con IA gratis (Pollinations, necesita internet). Si subes tus imágenes, se usan esas.")
        }
        if (useAi && images.isEmpty()) OutlinedTextField(
            value = character, onValueChange = { character = it },
            label = { Text("Describe a tu personaje (ej: niña de 5 años, cabello negro, vestido amarillo)") },
            modifier = Modifier.fillMaxWidth(), enabled = !busy
        )

        Button(
            enabled = !busy && text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                busy = true; result = null; progress = 0f; status = "Iniciando…"
                scope.launch {
                    try {
                        result = VideoMaker.make(ctx, VideoConfig(text, title, images, portrait, speed, pitch, useAi, character, showItems)) { p, s ->
                            progress = p; status = s
                        }
                    } catch (e: Exception) {
                        status = "Error: ${e.message}"
                    } finally {
                        busy = false
                    }
                }
            }
        ) { Text("Crear video") }

        if (busy) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        if (status.isNotEmpty()) Text(status)

        result?.let { uri ->
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }) { Text("Ver video") }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                ctx.startActivity(Intent.createChooser(send, "Compartir video"))
            }) { Text("Compartir") }
        }
    }
}
