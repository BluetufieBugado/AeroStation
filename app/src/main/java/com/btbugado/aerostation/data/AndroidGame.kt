package com.btbugado.aerostation.data

/** Um aplicativo Android fixado na biblioteca principal como se fosse um jogo. */
data class AndroidGame(
    val packageName: String,
    val activityName: String,
    val displayName: String,
    val iconUri: String?
) {
    val key: String get() = "retroaero://android-app/$packageName"
}
