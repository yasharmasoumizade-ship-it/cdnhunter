// Quick syntax check for TrafficCharts.kt
import java.io.File

fun main() {
    val file = File("app/src/main/java/com/cdnhunter/app/ui/components/TrafficCharts.kt")
    val content = file.readText()
    
    // Check for unmatched braces
    var openBraces = 0
    var closeBraces = 0
    var openParens = 0
    var closeParens = 0
    
    for (char in content) {
        when (char) {
            '{' -> openBraces++
            '}' -> closeBraces++
            '(' -> openParens++
            ')' -> closeParens++
        }
    }
    
    println("Open braces: $openBraces, Close braces: $closeBraces")
    println("Open parens: $openParens, Close parens: $closeParens")
    
    if (openBraces == closeBraces && openParens == closeParens) {
        println("✓ Braces and parentheses balanced")
    } else {
        println("✗ Braces or parentheses unbalanced!")
    }
}
