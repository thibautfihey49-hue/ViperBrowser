package com.viperbrowser
import android.net.VpnService
import android.os.ParcelFileDescriptor
class ViperVpnService : VpnService() {
    private var vpn: ParcelFileDescriptor? = null
    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        vpn = Builder().addAddress("10.1.10.1", 32).addRoute("0.0.0.0", 0).setMtu(1500).establish()
        return START_STICKY_COMPATIBILITY
    }
    override fun onDestroy() { vpn?.close(); super.onDestroy() }
}
