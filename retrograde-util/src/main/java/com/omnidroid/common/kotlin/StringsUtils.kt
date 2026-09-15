package com.omnidroid.common.kotlin

fun String.startsWithAny(strings: Collection<String>) = strings.any { this.startsWith(it) }
