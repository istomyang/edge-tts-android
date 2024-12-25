package com.istomyang.edgetss.engine

import java.math.BigInteger
import java.security.MessageDigest


/**
 * generate Sec-MS-GEC token.
 *
 * Use algo from [here](https://github.com/rany2/edge-tts/issues/290)
 */
internal fun genSecMsGec(): String {
    var sec: Long = System.currentTimeMillis() / 1000
    sec += 11644473600
    sec -= (sec % 300)
    val nsec = (sec * 1e9 / 100).toLong()
    val str = "%d6A5AA1D4EAFF4E9FB37E23D68491D6F4".format(nsec)
    val hasher = MessageDigest.getInstance("SHA-256")
    hasher.update(str.toByteArray())
    val hash = hasher.digest()
    val encoding = BigInteger(1, hash).toString(16).uppercase()
    return encoding
}