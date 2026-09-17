package com.btbugado.aerostation.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast

object AndroidAppLauncher {
    fun launch(context: Context, app: AndroidGame) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(app.packageName, app.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        AppAudio.playLaunch()
        try {
            context.startActivity(intent)
            PlayTimeStore.beginSession(context, app.key)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Não consegui abrir ${app.displayName}.", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, "O Android não permitiu abrir esse aplicativo.", Toast.LENGTH_SHORT).show()
        }
    }
}
