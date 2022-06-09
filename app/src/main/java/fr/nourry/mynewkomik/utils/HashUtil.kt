package fr.nourry.mynewkomik.utils

import java.math.BigInteger
import java.security.MessageDigest

fun String.md5(): String {
    val md = MessageDigest.getInstance("MD5")
    return BigInteger(1, md.digest(toByteArray())).toString(16).padStart(32, '0')
}

fun String.sha512(): String {
    return this.hashWithAlgorithm("SHA-512")
}
fun String.sha256(): String {
    return this.hashWithAlgorithm("SHA-256")
}
fun String.sha1(): String {
    return this.hashWithAlgorithm("SHA-1")
}

private fun String.hashWithAlgorithm(algorithm: String): String {
    val digest = MessageDigest.getInstance(algorithm)
    val bytes = digest.digest(this.toByteArray(Charsets.UTF_8))
    return bytes.fold("") { str, it -> str + "%02x".format(it) }
}

fun stringToHash(str:String):String {
    return str.md5()
}
