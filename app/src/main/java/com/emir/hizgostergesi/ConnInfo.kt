package com.emir.hizgostergesi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager

/**
 * Şu anki bağlantı türünü izler: 5G / 4.5G / 4G / 3G / 2G / Wi-Fi (bant) / Ethernet, VPN varsa belirtir.
 * İnternete bağlanmaz; sadece Android'in bağlantı bilgisini okur.
 */
object ConnInfo {
    /** Ekranda gösterilecek metin, örn. "5G", "Wi-Fi 5GHz · VPN". Bağlantı yoksa boş. */
    @Volatile
    var label = ""

    /** İstatistik anahtarı: "5G", "4.5G", "4G", "3G", "2G", "Mobil", "Wi-Fi", "Ethernet", "Diğer". */
    @Volatile
    var kind = ""

    val CELL_KINDS = setOf("5G", "4.5G", "4G", "3G", "2G", "Mobil")

    /** VPN açık mı, asıl hat mobil mi? (VPN'li trafiğin çift sayılmaması için) */
    @Volatile
    var vpn = false

    @Volatile
    var cellular = false

    private var cellType = 0
    private var cellOverride = 0
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private var telCallback: Any? = null

    fun start(c: Context) {
        val app = c.applicationContext
        val cm = app.getSystemService(ConnectivityManager::class.java)
        if (netCallback == null) {
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = update(app, caps)
                override fun onLost(network: Network) = refresh(app)
            }
            try {
                cm.registerDefaultNetworkCallback(cb)
                netCallback = cb
            } catch (e: Exception) {
                // izlenemiyorsa periyodik refresh yeterli
            }
        }
        registerTelephony(app)
        refresh(app)
    }

    fun stop(c: Context) {
        val app = c.applicationContext
        netCallback?.let {
            try {
                app.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it)
            } catch (e: Exception) {
            }
        }
        netCallback = null
        if (Build.VERSION.SDK_INT >= 31) {
            (telCallback as? TelephonyCallback)?.let {
                try {
                    app.getSystemService(TelephonyManager::class.java).unregisterTelephonyCallback(it)
                } catch (e: Exception) {
                }
            }
        }
        telCallback = null
    }

    /** 5G (NSA) ve 4.5G'yi ayırt etmek için ekran bilgisi dinleyicisi. İzin yoksa sessizce atlanır. */
    private fun registerTelephony(app: Context) {
        if (telCallback != null || Build.VERSION.SDK_INT < 31) return
        try {
            val tm = app.getSystemService(TelephonyManager::class.java)
            val cb = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                    cellType = info.networkType
                    cellOverride = info.overrideNetworkType
                    refresh(app)
                }
            }
            tm.registerTelephonyCallback(app.mainExecutor, cb)
            telCallback = cb
        } catch (e: Exception) {
            // izin verilmemiş olabilir
        }
    }

    fun refresh(c: Context) {
        val app = c.applicationContext
        registerTelephony(app)
        try {
            val cm = app.getSystemService(ConnectivityManager::class.java)
            update(app, cm.getNetworkCapabilities(cm.activeNetwork))
        } catch (e: Exception) {
            label = ""
            kind = ""
            vpn = false
            cellular = false
        }
    }

    private fun update(c: Context, caps: NetworkCapabilities?) {
        if (caps == null) {
            label = ""
            kind = ""
            vpn = false
            cellular = false
            return
        }
        vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        var l: String
        val k: String
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                k = "Wi-Fi"
                l = "Wi-Fi" + wifiBand(caps)
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                k = cellGeneration(c)
                l = k
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                k = "Ethernet"
                l = k
            }
            else -> {
                k = "Diğer"
                l = ""
            }
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) l = if (l.isEmpty()) "VPN" else "$l · VPN"
        label = l
        kind = k
    }

    private fun wifiBand(caps: NetworkCapabilities): String {
        val info = caps.transportInfo as? WifiInfo ?: return ""
        val f = info.frequency
        return when {
            f in 2400..2500 -> " 2.4GHz"
            f in 4900..5900 -> " 5GHz"
            f >= 5925 -> " 6GHz"
            else -> ""
        }
    }

    private fun cellGeneration(c: Context): String {
        if (cellOverride == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
            cellOverride == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED
        ) return "5G"
        if (cellOverride == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO ||
            cellOverride == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA
        ) return "4.5G"

        var type = cellType
        if (type == 0 && c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            type = try {
                c.getSystemService(TelephonyManager::class.java).dataNetworkType
            } catch (e: Exception) {
                0
            }
        }
        return when (type) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G"
            TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_GSM, TelephonyManager.NETWORK_TYPE_IDEN -> "2G"
            else -> "Mobil"
        }
    }
}
