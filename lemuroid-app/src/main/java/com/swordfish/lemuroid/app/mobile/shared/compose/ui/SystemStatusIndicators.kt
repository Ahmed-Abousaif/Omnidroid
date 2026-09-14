package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.swordfish.lemuroid.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BatteryStatus(
    val percent: Int = 0,
    val charging: Boolean = false,
)

data class WifiStatus(
    val enabled: Boolean = false,
    val connected: Boolean = false,
    val level: Int = 0,
)

data class ConnectionStatus(
    val wifi: WifiStatus = WifiStatus(),
    val usingCellular: Boolean = false,
    val cellularLabel: String? = null,
)

@Composable
fun SystemStatusIndicators(
    modifier: Modifier = Modifier,
    gamepadConnected: Boolean = false,
) {
    val battery = rememberBatteryStatus()
    val connection = rememberConnectionStatus()
    val clock = rememberCurrentTime()

    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ConnectionStatusIcon(connection)
        if (gamepadConnected) {
            StatusDivider()
            Icon(
                imageVector = Icons.Filled.SportsEsports,
                contentDescription = stringResource(R.string.status_gamepad_connected),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        StatusDivider()
        BatteryStatusIcon(battery)
        StatusDivider()
        Text(
            text = clock,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun StatusDivider() {
    Box(
        modifier =
            Modifier
                .padding(horizontal = 2.dp)
                .width(1.dp)
                .height(12.dp)
                .background(LocalContentColor.current.copy(alpha = 0.28f)),
    )
}

@Composable
private fun rememberCurrentTime(): String {
    var time by remember { mutableStateOf(formatStatusTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            time = formatStatusTime()
            delay(15_000)
        }
    }
    return time
}

private const val LOW_BATTERY_PERCENT = 15

private fun formatStatusTime(): String {
    return SimpleDateFormat("h:mma", Locale.getDefault())
        .format(Date())
        .replace(" ", "")
        .uppercase(Locale.getDefault())
}

@Composable
private fun ConnectionStatusIcon(status: ConnectionStatus) {
    val cellularLabel = status.cellularLabel
    if (status.usingCellular && cellularLabel != null) {
        val description = stringResource(R.string.status_cellular_connected, cellularLabel)
        Text(
            text = cellularLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { contentDescription = description },
        )
        return
    }
    WifiStatusIcon(status.wifi)
}

@Composable
private fun WifiStatusIcon(status: WifiStatus) {
    val description =
        when {
            !status.enabled -> stringResource(R.string.status_wifi_off)
            !status.connected -> stringResource(R.string.status_wifi_disconnected)
            else -> stringResource(R.string.status_wifi_connected)
        }
    val active = MaterialTheme.colorScheme.primary
    val inactive = LocalContentColor.current.copy(alpha = 0.28f)

    WifiArcsIcon(
        level = status.level,
        connected = status.connected,
        enabled = status.enabled,
        activeColor = if (status.connected) active else LocalContentColor.current.copy(alpha = 0.55f),
        inactiveColor = inactive,
        contentDescription = description,
    )
}

@Composable
private fun BatteryStatusIcon(status: BatteryStatus) {
    val lowBattery = status.percent <= LOW_BATTERY_PERCENT && !status.charging
    val color =
        when {
            status.charging -> LibraryNeonGreen
            lowBattery -> BatteryLowRed
            else -> LocalContentColor.current
        }
    val description =
        when {
            status.charging -> stringResource(R.string.status_battery_charging, status.percent)
            lowBattery -> stringResource(R.string.status_battery_low, status.percent)
            else -> stringResource(R.string.status_battery_level, status.percent)
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BatteryLevelIcon(
            percent = status.percent,
            charging = status.charging,
            color = color,
            contentDescription = description,
        )
        Text(
            text = "${status.percent}%",
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun rememberBatteryStatus(): BatteryStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf(readBatteryStatus(context)) }

    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    status = parseBatteryIntent(intent)
                }
            }
        val sticky =
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        if (sticky != null) {
            status = parseBatteryIntent(sticky)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    return status
}

@Composable
private fun rememberConnectionStatus(): ConnectionStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf(readConnectionStatus(context, null)) }

    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val telephonyManager = context.getSystemService(TelephonyManager::class.java)
        var latestDisplayInfo = readTelephonyDisplayInfo(telephonyManager)

        val refresh = {
            context.mainExecutor.execute {
                status = readConnectionStatus(context, latestDisplayInfo)
            }
        }

        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    refresh()
                }

                override fun onLost(network: Network) {
                    refresh()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    refresh()
                }
            }

        val request =
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
        connectivityManager.registerNetworkCallback(request, callback)

        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    refresh()
                }
            }
        val filter =
            IntentFilter().apply {
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(WifiManager.RSSI_CHANGED_ACTION)
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val displayInfoCallback =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                    override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                        latestDisplayInfo = telephonyDisplayInfo
                        refresh()
                    }
                }
            } else {
                null
            }

        @Suppress("DEPRECATION")
        val phoneStateListener =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S
            ) {
                object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                        latestDisplayInfo = telephonyDisplayInfo
                        refresh()
                    }
                }
            } else {
                null
            }

        if (telephonyManager != null && displayInfoCallback != null) {
            runCatching {
                telephonyManager.registerTelephonyCallback(context.mainExecutor, displayInfoCallback)
            }
        } else if (telephonyManager != null && phoneStateListener != null) {
            runCatching {
                @Suppress("DEPRECATION")
                telephonyManager.listen(
                    phoneStateListener,
                    PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED,
                )
            }
        }

        status = readConnectionStatus(context, latestDisplayInfo)

        onDispose {
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            context.unregisterReceiver(receiver)
            if (telephonyManager != null && displayInfoCallback != null) {
                runCatching { telephonyManager.unregisterTelephonyCallback(displayInfoCallback) }
            }
            if (telephonyManager != null && phoneStateListener != null) {
                runCatching {
                    @Suppress("DEPRECATION")
                    telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
                }
            }
        }
    }

    return status
}

private fun readBatteryStatus(context: Context): BatteryStatus {
    val sticky =
        ContextCompat.registerReceiver(
            context,
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    return sticky?.let(::parseBatteryIntent) ?: BatteryStatus()
}

private fun parseBatteryIntent(intent: Intent): BatteryStatus {
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
    val percent = ((level.coerceAtLeast(0) * 100f) / scale).toInt().coerceIn(0, 100)
    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    val charging = plugged || status == BatteryManager.BATTERY_STATUS_CHARGING
    return BatteryStatus(percent = percent, charging = charging)
}

private fun readWifiStatus(context: Context): WifiStatus {
    val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
    val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    val enabled = wifiManager?.isWifiEnabled == true
    val wifiNetwork =
        connectivityManager?.allNetworks?.firstOrNull { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    val capabilities = wifiNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
    val connected = enabled && capabilities != null
    val rssi =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && capabilities != null) {
            capabilities.signalStrength
        } else {
            @Suppress("DEPRECATION")
            wifiManager?.connectionInfo?.rssi ?: -100
        }
    val level =
        if (connected) {
            @Suppress("DEPRECATION")
            WifiManager.calculateSignalLevel(rssi, 5)
        } else {
            0
        }
    return WifiStatus(enabled = enabled, connected = connected, level = level.coerceIn(0, 4))
}

private fun readConnectionStatus(
    context: Context,
    displayInfo: TelephonyDisplayInfo?,
): ConnectionStatus {
    val wifi = readWifiStatus(context)
    val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    val capabilities =
        connectivityManager?.activeNetwork?.let { network ->
            connectivityManager.getNetworkCapabilities(network)
        }
    val usingWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    val usingCellular =
        !usingWifi && capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
    val telephonyManager = context.getSystemService(TelephonyManager::class.java)
    val cellularLabel =
        if (usingCellular) {
            cellularGenerationLabel(displayInfo, readDataNetworkType(telephonyManager))
        } else {
            null
        }
    return ConnectionStatus(
        wifi = wifi,
        usingCellular = usingCellular,
        cellularLabel = cellularLabel,
    )
}

private fun readTelephonyDisplayInfo(telephonyManager: TelephonyManager?): TelephonyDisplayInfo? {
    if (telephonyManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        return null
    }
    return try {
        TelephonyManager::class.java
            .getMethod("getTelephonyDisplayInfo")
            .invoke(telephonyManager) as? TelephonyDisplayInfo
    } catch (_: Throwable) {
        null
    }
}

private fun readDataNetworkType(telephonyManager: TelephonyManager?): Int {
    if (telephonyManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        return TelephonyManager.NETWORK_TYPE_UNKNOWN
    }
    return try {
        telephonyManager.dataNetworkType
    } catch (_: SecurityException) {
        TelephonyManager.NETWORK_TYPE_UNKNOWN
    }
}

private fun cellularGenerationLabel(
    displayInfo: TelephonyDisplayInfo?,
    networkType: Int,
): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && displayInfo != null) {
        when (displayInfo.overrideNetworkType) {
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
            -> return "5G"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA,
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO,
            -> return "LTE"
        }
        @Suppress("DEPRECATION")
        if (displayInfo.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE) {
            return "5G"
        }
        return mapNetworkType(displayInfo.networkType)
    }
    return mapNetworkType(networkType)
}

private fun mapNetworkType(networkType: Int): String {
    return when (networkType) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_HSPAP,
        TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_EHRPD,
        -> "4G"
        TelephonyManager.NETWORK_TYPE_UMTS,
        TelephonyManager.NETWORK_TYPE_TD_SCDMA,
        TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A,
        TelephonyManager.NETWORK_TYPE_EVDO_B,
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        -> "3G"
        TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_CDMA,
        TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_IDEN,
        TelephonyManager.NETWORK_TYPE_GSM,
        -> "2G"
        else -> "4G"
    }
}
