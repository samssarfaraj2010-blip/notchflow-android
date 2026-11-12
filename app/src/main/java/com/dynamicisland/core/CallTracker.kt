package com.dynamicisland.core

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Call STATE only (ringing / in call / idle) via the supported telephony callback. Needs READ_PHONE_STATE.
 * Phone numbers, contacts and call logs are never requested, so caller names are not available.
 */
@Suppress("DEPRECATION")
class CallTracker(private val ctx: Context) {
    private val _state = MutableStateFlow<CallData?>(null)
    val state: StateFlow<CallData?> = _state.asStateFlow()

    private val telephony = ctx.getSystemService(TelephonyManager::class.java)
    private var wanted = false
    private var registered: Any? = null

    fun hasPermission() =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    fun canEndCall() =
        Build.VERSION.SDK_INT >= 28 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED

    /**
     * Ends the ongoing call through the supported TelecomManager API (needs ANSWER_PHONE_CALLS).
     * If the permission has not been granted (or the platform refuses) the system Phone app is opened instead,
     * so the button is never a dead end.
     */
    fun endCall() {
        val ended = canEndCall() && try {
            @Suppress("MissingPermission")
            ctx.getSystemService(TelecomManager::class.java)?.endCall() == true
        } catch (e: SecurityException) {
            Log.w(TAG, "End call refused", e)
            false
        }
        if (!ended) {
            runCatching {
                ctx.startActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_PHONE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    fun start() { wanted = true; register() }
    fun refresh() { if (wanted) register() }
    fun stop() { wanted = false; unregister(); _state.value = null }

    private fun register() {
        if (registered != null || !hasPermission()) return
        try {
            registered = if (Build.VERSION.SDK_INT >= 31) registerModern() else registerLegacy()
        } catch (e: SecurityException) {
            Log.w(TAG, "Phone state permission missing", e)
        }
    }

    @RequiresApi(31)
    private fun registerModern(): Any {
        val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) = handle(state)
        }
        telephony.registerTelephonyCallback(ContextCompat.getMainExecutor(ctx), cb)
        return cb
    }

    private fun registerLegacy(): Any {
        val l = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) = handle(state)
        }
        telephony.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
        return l
    }

    private fun unregister() {
        val r = registered ?: return
        registered = null
        if (Build.VERSION.SDK_INT >= 31 && r is TelephonyCallback) telephony.unregisterTelephonyCallback(r)
        else if (r is PhoneStateListener) telephony.listen(r, PhoneStateListener.LISTEN_NONE)
    }

    private fun handle(state: Int) {
        _state.value = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> CallData(CallPhase.RINGING, SystemClock.elapsedRealtime())
            TelephonyManager.CALL_STATE_OFFHOOK ->
                _state.value?.takeIf { it.phase == CallPhase.ACTIVE } ?: CallData(CallPhase.ACTIVE, SystemClock.elapsedRealtime())
            else -> null
        }
    }

    private companion object { const val TAG = "CallTracker" }
}
