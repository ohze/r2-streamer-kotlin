package org.readium.r2

import java.io.File
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.security.MessageDigest

fun String.toHexBytes(): ByteArray {
  val len = this.length
  val data = ByteArray(len / 2)
  var i = 0
  while (i < len) {
    data[i / 2] = ((Character.digit(this[i], 16) shl 4)
        + Character.digit(this[i + 1], 16)).toByte()
    i += 2
  }
  return data
}

fun String.sha1() = String(this.getRawBytes().sha1Bytes().encodeHex())

fun String.getRawBytes(): ByteArray {
  return try {
    toByteArray(Charsets.UTF_8)
  } catch (e: UnsupportedEncodingException) {
    toByteArray()
  }
}

private val DIGITS_LOWER = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

fun ByteArray.sha1Bytes(): ByteArray = MessageDigest
    .getInstance("SHA-1")
    .digest(this)

fun ByteArray.encodeHex(): CharArray {
  val l = size
  val out = CharArray(l shl 1)
  var i = 0
  var j = 0
  while (i < l) {
    out[j++] = DIGITS_LOWER[(240 and this[i].toInt()).ushr(4)]
    out[j++] = DIGITS_LOWER[15 and this[i].toInt()]
    i++
  }
  return out
}

fun InputStream.toFile(path: String) {
  use { input ->
    File(path).outputStream().use { input.copyTo(it) }
  }
}
