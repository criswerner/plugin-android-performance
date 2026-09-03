package com.cristianwer.plugin.gfx

data class PhaseBreakdown(
    val drawMs: Double = 0.0,
    val prepareMs: Double = 0.0,
    val processMs: Double = 0.0,
    val executeMs: Double = 0.0
) {
    val totalFrameMs: Double get() = drawMs + prepareMs + processMs + executeMs
    val cpuPhaseMs: Double get() = drawMs + prepareMs
    val gpuPhaseMs: Double get() = processMs + executeMs
}

data class SlowRenderingStats(
    val slowUiThreadCount: Int = 0,
    val slowSyncCount: Int = 0,
    val slowBitmapUploadCount: Int = 0,
    val slowIssueDrawCount: Int = 0,
    val missedVsyncCount: Int = 0
) {
    val totalSlowEvents: Int
        get() = slowUiThreadCount + slowSyncCount + slowBitmapUploadCount + slowIssueDrawCount + missedVsyncCount
}

data class GfxReport(
    val packageName: String,
    val totalFrames: Int,
    val jankyFrames: Int,
    val jankyPercentage: Double,
    val p50Ms: Double,
    val p90Ms: Double,
    val p95Ms: Double,
    val p99Ms: Double,
    val frozenFrames: Int,
    val slowRenderingStats: SlowRenderingStats,
    val phaseBreakdown: PhaseBreakdown?,
    val rawOutput: String
) {
    val hasData: Boolean get() = totalFrames > 0
}
