package com.pipboyremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val glow = Color(0xFF9BFF98)
private val dim = Color(0xFF91B993)
private val background = Color(0xFF07100A)
private val panel = Color(0xFF102117)
private val panel2 = Color(0xFF16291C)
private val warn = Color(0xFFFFD47A)
private val line = Color(0xFF305B3D)

private enum class Screen(val title: String) {
    STAT("STAT"), ITEMS("ITEMS"), DATA("DATA"), MAP("MAP"), SYNC("SYNC")
}

internal enum class LinkState { NOT_PAIRED, PAIRED, LIVE, OFFLINE, DEMO }

/** In-memory session survives screen rotation; tokens are never written to saved instance state. */
internal class PipBoySession : ViewModel() {
    val tokenState = mutableStateOf("")
    val snapshotState = mutableStateOf<GameSnapshot?>(null)
    val linkStateState = mutableStateOf(LinkState.NOT_PAIRED)
    val messageState = mutableStateOf("Pair with the Windows bridge, or open the clearly labelled demo.")
    val lastFetchedState = mutableLongStateOf(0L)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PipBoyApp() }
    }
}

@Composable
private fun PipBoyApp() {
    var screen by rememberSaveable { mutableStateOf(Screen.SYNC) }
    var host by rememberSaveable { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val session: PipBoySession = viewModel()
    var token by session.tokenState
    var snapshot by session.snapshotState
    var linkState by session.linkStateState
    var message by session.messageState
    var lastFetchedMs by session.lastFetchedState
    var busy by remember { mutableStateOf(false) }
    var autoRefresh by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    fun refreshNow() {
        if (busy || token.isEmpty()) return
        val pairingToken = token
        val currentHost = host
        busy = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { BridgeClient(currentHost).snapshot(pairingToken) }
                // Ignore late responses after a server change or unpair.
                if (token == pairingToken && host == currentHost) {
                    snapshot = result
                    lastFetchedMs = System.currentTimeMillis()
                    linkState = if (result.isDemo) LinkState.DEMO else LinkState.LIVE
                    message = if (result.isDemo) "Mock bridge data — not from Fallout" else "Snapshot received from bridge"
                }
            } catch (e: Exception) {
                if (token == pairingToken && host == currentHost) {
                    linkState = LinkState.OFFLINE
                    message = "Refresh failed: ${e.message ?: "No response"}. Displayed data may be old."
                }
            } finally {
                busy = false
            }
        }
    }

    fun pairToPc(pairHost: String, manualCode: String? = null, qrSecret: String? = null) {
        if (busy || token.isNotEmpty()) return
        busy = true
        scope.launch {
            var shouldRefresh = false
            try {
                val newToken = withContext(Dispatchers.IO) {
                    val client = BridgeClient(pairHost)
                    if (qrSecret != null) client.pairQr(qrSecret)
                    else client.pair(manualCode ?: "")
                }
                if (token.isEmpty()) {
                    host = pairHost
                    token = newToken
                    code = ""
                    linkState = LinkState.PAIRED
                    message = "Paired. Fetching first snapshot…"
                    shouldRefresh = true
                }
            } catch (e: Exception) {
                if (token.isEmpty()) {
                    message = "Pair failed: ${e.message ?: "Unknown error"}"
                    linkState = LinkState.NOT_PAIRED
                }
            } finally {
                busy = false
            }
            if (shouldRefresh) refreshNow()
        }
    }

    fun scanPairingQr() {
        if (busy || token.isNotEmpty()) return
        busy = true
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        try {
            GmsBarcodeScanning.getClient(context, options).startScan()
                .addOnSuccessListener { result ->
                    try {
                        val invite = parsePairingInvitation(result.rawValue ?: "")
                        busy = false
                        pairToPc(invite.host, qrSecret = invite.secret)
                    } catch (e: Exception) {
                        busy = false
                        message = "QR rejected: ${e.message ?: "Not a valid invitation"}"
                    }
                }
                .addOnCanceledListener {
                    busy = false
                    message = "QR scan cancelled"
                }
                .addOnFailureListener { e ->
                    busy = false
                    message = "Scanner unavailable: ${e.message ?: "Google Play services required"}"
                }
        } catch (e: Exception) {
            busy = false
            message = "Scanner unavailable: ${e.message ?: "Unknown error"}"
        }
    }

    // Poll only while the app is foregrounded. Never poll when demo-only or unpaired.
    val refreshTick by rememberUpdatedState(newValue = { if (!busy) refreshNow() })
    LaunchedEffect(token, host, autoRefresh) {
        if (token.isNotEmpty() && autoRefresh) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    delay(5_000)
                    refreshTick()
                }
            }
        }
    }

    val palette = darkColorScheme(
        primary = glow, onPrimary = background, secondary = dim,
        background = background, onBackground = glow,
        surface = panel, onSurface = glow, surfaceVariant = panel2,
        onSurfaceVariant = dim, outline = line
    )
    MaterialTheme(colorScheme = palette, typography = Typography()) {
        Scaffold(
            containerColor = background,
            topBar = {
                Column(Modifier.fillMaxWidth().background(background).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("PIP-BOY", color = glow, fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                            Text("REMOTE  //  VLK-TTW COMPANION", color = dim,
                                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                        }
                        Text("READ ONLY", color = warn, fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(10.dp))
                    ConnectionStrip(linkState, snapshot, lastFetchedMs)
                }
            },
            bottomBar = {
                NavigationBar(containerColor = panel, tonalElevation = 0.dp) {
                    Screen.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = screen == destination,
                            onClick = { screen = destination },
                            icon = { Text(if (screen == destination) "◆" else "◇", color = if (screen == destination) glow else dim) },
                            label = { Text(destination.title, fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelSmall) },
                            alwaysShowLabel = true
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (screen) {
                    Screen.STAT -> StatScreen(snapshot)
                    Screen.ITEMS -> ItemsScreen(snapshot)
                    Screen.DATA -> DataScreen(snapshot)
                    Screen.MAP -> MapScreen(snapshot)
                    Screen.SYNC -> SyncScreen(
                        host = host,
                        onHostChange = {
                            if (it != host) {
                                host = it
                                token = ""
                                autoRefresh = false
                                snapshot = null
                                linkState = LinkState.NOT_PAIRED
                                lastFetchedMs = 0
                                message = "PC changed — pair again"
                            }
                        },
                        code = code, onCodeChange = { code = it.take(6).filter(Char::isDigit) },
                        paired = token.isNotEmpty(), linkState = linkState,
                        snapshot = snapshot, busy = busy, message = message,
                        autoRefresh = autoRefresh,
                        onAutoRefresh = { autoRefresh = it },
                        onPair = { pairToPc(host, manualCode = code) },
                        onScanQr = ::scanPairingQr,
                        onRefresh = ::refreshNow,
                        onUnpair = {
                            val oldToken = token
                            val oldHost = host
                            token = ""
                            autoRefresh = false
                            linkState = LinkState.NOT_PAIRED
                            snapshot = null
                            lastFetchedMs = 0
                            message = "Disconnected locally; notifying bridge…"
                            if (oldToken.isNotEmpty()) scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { BridgeClient(oldHost).unpair(oldToken) }
                                    message = "Unpaired. Bridge token revoked."
                                } catch (_: Exception) {
                                    message = "Local token cleared, but bridge revocation could not be confirmed. Restart bridge to invalidate it."
                                }
                            }
                        },
                        onDemo = {
                            try {
                                val json = context.assets.open("demo_snapshot.json").bufferedReader().use { it.readText() }
                                snapshot = parseGameSnapshot(JSONObject(json))
                                token = ""
                                autoRefresh = false
                                linkState = LinkState.DEMO
                                lastFetchedMs = System.currentTimeMillis()
                                message = "DEMO ONLY — simulated items and character"
                                screen = Screen.STAT
                            } catch (e: Exception) { message = "Demo unavailable: ${e.message}" }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionStrip(state: LinkState, snapshot: GameSnapshot?, fetchedMs: Long) {
    val label = when (state) {
        LinkState.NOT_PAIRED -> "NOT PAIRED"
        LinkState.PAIRED -> "PAIRED · WAITING FOR DATA"
        LinkState.LIVE -> if (snapshot?.isDemo == true) "MOCK SOURCE" else "LAST FETCH SUCCEEDED"
        LinkState.OFFLINE -> "OFFLINE · CACHED DATA"
        LinkState.DEMO -> "SIMULATED DEMO DATA"
    }
    val colour = when (state) {
        LinkState.LIVE -> glow
        LinkState.PAIRED, LinkState.OFFLINE, LinkState.DEMO -> warn
        LinkState.NOT_PAIRED -> dim
    }
    Surface(shape = RoundedCornerShape(8.dp), color = panel, border = BorderStroke(1.dp, line)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("●", color = colour)
            Spacer(Modifier.width(8.dp))
            Text(label, Modifier.weight(1f), color = colour, fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall, maxLines = 2)
            if (fetchedMs > 0) Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(fetchedMs)),
                color = dim, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(title, color = glow, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) Text(subtitle, color = dim, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = panel, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, line)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), content = content)
    }
}

private fun formatNumber(value: Double?): String = when {
    value == null -> "—"
    value % 1.0 == 0.0 -> value.toLong().toString()
    else -> String.format(Locale.US, "%.1f", value)
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = dim, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = glow, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Meter(label: String, value: Double?, max: Double?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ValueRow(label, "${formatNumber(value)} / ${formatNumber(max)}")
        LinearProgressIndicator(
            progress = { if (value == null || max == null || max <= 0) 0f else (value / max).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(7.dp),
            color = glow, trackColor = line, gapSize = 0.dp, drawStopIndicator = {}
        )
    }
}

@Composable
private fun EmptyData(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Panel {
            Text("NO GAME DATA", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = dim)
            Text("Open SYNC to pair or view the simulated demo.", color = warn, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatScreen(snapshot: GameSnapshot?) {
    if (snapshot == null) return EmptyData("No snapshot has been received yet.")
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Panel {
                SectionHeading("CHARACTER STATUS", snapshot.location ?: "Location unavailable")
                ValueRow("NAME", snapshot.characterName ?: "Unknown")
                ValueRow("LEVEL", snapshot.level?.toString() ?: "—")
                Meter("HP", snapshot.hp, snapshot.maxHp)
                Meter("AP", snapshot.ap, snapshot.maxAp)
                Meter("CARRY", snapshot.carryWeight, snapshot.maxCarryWeight)
                ValueRow("CAPS", formatNumber(snapshot.caps))
                ValueRow("RADS", formatNumber(snapshot.radiation))
            }
        }
        item {
            Panel {
                SectionHeading("S.P.E.C.I.A.L.")
                if (snapshot.special.isEmpty()) Text("No SPECIAL data supplied", color = dim)
                listOf("strength", "perception", "endurance", "charisma", "intelligence", "agility", "luck").forEach { key ->
                    ValueRow(key.uppercase(Locale.US), formatNumber(snapshot.special[key]))
                }
            }
        }
        item {
            Panel {
                SectionHeading("SURVIVAL", "Values are reported by the installed game adapter")
                ValueRow("HUNGER", formatNumber(snapshot.hunger))
                ValueRow("THIRST", formatNumber(snapshot.thirst))
                ValueRow("SLEEP", formatNumber(snapshot.sleep))
            }
        }
        item { ReadOnlyNotice() }
    }
}

@Composable
private fun ItemsScreen(snapshot: GameSnapshot?) {
    if (snapshot == null) return EmptyData("Inventory requires a snapshot.")
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("ALL") }
    var descendingCount by rememberSaveable { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val categories = listOf("ALL", "Weapons", "Apparel", "Aid", "Ammo", "Misc")
    val rows = remember(snapshot, search, category, descendingCount) {
        snapshot.inventory.filter {
            (category == "ALL" || it.category.equals(category, ignoreCase = true)) &&
                (search.isBlank() || it.name.contains(search, ignoreCase = true))
        }.let { items -> if (descendingCount) items.sortedWith(compareByDescending<GameItem> { it.count }.thenBy { it.name }) else items.sortedBy { it.name.lowercase(Locale.US) } }
    }
    val selected = snapshot.inventory.firstOrNull { it.id == selectedId }
    if (selectedId != null && selected != null) {
        ModalBottomSheet(onDismissRequest = { selectedId = null }, containerColor = panel2) {
            Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionHeading(selected.name, "${selected.category} · item ID: ${selected.id.ifEmpty { "unknown" }}")
                ValueRow("COUNT", selected.count.toString())
                ValueRow("WEIGHT", formatNumber(selected.weight))
                ValueRow("VALUE", formatNumber(selected.value))
                ValueRow("CONDITION", selected.condition?.let { "${(it * 100).roundToInt()}%" } ?: "Not reported")
                ValueRow("EQUIPPED", if (selected.equipped) "YES" else "NO")
                HorizontalDivider(color = line)
                Text("Read-only mode. Equip, use and drop are not implemented by this app.", color = warn,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SectionHeading("INVENTORY", "${rows.size} displayed · ${snapshot.inventory.size} total entries")
            OutlinedTextField(value = search, onValueChange = { search = it },
                label = { Text("Search items") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { group ->
                    FilterChip(selected = category == group, onClick = { category = group }, label = { Text(group) })
                }
            }
            TextButton(onClick = { descendingCount = !descendingCount }) {
                Text(if (descendingCount) "SORT: MOST FIRST ↓" else "SORT: NAME A–Z")
            }
        }
        if (rows.isEmpty()) item { Panel { Text("No matching inventory entries", color = dim) } }
        items(rows, key = { item -> "${item.id}:${item.name}:${item.category}" }) { item ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { selectedId = item.id },
                color = panel, shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, line)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, Modifier.weight(1f), color = glow, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text("×${item.count}", color = glow, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("${item.category.uppercase(Locale.US)}${if (item.equipped) "  ·  EQUIPPED" else ""}",
                        color = if (item.equipped) warn else dim, style = MaterialTheme.typography.labelSmall)
                    if (item.condition != null) {
                        Spacer(Modifier.height(7.dp))
                        LinearProgressIndicator(progress = { item.condition.toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(5.dp), color = glow,
                            trackColor = line, gapSize = 0.dp, drawStopIndicator = {})
                    }
                }
            }
        }
        item { ReadOnlyNotice() }
    }
}

@Composable
private fun DataScreen(snapshot: GameSnapshot?) {
    if (snapshot == null) return EmptyData("Quests and radio require a snapshot.")
    var subsection by rememberSaveable { mutableStateOf("QUESTS") }
    var expanded by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("QUESTS", "RADIO").forEach { tab ->
                FilterChip(selected = subsection == tab, onClick = { subsection = tab }, label = { Text(tab) })
            }
        }
        if (subsection == "RADIO") {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Panel {
                        SectionHeading("RADIO", "Station display only")
                        ValueRow("PLAYING", snapshot.radioStation ?: "None reported")
                        Text("Station switching is disabled until a separately verified write pathway exists.", color = warn)
                    }
                }
                item { ReadOnlyNotice() }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { SectionHeading("QUESTS", "${snapshot.quests.size} reported") }
                if (snapshot.quests.isEmpty()) item { Panel { Text("Quest list is empty or unsupported", color = dim) } }
                items(snapshot.quests, key = { "${it.id}:${it.title}" }) { quest ->
                    Panel {
                        Row(Modifier.fillMaxWidth().clickable { expanded = if (expanded == quest.id) null else quest.id },
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(quest.title, color = glow, fontWeight = FontWeight.SemiBold)
                                Text(quest.status.uppercase(Locale.US), color = if (quest.status == "active") warn else dim,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            Text(if (expanded == quest.id) "−" else "+", color = glow)
                        }
                        if (expanded == quest.id) {
                            Spacer(Modifier.height(10.dp))
                            if (quest.objectives.isEmpty()) Text("No objectives reported", color = dim)
                            quest.objectives.forEach { Text("• $it", color = dim, modifier = Modifier.padding(vertical = 4.dp)) }
                        }
                    }
                }
                item { ReadOnlyNotice() }
            }
        }
    }
}

@Composable
private fun MapScreen(snapshot: GameSnapshot?) {
    if (snapshot == null) return EmptyData("Map information requires a snapshot.")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Panel {
                SectionHeading("WORLD DATA", "A location list, not a rendered game map")
                ValueRow("WORLD", snapshot.world ?: "Unknown / interior")
                ValueRow("CELL", snapshot.location ?: "Unknown")
                Text("No game map texture is bundled. The adapter currently supplies marker names and metadata only.",
                    color = dim, style = MaterialTheme.typography.bodySmall)
            }
        }
        item { SectionHeading("MAP MARKERS", "${snapshot.markers.size} reported") }
        if (snapshot.markers.isEmpty()) item { Panel { Text("No markers reported", color = dim) } }
        items(snapshot.markers) { marker ->
            Panel {
                ValueRow(marker.name, when (marker.visited) { true -> "DISCOVERED"; false -> "UNDISCOVERED"; null -> "UNKNOWN" })
                Text("Fast travel: ${when (marker.canFastTravel) { true -> "game reports allowed"; false -> "unavailable"; null -> "unknown" }} (no phone action)",
                    color = dim, style = MaterialTheme.typography.bodySmall)
            }
        }
        item { ReadOnlyNotice() }
    }
}

@Composable
private fun SyncScreen(
    host: String, onHostChange: (String) -> Unit, code: String, onCodeChange: (String) -> Unit,
    paired: Boolean, linkState: LinkState, snapshot: GameSnapshot?, busy: Boolean, message: String,
    autoRefresh: Boolean, onAutoRefresh: (Boolean) -> Unit,
    onPair: () -> Unit, onScanQr: () -> Unit, onRefresh: () -> Unit, onUnpair: () -> Unit, onDemo: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Panel {
                SectionHeading("DESKTOP CONNECTION", "Your PC and phone must be on the same trusted Wi-Fi network")
                OutlinedTextField(
                    value = host, onValueChange = onHostChange, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Windows PC IPv4 address") }, placeholder = { Text("e.g. 192.168.1.50") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !busy && !paired
                )
                if (paired) Text("Unpair before changing the PC address.", color = dim,
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                if (!paired) {
                    Button(onClick = onScanQr, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("SCAN QR ON PC TITLE SCREEN")
                    }
                    Text("Recommended: one-time QR generated by the Windows bridge. Game-menu overlay requires borderless/windowed Fallout.",
                        color = dim, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = code, onValueChange = onCodeChange, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Six-digit pairing code") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        enabled = !busy
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onPair, enabled = !busy && code.length == 6, modifier = Modifier.fillMaxWidth()) { Text("PAIR WITH PC") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRefresh, enabled = !busy, modifier = Modifier.weight(1f)) { Text("REFRESH") }
                        OutlinedButton(onClick = onUnpair, enabled = !busy, modifier = Modifier.weight(1f)) { Text("UNPAIR") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Refresh every 5 seconds while app is open", Modifier.weight(1f), color = dim,
                            style = MaterialTheme.typography.bodySmall)
                        Switch(checked = autoRefresh, onCheckedChange = onAutoRefresh)
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(message, color = if (linkState == LinkState.OFFLINE) warn else dim,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Panel {
                SectionHeading("DATA SOURCE")
                ValueRow("SOURCE", snapshot?.source ?: "No snapshot")
                ValueRow("GAME TICK", snapshot?.gameTick?.toString() ?: "—")
                ValueRow("CONNECTION", linkState.name.replace('_', ' '))
                Text("A successful refresh confirms only that the bridge returned a snapshot; it does not prove a live game tick or save safety.",
                    color = dim, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Panel {
                SectionHeading("CAPABILITIES", "Writes are deliberately disabled in this application")
                if (snapshot == null) Text("Connect to inspect supported reads", color = dim)
                else {
                    listOf("inventoryRead" to "Inventory", "characterRead" to "Character",
                        "questRead" to "Quests", "mapRead" to "Map metadata", "radioRead" to "Radio status").forEach { (key, label) ->
                        ValueRow(label, if (snapshot.supports(key)) "AVAILABLE" else "NOT VERIFIED")
                    }
                }
            }
        }
        item {
            Panel {
                SectionHeading("OFFLINE UI DEMO", "Simulated data only — no Fallout connection")
                OutlinedButton(onClick = onDemo, enabled = !paired && !busy,
                    modifier = Modifier.fillMaxWidth()) { Text("OPEN DEMO") }
                if (paired) Text("Unpair first so the previous token can be revoked.",
                    color = dim, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Panel {
                SectionHeading("SECURITY LIMITATION")
                Text("This prototype uses unencrypted HTTP on a private LAN. Pairing codes and bearer tokens can be intercepted on that network. Do not use public Wi-Fi or expose the bridge to the internet.",
                    color = warn, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text("Tokens are retained only in app memory and require re-pairing after a cold start.", color = dim,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ReadOnlyNotice() {
    Text("READ-ONLY · NO EQUIP / USE / DROP / FAST TRAVEL COMMANDS",
        color = warn, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
}
