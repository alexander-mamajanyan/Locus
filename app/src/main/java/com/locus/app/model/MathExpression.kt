package com.locus.app.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

fun compileMathExpression(source: String): (Double) -> Double =
    ExpressionParser(source).parse()

private class ExpressionParser(private val source: String) {
    private var index = 0

    fun parse(): (Double) -> Double {
        val expression = parseExpression()
        skipSpaces()
        require(index == source.length) { "Unexpected input at position ${index + 1}." }
        return expression
    }

    private fun parseExpression(): (Double) -> Double {
        var left = parseTerm()
        while (true) {
            skipSpaces()
            left = when {
                take('+') -> {
                    val previous = left
                    val right = parseTerm()
                    val combined: (Double) -> Double = { x -> previous(x) + right(x) }
                    combined
                }
                take('-') -> {
                    val previous = left
                    val right = parseTerm()
                    val combined: (Double) -> Double = { x -> previous(x) - right(x) }
                    combined
                }
                else -> return left
            }
        }
    }

    private fun parseTerm(): (Double) -> Double {
        var left = parseUnary()
        while (true) {
            skipSpaces()
            left = when {
                take('*') -> {
                    val previous = left
                    val right = parseUnary()
                    val combined: (Double) -> Double = { x -> previous(x) * right(x) }
                    combined
                }
                take('/') -> {
                    val previous = left
                    val right = parseUnary()
                    val combined: (Double) -> Double = { x -> previous(x) / right(x) }
                    combined
                }
                else -> return left
            }
        }
    }

    private fun parseUnary(): (Double) -> Double {
        skipSpaces()
        return when {
            take('+') -> parseUnary()
            take('-') -> {
                val operand = parseUnary()
                val negated: (Double) -> Double = { x -> -operand(x) }
                negated
            }
            else -> parsePower()
        }
    }

    private fun parsePower(): (Double) -> Double {
        val base = parsePrimary()
        skipSpaces()
        if (!take('^')) return base
        val exponent = parseUnary()
        return { x -> base(x).pow(exponent(x)) }
    }

    private fun parsePrimary(): (Double) -> Double {
        skipSpaces()
        require(index < source.length) { "Expression is incomplete." }

        if (take('(')) {
            val expression = parseExpression()
            skipSpaces()
            require(take(')')) { "Expected ')' at position ${index + 1}." }
            return expression
        }

        val char = source[index]
        if (char.isDigit() || char == '.') return parseNumber()
        if (char.isLetter()) return parseName()
        throw IllegalArgumentException("Unexpected '${source[index]}' at position ${index + 1}.")
    }

    private fun parseNumber(): (Double) -> Double {
        val start = index
        var decimalPointSeen = false
        while (index < source.length) {
            val char = source[index]
            when {
                char.isDigit() -> index++
                char == '.' && !decimalPointSeen -> {
                    decimalPointSeen = true
                    index++
                }
                else -> break
            }
        }
        val number = source.substring(start, index).toDoubleOrNull()
            ?: throw IllegalArgumentException("Invalid number at position ${start + 1}.")
        return { number }
    }

    private fun parseName(): (Double) -> Double {
        val start = index
        while (index < source.length && source[index].isLetter()) index++
        val name = source.substring(start, index).lowercase()
        if (name == "x") return { x -> x }
        if (name == "pi" || name == "π") return { Math.PI }
        if (name == "e") return { Math.E }

        skipSpaces()
        require(take('(')) { "Function '$name' needs parentheses, for example $name(x)." }
        val argument = parseExpression()
        skipSpaces()
        require(take(')')) { "Expected ')' at position ${index + 1}." }
        return when (name) {
            "sin" -> { x: Double -> sin(argument(x)) }
            "cos" -> { x: Double -> cos(argument(x)) }
            "tan" -> { x: Double -> tan(argument(x)) }
            "sqrt" -> { x: Double -> sqrt(argument(x)) }
            "abs" -> { x: Double -> abs(argument(x)) }
            "ln" -> { x: Double -> ln(argument(x)) }
            "log" -> { x: Double -> log10(argument(x)) }
            "exp" -> { x: Double -> exp(argument(x)) }
            else -> throw IllegalArgumentException("Unknown function '$name'.")
        }
    }

    private fun take(char: Char): Boolean {
        if (index >= source.length || source[index] != char) return false
        index++
        return true
    }

    private fun skipSpaces() {
        while (index < source.length && source[index].isWhitespace()) index++
    }

    private fun Double.pow(exponent: Double): Double = Math.pow(this, exponent)
}
