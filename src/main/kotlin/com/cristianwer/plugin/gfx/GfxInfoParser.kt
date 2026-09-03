package com.cristianwer.plugin.gfx

import java.util.regex.Pattern

object GfxInfoParser {

    private val totalFramesRegex = Pattern.compile("Total frames rendered:\\s*(\\d+)", Pattern.CASE_INSENSITIVE)
    private val jankyFramesRegex = Pattern.compile("Janky frames:\\s*(\\d+)\\s*\\(([^%]+)%\\)", Pattern.CASE_INSENSITIVE)

    private val p50Regex = Pattern.compile("50th percentile:\\s*(\\d+)ms", Pattern.CASE_INSENSITIVE)
    private val p90Regex = Pattern.compile("90th percentile:\\s*(\\d+)ms", Pattern.CASE_INSENSITIVE)
    private val p95Regex = Pattern.compile("95th percentile:\\s*(\\d+)ms", Pattern.CASE_INSENSITIVE)
    private val p99Regex = Pattern.compile("99th percentile:\\s*(\\d+)ms", Pattern.CASE_INSENSITIVE)

    private val slowUiThreadRegex = Pattern.compile("Number Slow UI thread:\\s*(\\d+)", Pattern.CASE_INSENSITIVE)
    private val slowSyncRegex = Pattern.compile("Number Slow (?:sync|enqueue):\\s*(\\d+)", Pattern.CASE_INSENSITIVE)
    private val slowBitmapRegex = Pattern.compile("Number Slow Bitmap uploads:\\s*(\\d+)", Pattern.CASE_INSENSITIVE)
    private val slowDrawRegex = Pattern.compile("Number Slow issue draw commands:\\s*(\\d+)", Pattern.CASE_INSENSITIVE)
    private val missedVsyncRegex = Pattern.compile("Number (?:Missed Vsync|Frame deadline missed):\\s*(\\d+)", Pattern.CASE_INSENSITIVE)
    private val frozenFramesRegex = Pattern.compile("Number Frozen frames:\\s*(\\d+)", Pattern.CASE_INSENSITIVE)

    fun parse(packageName: String, output: String): GfxReport {
        if (output.isBlank() || output.contains("No process found", ignoreCase = true)) {
            return GfxReport(
                packageName = packageName,
                totalFrames = 0,
                jankyFrames = 0,
                jankyPercentage = 0.0,
                p50Ms = 0.0,
                p90Ms = 0.0,
                p95Ms = 0.0,
                p99Ms = 0.0,
                frozenFrames = 0,
                slowRenderingStats = SlowRenderingStats(),
                phaseBreakdown = null,
                rawOutput = output
            )
        }

        val totalFrames = extractInt(totalFramesRegex, output)
        val jankyFrames = extractInt(jankyFramesRegex, output, 1)
        val jankyPct = extractDouble(jankyFramesRegex, output, 2)

        val p50 = extractDouble(p50Regex, output)
        val p90 = extractDouble(p90Regex, output)
        val p95 = extractDouble(p95Regex, output)
        val p99 = extractDouble(p99Regex, output)

        val slowUiThread = extractInt(slowUiThreadRegex, output)
        val slowSync = extractInt(slowSyncRegex, output)
        val slowBitmap = extractInt(slowBitmapRegex, output)
        val slowDraw = extractInt(slowDrawRegex, output)
        val missedVsync = extractInt(missedVsyncRegex, output)

        val slowStats = SlowRenderingStats(
            slowUiThreadCount = slowUiThread,
            slowSyncCount = slowSync,
            slowBitmapUploadCount = slowBitmap,
            slowIssueDrawCount = slowDraw,
            missedVsyncCount = missedVsync
        )

        var frozen = extractInt(frozenFramesRegex, output)
        if (frozen == 0) {
            frozen = calculateFrozenFramesFromHistogram(output)
        }

        val phaseBreakdown = parsePhaseBreakdown(output)

        return GfxReport(
            packageName = packageName,
            totalFrames = totalFrames,
            jankyFrames = jankyFrames,
            jankyPercentage = if (jankyPct > 0) jankyPct else calculatePercentage(jankyFrames, totalFrames),
            p50Ms = p50,
            p90Ms = p90,
            p95Ms = p95,
            p99Ms = p99,
            frozenFrames = frozen,
            slowRenderingStats = slowStats,
            phaseBreakdown = phaseBreakdown,
            rawOutput = output
        )
    }

    private fun extractInt(pattern: Pattern, text: String, groupIndex: Int = 1): Int {
        val matcher = pattern.matcher(text)
        return if (matcher.find()) {
            matcher.group(groupIndex).toIntOrNull() ?: 0
        } else 0
    }

    private fun extractDouble(pattern: Pattern, text: String, groupIndex: Int = 1): Double {
        val matcher = pattern.matcher(text)
        return if (matcher.find()) {
            matcher.group(groupIndex).toDoubleOrNull() ?: 0.0
        } else 0.0
    }

    private fun calculatePercentage(numerator: Int, denominator: Int): Double {
        if (denominator <= 0) return 0.0
        return (numerator.toDouble() / denominator.toDouble()) * 100.0
    }

    private fun calculateFrozenFramesFromHistogram(output: String): Int {
        // Parse HISTOGRAM section: e.g. HISTOGRAM: 5ms=10 10ms=5 750ms=2 1000ms=1
        val histogramIndex = output.indexOf("HISTOGRAM:")
        if (histogramIndex == -1) return 0

        var count = 0
        val subText = output.substring(histogramIndex)
        val endLine = subText.indexOf('\n')
        val histLine = if (endLine != -1) subText.substring(0, endLine) else subText

        val entries = histLine.removePrefix("HISTOGRAM:").trim().split("\\s+".toRegex())
        for (entry in entries) {
            if (entry.contains("ms=")) {
                val parts = entry.split("ms=")
                if (parts.size == 2) {
                    val ms = parts[0].toIntOrNull() ?: 0
                    val frames = parts[1].toIntOrNull() ?: 0
                    if (ms >= 700) {
                        count += frames
                    }
                }
            }
        }
        return count
    }

    private fun parsePhaseBreakdown(output: String): PhaseBreakdown? {
        // Try parsing legacy format: Draw \t Prepare \t Process \t Execute
        val lines = output.lines()
        var headerFound = false
        var drawSum = 0.0
        var prepareSum = 0.0
        var processSum = 0.0
        var executeSum = 0.0
        var count = 0

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Draw\tPrepare\tProcess\tExecute") ||
                trimmed.startsWith("Draw  Prepare  Process  Execute")) {
                headerFound = true
                continue
            }
            if (headerFound) {
                if (trimmed.isEmpty() || trimmed.startsWith("---") || trimmed.startsWith("Stats")) {
                    break
                }
                val cols = trimmed.split("\\s+".toRegex())
                if (cols.size >= 4) {
                    val d = cols[0].toDoubleOrNull()
                    val p = cols[1].toDoubleOrNull()
                    val pr = cols[2].toDoubleOrNull()
                    val e = cols[3].toDoubleOrNull()
                    if (d != null && p != null && pr != null && e != null) {
                        drawSum += d
                        prepareSum += p
                        processSum += pr
                        executeSum += e
                        count++
                    }
                }
            }
        }

        if (count > 0) {
            return PhaseBreakdown(
                drawMs = drawSum / count,
                prepareMs = prepareSum / count,
                processMs = processSum / count,
                executeMs = executeSum / count
            )
        }

        return null
    }
}
