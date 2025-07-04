package icu.nullptr.hidemyapplist.xposed

import android.app.ActivityManagerHidden
import android.content.AttributionSource
import android.content.pm.IPackageManager
import android.os.Build
import android.os.Bundle
import android.os.ServiceManager
import icu.nullptr.hidemyapplist.common.BuildConfig
import icu.nullptr.hidemyapplist.common.Constants
import rikka.hidden.compat.ActivityManagerApis
import rikka.hidden.compat.adapter.UidObserverAdapter

object UserService {

    private const val TAG = "HMA-UserService"

    private var appUid = 0

    private val uidObserver = object : UidObserverAdapter() {
        override fun onUidActive(uid: Int) {
            if (uid != appUid) return
            try {
                val provider = ActivityManagerApis.getContentProviderExternal(Constants.PROVIDER_AUTHORITY, 0, null, null)
                if (provider == null) {
                    logE(TAG, "Failed to get provider")
                    return
                }
                val extras = Bundle()
                extras.putBinder("binder", HMAService.instance)
                val reply = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val attr = AttributionSource.Builder(1000).setPackageName("android").build()
                    provider.call(attr, Constants.PROVIDER_AUTHORITY, "", null, extras)
                } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
                    provider.call("android", null, Constants.PROVIDER_AUTHORITY, "", null, extras)
                } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    provider.call("android", Constants.PROVIDER_AUTHORITY, "", null, extras)
                } else {
                    provider.call("android", "", null, extras)
                }
                if (reply == null) {
                    logE(TAG, "Failed to send binder to app")
                    return
                }
                logI(TAG, "Send binder to app")
            } catch (e: Throwable) {
                logE(TAG, "onUidActive", e)
            }
        }
    }

    fun register(pms: IPackageManager) {
        logI(TAG, "Initialize HMAService - Version ${BuildConfig.SERVICE_VERSION}")
        val service = HMAService(pms)
        appUid = Utils.getPackageUidCompat(service.pms, Constants.APP_PACKAGE_NAME, 0, 0)
        val appPackage = Utils.getPackageInfoCompat(service.pms, Constants.APP_PACKAGE_NAME, 0, 0)

        // 注意：应用签名验证已被禁用（修改日期：2025-07-03）
        // 原因：允许修改后的应用正常运行，绕过签名检查
        // 如需恢复签名验证，请取消注释下方的验证逻辑
        if (!Utils.verifyAppSignature(appPackage.applicationInfo?.sourceDir.toString())) {
            // 签名验证失败，但已禁用验证 - 继续执行而不是返回
            logW(TAG, "App signature verification disabled - continuing service initialization")
            // 原始行为：logE(TAG, "Fatal: App signature mismatch") 然后 return
        }
        logD(TAG, "Client uid: $appUid")
        logI(TAG, "Register observer")

        waitSystemService("activity")
        ActivityManagerApis.registerUidObserver(
            uidObserver,
            ActivityManagerHidden.UID_OBSERVER_ACTIVE,
            ActivityManagerHidden.PROCESS_STATE_UNKNOWN,
            null
        )
    }

    private fun waitSystemService(name: String) {
        while (ServiceManager.getService(name) == null) {
            Thread.sleep(1000)
        }
    }
}
