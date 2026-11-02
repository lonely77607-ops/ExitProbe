package com.example.exitprobe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Probe(
    val tag: String,
    val text: String = "检测中…",
    val ms: Long? = null,
    val loading: Boolean = true
)

private suspend fun request(url: String): Pair<String, Long> = withContext(Dispatchers.IO) {
    val start = System.nanoTime()
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 5000
    conn.readTimeout = 5000
    conn.setRequestProperty("User-Agent", "curl/8.0")
    val body = conn.inputStream.bufferedReader().use { it.readText() }
    body to (System.nanoTime() - start) / 1_000_000
}

private suspend fun probeDomestic(): Probe = try {
    val (body, ms) = request("https://myip.ipip.net/json")
    val loc = JSONObject(body).getJSONObject("data").getJSONArray("location")
    val parts = (0 until loc.length()).map { loc.getString(it) }
        .filter { it.isNotBlank() && it != "中国" }.distinct()
    Probe("国内", parts.joinToString(" "), ms, false)
} catch (e: Exception) {
    Probe("国内", "检测失败", null, false)
}

private suspend fun probeForeign(): Probe = try {
    val (body, ms) = request("https://ipinfo.io/json")
    val j = JSONObject(body)
    val text = listOf(j.optString("country"), j.optString("city"), j.optString("org"))
        .filter { it.isNotBlank() }.joinToString(" ")
    Probe("国外", text, ms, false)
} catch (e: Exception) {
    Probe("国外", "检测失败", null, false)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { App() } }
    }
}

@Composable
fun App() {
    var cn by remember { mutableStateOf(Probe("国内")) }
    var intl by remember { mutableStateOf(Probe("国外")) }
    var round by remember { mutableIntStateOf(0) }

    LaunchedEffect(round) {
        cn = Probe("国内"); intl = Probe("国外")
        coroutineScope {
            launch { cn = probeDomestic() }
            launch { intl = probeForeign() }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFFAFAFA))
            .statusBarsPadding().padding(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("出口地址", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { round++ }) { Icon(Icons.Default.Refresh, "刷新") }
        }
        Spacer(Modifier.height(12.dp))
        ProbeRow(cn)
        Spacer(Modifier.height(10.dp))
        ProbeRow(intl)
    }
}

@Composable
fun ProbeRow(p: Probe) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF0F0F0)).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            p.tag, fontSize = 12.sp, color = Color.Gray,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(p.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        if (p.loading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            val ms = p.ms
            val (fg, bg) = when {
                ms == null -> Color(0xFF666666) to Color(0xFFE0E0E0)
                ms < 50 -> Color(0xFF1B9B4B) to Color(0xFFE3F5EA)
                ms < 150 -> Color(0xFFC77700) to Color(0xFFFBEBD7)
                else -> Color(0xFFD32F2F) to Color(0xFFFDE3E3)
            }
            Text(
                if (ms == null) "-- ms" else "$ms ms",
                color = fg, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(bg)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}
