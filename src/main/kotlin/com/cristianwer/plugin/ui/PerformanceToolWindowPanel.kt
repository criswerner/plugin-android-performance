package com.cristianwer.plugin.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.cristianwer.plugin.adb.AdbDevice
import com.cristianwer.plugin.adb.AdbManager
import com.cristianwer.plugin.gfx.GfxInfoParser
import com.cristianwer.plugin.gfx.GfxReport
import com.cristianwer.plugin.perfetto.PerfettoTraceManager
import java.awt.*
import java.io.File
import java.text.DecimalFormat
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.border.TitledBorder

class PerformanceToolWindowPanel(private val project: Project) : JBPanel<PerformanceToolWindowPanel>(BorderLayout()) {

    private val deviceComboBox = JComboBox<AdbDevice>()
    private val refreshDevicesButton = JButton("🔄")
    private val packageNameField = JTextField(20)

    // GFX UI Components
    private val fetchGfxButton = JButton("⚡ Obtener Métricas GFX")
    private val resetGfxButton = JButton("🧹 Resetear Stats GFX")
    
    private val totalFramesLabel = JBLabel("Total Frames: -")
    private val jankyFramesLabel = JBLabel("Janky Frames: -")
    private val slowRenderingLabel = JBLabel("Slow Rendering: -")
    private val frozenFramesLabel = JBLabel("Frozen Frames (>700ms): -")
    private val percentilesLabel = JBLabel("Latencia (p50/p90/p95/p99): -")
    
    private val cpuPhaseLabel = JBLabel("CPU Phase (Draw + Prepare): -")
    private val gpuPhaseLabel = JBLabel("GPU Phase (Process + Execute): -")
    private val totalFrameTimeLabel = JBLabel("Tiempo Total Frame Promedio: -")
    
    private val rawGfxTextArea = JTextArea(10, 40).apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 11)
    }

    // Perfetto UI Components
    private val playStopButton = JButton("▶ INICIAR CAPTURA PERFETTO")
    private val statusLabel = JBLabel("Estado: Listo para grabar")
    private val timerLabel = JBLabel("00:00")

    private val schedCheckBox = JCheckBox("sched (Kernel Sched)", true)
    private val freqCheckBox = JCheckBox("freq (CPU Freq)", true)
    private val gfxCheckBox = JCheckBox("gfx (RenderThread)", true)
    private val viewCheckBox = JCheckBox("view (View System)", true)
    private val amCheckBox = JCheckBox("am (ActivityManager)", true)
    private val wmCheckBox = JCheckBox("wm (WindowManager)", true)
    private val appCheckBox = JCheckBox("app (App Traces)", true)

    private val openFolderButton = JButton("📂 Abrir Carpeta de Trazas")
    private val openPerfettoWebButton = JButton("🌐 Abrir ui.perfetto.dev")
    private val lastTraceFileLabel = JBLabel("Última traza: Ninguna")

    private var timerThread: Thread? = null
    private var lastSavedTraceFile: File? = null

    init {
        border = EmptyBorder(8, 8, 8, 8)
        setupTopBar()
        setupTabbedPane()
        refreshDevices()
    }

    private fun setupTopBar() {
        val topPanel = JPanel(GridBagLayout())
        topPanel.border = BorderFactory.createTitledBorder("Dispositivo & App")
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            fill = GridBagConstraints.HORIZONTAL
        }

        // Row 0: Device Selector
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0
        topPanel.add(JLabel("Dispositivo:"), gbc)

        gbc.gridx = 1; gbc.weightx = 1.0
        topPanel.add(deviceComboBox, gbc)

        gbc.gridx = 2; gbc.weightx = 0.0
        refreshDevicesButton.toolTipText = "Refrescar lista de dispositivos ADB"
        refreshDevicesButton.addActionListener { refreshDevices() }
        topPanel.add(refreshDevicesButton, gbc)

        // Row 1: Package Name Field
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0
        topPanel.add(JLabel("Package Name:"), gbc)

        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0
        packageNameField.toolTipText = "Ejemplo: com.example.myapp"
        packageNameField.text = project.name.lowercase().replace("[^a-z0-9]".toRegex(), "")
            .let { if (it.isNotBlank()) "com.example.$it" else "com.example.app" }
        topPanel.add(packageNameField, gbc)

        add(topPanel, BorderLayout.NORTH)
    }

    private fun setupTabbedPane() {
        val tabbedPane = JBTabbedPane()
        tabbedPane.addTab("📊 Rendimiento GFX", createGfxTab())
        tabbedPane.addTab("⏱️ Captura Perfetto", createPerfettoTab())
        add(tabbedPane, BorderLayout.CENTER)
    }

    private fun createGfxTab(): JPanel {
        val panel = JPanel(BorderLayout(0, 8))

        // Action Buttons Bar
        val actionBar = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            fetchGfxButton.addActionListener { fetchGfxMetrics() }
            resetGfxButton.addActionListener { resetGfxMetrics() }
            add(fetchGfxButton)
            add(resetGfxButton)
        }
        panel.add(actionBar, BorderLayout.NORTH)

        // Metrics Grid & Breakdown
        val metricsPanel = JPanel(GridLayout(2, 1, 8, 8))

        // Card 1: Main GFX Metrics Summary
        val summaryCard = JPanel(GridLayout(5, 1, 4, 4)).apply {
            border = BorderFactory.createCompoundBorder(
                TitledBorder("Resumen de Frames GFX & Slow Rendering"),
                EmptyBorder(4, 8, 4, 8)
            )
            add(totalFramesLabel)
            add(jankyFramesLabel)
            add(slowRenderingLabel)
            add(frozenFramesLabel)
            add(percentilesLabel)
        }

        // Card 2: CPU vs GPU Breakdown
        val breakdownCard = JPanel(GridLayout(3, 1, 4, 4)).apply {
            border = BorderFactory.createCompoundBorder(
                TitledBorder("Informe GFX: CPU vs GPU"),
                EmptyBorder(4, 8, 4, 8)
            )
            add(cpuPhaseLabel)
            add(gpuPhaseLabel)
            add(totalFrameTimeLabel)
        }

        metricsPanel.add(summaryCard)
        metricsPanel.add(breakdownCard)

        // Raw Output Scroll Pane
        val rawPanel = JPanel(BorderLayout()).apply {
            border = TitledBorder("Salida Raw (dumpsys gfxinfo)")
            add(JBScrollPane(rawGfxTextArea), BorderLayout.CENTER)
        }

        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, metricsPanel, rawPanel).apply {
            resizeWeight = 0.5
        }

        panel.add(splitPane, BorderLayout.CENTER)
        return panel
    }

    private fun createPerfettoTab(): JPanel {
        val panel = JPanel(BorderLayout(0, 8))
        panel.border = EmptyBorder(8, 8, 8, 8)

        // Center Panel with Play/Stop Button and Timer
        val centerPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(8, 8, 8, 8)
            fill = GridBagConstraints.HORIZONTAL
            gridx = 0
        }

        // Play/Stop Button
        gbc.gridy = 0
        playStopButton.font = Font(Font.SANS_SERIF, Font.BOLD, 14)
        playStopButton.preferredSize = Dimension(320, 50)
        playStopButton.background = JBColor(Color(46, 139, 87), Color(40, 167, 69))
        playStopButton.foreground = Color.WHITE
        playStopButton.isOpaque = true
        playStopButton.addActionListener { togglePerfettoRecording() }
        centerPanel.add(playStopButton, gbc)

        // Timer & Status
        gbc.gridy = 1
        timerLabel.font = Font(Font.MONOSPACED, Font.BOLD, 22)
        timerLabel.horizontalAlignment = SwingConstants.CENTER
        centerPanel.add(timerLabel, gbc)

        gbc.gridy = 2
        statusLabel.horizontalAlignment = SwingConstants.CENTER
        centerPanel.add(statusLabel, gbc)

        // Categories Configuration Panel
        val categoriesPanel = JPanel(GridLayout(4, 2, 4, 4)).apply {
            border = TitledBorder("Categorías de Traza Perfetto")
            add(schedCheckBox)
            add(freqCheckBox)
            add(gfxCheckBox)
            add(viewCheckBox)
            add(amCheckBox)
            add(wmCheckBox)
            add(appCheckBox)
        }

        gbc.gridy = 3
        centerPanel.add(categoriesPanel, gbc)

        // Export & Open Buttons
        val exportPanel = JPanel(FlowLayout(FlowLayout.CENTER, 8, 8)).apply {
            border = TitledBorder("Acciones de Traza")
            openFolderButton.addActionListener {
                val dir = getTraceExportDir()
                if (dir.exists()) Desktop.getDesktop().open(dir)
            }
            openPerfettoWebButton.addActionListener {
                BrowserUtil.browse("https://ui.perfetto.dev")
            }
            add(openFolderButton)
            add(openPerfettoWebButton)
        }

        gbc.gridy = 4
        centerPanel.add(exportPanel, gbc)

        gbc.gridy = 5
        lastTraceFileLabel.font = Font(Font.SANS_SERIF, Font.ITALIC, 11)
        centerPanel.add(lastTraceFileLabel, gbc)

        panel.add(JBScrollPane(centerPanel), BorderLayout.CENTER)
        return panel
    }

    private fun refreshDevices() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val devices = AdbManager.getConnectedDevices()
            SwingUtilities.invokeLater {
                deviceComboBox.removeAllItems()
                if (devices.isEmpty()) {
                    deviceComboBox.addItem(AdbDevice("No device", "Sin dispositivos ADB", "offline", false))
                } else {
                    devices.forEach { deviceComboBox.addItem(it) }
                }
            }
        }
    }

    private fun getSelectedDevice(): AdbDevice? {
        val selected = deviceComboBox.selectedItem as? AdbDevice
        if (selected == null || selected.serial == "No device") return null
        return selected
    }

    private fun fetchGfxMetrics() {
        val device = getSelectedDevice()
        val pkg = packageNameField.text.trim()

        if (device == null) {
            JOptionPane.showMessageDialog(this, "Por favor seleccione un dispositivo o emulador conectado.", "Error", JOptionPane.ERROR_MESSAGE)
            return
        }
        if (pkg.isBlank()) {
            JOptionPane.showMessageDialog(this, "Ingrese el nombre del paquete Android.", "Error", JOptionPane.WARNING_MESSAGE)
            return
        }

        fetchGfxButton.isEnabled = false
        fetchGfxButton.text = "Cargando..."

        ApplicationManager.getApplication().executeOnPooledThread {
            val output = AdbManager.getDumpsysGfxInfo(device.serial, pkg)
            val report = GfxInfoParser.parse(pkg, output)

            SwingUtilities.invokeLater {
                updateGfxUI(report)
                fetchGfxButton.isEnabled = true
                fetchGfxButton.text = "⚡ Obtener Métricas GFX"
            }
        }
    }

    private fun resetGfxMetrics() {
        val device = getSelectedDevice()
        val pkg = packageNameField.text.trim()
        if (device == null || pkg.isBlank()) return

        ApplicationManager.getApplication().executeOnPooledThread {
            AdbManager.resetGfxInfo(device.serial, pkg)
            SwingUtilities.invokeLater {
                rawGfxTextArea.text = "Stats de gfxinfo reseteadas para $pkg"
                totalFramesLabel.text = "Total Frames: Stats Reseteadas"
            }
        }
    }

    private fun updateGfxUI(report: GfxReport) {
        val df = DecimalFormat("#.##")
        rawGfxTextArea.text = report.rawOutput

        if (!report.hasData) {
            totalFramesLabel.text = "Total Frames: 0 (No se encontró el proceso de la app)"
            jankyFramesLabel.text = "Janky Frames: -"
            slowRenderingLabel.text = "Slow Rendering: -"
            frozenFramesLabel.text = "Frozen Frames (>700ms): -"
            percentilesLabel.text = "Latencia: -"
            cpuPhaseLabel.text = "CPU Phase: -"
            gpuPhaseLabel.text = "GPU Phase: -"
            totalFrameTimeLabel.text = "Tiempo Total Frame: -"
            return
        }

        totalFramesLabel.text = "<html><b>Total Frames:</b> ${report.totalFrames}</html>"
        
        val jankyColor = if (report.jankyPercentage > 5.0) "red" else "green"
        jankyFramesLabel.text = "<html><b>Janky Frames:</b> ${report.jankyFrames} (<font color='$jankyColor'><b>${df.format(report.jankyPercentage)}%</b></font>)</html>"

        val s = report.slowRenderingStats
        slowRenderingLabel.text = "<html><b>Slow Rendering:</b> Slow UI: ${s.slowUiThreadCount} | Slow Bitmap: ${s.slowBitmapUploadCount} | Missed Vsync: ${s.missedVsyncCount}</html>"

        val frozenColor = if (report.frozenFrames > 0) "red" else "green"
        frozenFramesLabel.text = "<html><b>Frozen Frames (>700ms):</b> <font color='$frozenColor'><b>${report.frozenFrames}</b></font> ❄️</html>"

        percentilesLabel.text = "<html><b>Latencia:</b> p50: ${report.p50Ms}ms | p90: ${report.p90Ms}ms | p95: ${report.p95Ms}ms | p99: ${report.p99Ms}ms</html>"

        report.phaseBreakdown?.let { phase ->
            cpuPhaseLabel.text = "<html><b>CPU Phase (Draw + Prepare):</b> ${df.format(phase.cpuPhaseMs)} ms (Draw: ${df.format(phase.drawMs)}ms, Prepare: ${df.format(phase.prepareMs)}ms)</html>"
            gpuPhaseLabel.text = "<html><b>GPU Phase (Process + Execute):</b> ${df.format(phase.gpuPhaseMs)} ms (Process: ${df.format(phase.processMs)}ms, Execute: ${df.format(phase.executeMs)}ms)</html>"
            totalFrameTimeLabel.text = "<html><b>Tiempo Total Frame Promedio:</b> ${df.format(phase.totalFrameMs)} ms</html>"
        } ?: run {
            cpuPhaseLabel.text = "CPU Phase: No disponible en el resumen actual"
            gpuPhaseLabel.text = "GPU Phase: No disponible en el resumen actual"
            totalFrameTimeLabel.text = "Tiempo Total Frame: -"
        }
    }

    private fun togglePerfettoRecording() {
        if (PerfettoTraceManager.isRecording) {
            stopPerfettoRecording()
        } else {
            startPerfettoRecording()
        }
    }

    private fun startPerfettoRecording() {
        val device = getSelectedDevice()
        if (device == null) {
            JOptionPane.showMessageDialog(this, "Seleccione un dispositivo conectado antes de grabar la traza.", "Error", JOptionPane.ERROR_MESSAGE)
            return
        }

        val categories = mutableListOf<String>()
        if (schedCheckBox.isSelected) categories.add("sched")
        if (freqCheckBox.isSelected) categories.add("freq")
        if (gfxCheckBox.isSelected) categories.add("gfx")
        if (viewCheckBox.isSelected) categories.add("view")
        if (amCheckBox.isSelected) categories.add("am")
        if (wmCheckBox.isSelected) categories.add("wm")
        if (appCheckBox.isSelected) categories.add("app")

        playStopButton.isEnabled = false

        ApplicationManager.getApplication().executeOnPooledThread {
            val started = PerfettoTraceManager.startTrace(
                serial = device.serial,
                categories = categories,
                onStatusChanged = { msg ->
                    SwingUtilities.invokeLater { statusLabel.text = msg }
                }
            )

            SwingUtilities.invokeLater {
                playStopButton.isEnabled = true
                if (started) {
                    playStopButton.text = "⏹ DETENER CAPTURA PERFETTO"
                    playStopButton.background = JBColor(Color(178, 34, 34), Color(220, 53, 69))
                    startTimer()
                }
            }
        }
    }

    private fun stopPerfettoRecording() {
        playStopButton.isEnabled = false
        stopTimer()

        val exportDir = getTraceExportDir()

        ApplicationManager.getApplication().executeOnPooledThread {
            val traceFile = PerfettoTraceManager.stopTrace(
                targetLocalDir = exportDir.absolutePath,
                onStatusChanged = { msg ->
                    SwingUtilities.invokeLater { statusLabel.text = msg }
                }
            )

            SwingUtilities.invokeLater {
                playStopButton.isEnabled = true
                playStopButton.text = "▶ INICIAR CAPTURA PERFETTO"
                playStopButton.background = JBColor(Color(46, 139, 87), Color(40, 167, 69))

                if (traceFile != null) {
                    lastSavedTraceFile = traceFile
                    lastTraceFileLabel.text = "<html>Última traza: <b>${traceFile.name}</b> (${traceFile.length() / 1024} KB)</html>"
                    JOptionPane.showMessageDialog(
                        this,
                        "Traza guardada exitosamente:\n${traceFile.absolutePath}\n\nPuedes abrirla en ui.perfetto.dev",
                        "Captura Finalizada",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                }
            }
        }
    }

    private fun startTimer() {
        stopTimer()
        timerThread = Thread {
            while (PerfettoTraceManager.isRecording) {
                val secs = PerfettoTraceManager.getRecordingDurationSeconds()
                val minutes = secs / 60
                val remainingSecs = secs % 60
                val timeStr = String.format("%02d:%02d", minutes, remainingSecs)

                SwingUtilities.invokeLater {
                    timerLabel.text = timeStr
                }
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }.apply { start() }
    }

    private fun stopTimer() {
        timerThread?.interrupt()
        timerThread = null
    }

    private fun getTraceExportDir(): File {
        val projectPath = project.basePath
        val dir = if (!projectPath.isNullOrBlank()) {
            File(projectPath, "perfetto_traces")
        } else {
            File(System.getProperty("user.home"), "perfetto_traces")
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}
