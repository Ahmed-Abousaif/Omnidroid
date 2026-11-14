package com.omnidroid.common.math

data class Fraction(val numerator: Int, val denominator: Int) {
    val floatValue = numerator.toFloat() / denominator.toFloat()
}
