// Vendored from AntNexo/antnexo-android v1.1.0 (4e1ed06e7e3d5977e2be854a3753d95750e50b2d) by scripts/vendor.sh. Do not edit.
package com.antnexo.core

/**
 * A locale identifier as BCP 47 without extensions (`en_CZ` → `en-CZ`, `en-US-u-ca-japanese` → `en-US`), at most 35
 * characters (what the API takes); `und` when empty.
 */
fun bcp47(tag: String): String {
    // Locale.toString() puts a script and extensions after "_#" ("sr_RS_#Latn"): the script goes after the language
    val script = tag.substringAfter("_#", "").substringBefore('-').takeIf { it.length == 4 && it.all(Char::isLetter) }
    var base = tag.substringBefore("_#").substringBefore('@').replace('_', '-')
    if (script != null) base = base.substringBefore('-') + "-" + script + base.removePrefix(base.substringBefore('-'))
    // drop extensions and private use: everything from the first singleton subtag on
    val parts = base.split('-').filter { it.isNotEmpty() }.takeWhile { it.length > 1 }
    val out = parts.joinToString("-").take(35).trimEnd('-')
    return out.ifEmpty { "und" }
}
