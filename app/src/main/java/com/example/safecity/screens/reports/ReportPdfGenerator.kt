package com.example.safecity.screens.reports

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.safecity.models.Incident
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ReportPdfGenerator {

    suspend fun generatePdf(
        context: Context,
        filters: ReportFilters,
        preview: ReportPreview
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val pageWidth = 595
            val pageHeight = 842
            val margin = 32
            val footerHeight = 34
            val zoneId = ZoneId.systemDefault()
            val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale("es", "CO"))
            val dateOnlyFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("es", "CO"))

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 70, 140)
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val bodyBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.LTGRAY
                strokeWidth = 1f
            }
            val chartPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(25, 118, 210)
                style = Paint.Style.FILL
            }
            val chartPaintAlt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(46, 125, 50)
                style = Paint.Style.FILL
            }

            val generatedDate = Instant.ofEpochMilli(preview.generatedAtMillis)
                .atZone(zoneId)
                .format(dateFormatter)
            val rangeStart = Instant.ofEpochMilli(preview.rangeStartMillis)
                .atZone(zoneId)
                .format(dateOnlyFormatter)
            val rangeEnd = Instant.ofEpochMilli(preview.rangeEndMillis)
                .atZone(zoneId)
                .format(dateOnlyFormatter)

            val document = PdfDocument()
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas
            var y = margin + 30

            fun drawHeader(target: Canvas) {
                target.drawText("SafeCity", margin.toFloat(), 24f, titlePaint)
                target.drawText("Reporte de incidentes", margin.toFloat(), 42f, subtitlePaint)
                target.drawLine(
                    margin.toFloat(),
                    50f,
                    (pageWidth - margin).toFloat(),
                    50f,
                    linePaint
                )
            }

            fun drawFooter(target: Canvas) {
                val footerY = (pageHeight - footerHeight).toFloat()
                target.drawLine(
                    margin.toFloat(),
                    footerY - 8f,
                    (pageWidth - margin).toFloat(),
                    footerY - 8f,
                    linePaint
                )
                target.drawText("SafeCity - Reporte generado automaticamente", margin.toFloat(), footerY + 10f, subtitlePaint)
                target.drawText("Pagina $pageNumber", (pageWidth - margin - 70).toFloat(), footerY + 10f, subtitlePaint)
            }

            fun startNewPage() {
                drawFooter(canvas)
                document.finishPage(page)

                pageNumber += 1
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = margin + 30
                drawHeader(canvas)
            }

            fun ensureSpace(heightNeeded: Int) {
                if (y + heightNeeded > pageHeight - margin - footerHeight) {
                    startNewPage()
                }
            }

            fun drawSectionTitle(title: String) {
                ensureSpace(26)
                canvas.drawText(title, margin.toFloat(), y.toFloat(), sectionPaint)
                y += 8
                canvas.drawLine(
                    margin.toFloat(),
                    y.toFloat(),
                    (pageWidth - margin).toFloat(),
                    y.toFloat(),
                    linePaint
                )
                y += 16
            }

            fun drawWrappedText(text: String, paint: Paint = bodyPaint, lineHeight: Int = 16, left: Int = margin): Int {
                var localY = y
                val maxWidth = pageWidth - (margin * 2)
                var remaining = text.trim()
                while (remaining.isNotEmpty()) {
                    ensureSpace(lineHeight + 2)
                    val count = paint.breakText(remaining, true, maxWidth.toFloat(), null)
                    val safeCount = count.coerceAtLeast(1)
                    val line = remaining.substring(0, safeCount)
                    canvas.drawText(line.trimEnd(), left.toFloat(), localY.toFloat(), paint)
                    localY += lineHeight
                    y = localY
                    remaining = remaining.substring(safeCount).trimStart()
                }
                return localY
            }

            fun drawKeyValue(key: String, value: String) {
                ensureSpace(18)
                canvas.drawText("$key:", margin.toFloat(), y.toFloat(), bodyBoldPaint)
                canvas.drawText(value, (margin + 130).toFloat(), y.toFloat(), bodyPaint)
                y += 16
            }

            fun drawBarChart(title: String, data: List<Pair<String, Int>>, paint: Paint = chartPaint) {
                if (data.isEmpty()) return
                drawSectionTitle(title)
                val maxValue = data.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                val chartLeft = margin + 150
                val chartRight = pageWidth - margin - 40
                val chartWidth = (chartRight - chartLeft).coerceAtLeast(120)

                data.take(12).forEach { (label, value) ->
                    ensureSpace(22)
                    val ratio = value.toFloat() / maxValue.toFloat()
                    val barWidth = (chartWidth * ratio).toInt().coerceAtLeast(if (value > 0) 2 else 0)
                    val top = y - 11
                    val bottom = y - 1

                    canvas.drawText(trimForCell(label, 24), margin.toFloat(), y.toFloat(), bodyPaint)
                    canvas.drawRect(
                        chartLeft.toFloat(),
                        top.toFloat(),
                        (chartLeft + barWidth).toFloat(),
                        bottom.toFloat(),
                        paint
                    )
                    canvas.drawText(value.toString(), (chartRight + 8).toFloat(), y.toFloat(), bodyBoldPaint)
                    y += 18
                }
                y += 8
            }

            fun drawIncidentTable(incidents: List<Incident>) {
                if (incidents.isEmpty()) return

                drawSectionTitle("Tabla de incidentes")
                val colDate = margin
                val colType = colDate + 72
                val colCategory = colType + 90
                val colStatus = colCategory + 130
                val colZone = colStatus + 92

                fun drawHeaderRow() {
                    ensureSpace(22)
                    val rowTop = y - 12
                    val rowBottom = y + 4
                    val headerBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(238, 242, 250)
                        style = Paint.Style.FILL
                    }
                    canvas.drawRect(
                        margin.toFloat(),
                        rowTop.toFloat(),
                        (pageWidth - margin).toFloat(),
                        rowBottom.toFloat(),
                        headerBg
                    )
                    canvas.drawText("Fecha", colDate.toFloat(), y.toFloat(), bodyBoldPaint)
                    canvas.drawText("Tipo", colType.toFloat(), y.toFloat(), bodyBoldPaint)
                    canvas.drawText("Categoria", colCategory.toFloat(), y.toFloat(), bodyBoldPaint)
                    canvas.drawText("Estado", colStatus.toFloat(), y.toFloat(), bodyBoldPaint)
                    canvas.drawText("Zona", colZone.toFloat(), y.toFloat(), bodyBoldPaint)
                    y += 16
                }

                drawHeaderRow()

                incidents.forEachIndexed { index, incident ->
                    ensureSpace(18)
                    if (y + 18 > pageHeight - margin - footerHeight) {
                        startNewPage()
                        drawSectionTitle("Tabla de incidentes (continuacion)")
                        drawHeaderRow()
                    }

                    val rowDate = Instant.ofEpochMilli(incident.timestamp).atZone(zoneId).format(dateOnlyFormatter)
                    val rowType = if (incident.type.name == "SEGURIDAD") "Seguridad" else "Infraestructura"
                    val rowCategory = incident.category.ifBlank { "Sin categoria" }
                    val rowStatus = statusLabel(incident.status)
                    val rowZone = incident.address.ifBlank { "Sin zona" }

                    canvas.drawText(trimForCell(rowDate, 10), colDate.toFloat(), y.toFloat(), bodyPaint)
                    canvas.drawText(trimForCell(rowType, 14), colType.toFloat(), y.toFloat(), bodyPaint)
                    canvas.drawText(trimForCell(rowCategory, 18), colCategory.toFloat(), y.toFloat(), bodyPaint)
                    canvas.drawText(trimForCell(rowStatus, 12), colStatus.toFloat(), y.toFloat(), bodyPaint)
                    canvas.drawText(trimForCell(rowZone, 16), colZone.toFloat(), y.toFloat(), bodyPaint)
                    y += 14

                    if (index % 2 == 1) {
                        canvas.drawLine(
                            margin.toFloat(),
                            y.toFloat(),
                            (pageWidth - margin).toFloat(),
                            y.toFloat(),
                            linePaint
                        )
                    }
                }
                y += 8
            }

            drawHeader(canvas)

            drawSectionTitle("Resumen del reporte")
            drawKeyValue("Titulo", "Reporte de incidentes SafeCity")
            drawKeyValue("Fecha de generacion", generatedDate)
            drawKeyValue("Rango consultado", "$rangeStart - $rangeEnd")
            drawKeyValue("Filtro de tiempo", filters.rangeType.label)
            drawKeyValue("Filtro de tipo", filters.categoryFilter.label)
            drawKeyValue("Graficas", if (filters.includeCharts) "Incluidas" else "No incluidas")
            drawKeyValue("Tabla de incidentes", if (filters.includeIncidentList) "Incluida" else "No incluida")

            y += 6
            drawSectionTitle("Resumen ejecutivo")
            drawWrappedText(preview.executiveSummary)

            drawSectionTitle("Totales generales")
            drawKeyValue("Total de incidentes", preview.totalIncidents.toString())
            drawKeyValue("Verificados", preview.verifiedCount.toString())
            drawKeyValue("No verificados", preview.unverifiedCount.toString())

            drawSectionTitle("Estadisticas")
            drawWrappedText("Categorias con mayor frecuencia:")
            preview.byCategory.entries.take(5).forEach { (name, count) ->
                ensureSpace(16)
                canvas.drawText("- ${trimForCell(name, 40)}: $count", margin.toFloat(), y.toFloat(), bodyPaint)
                y += 14
            }

            if (preview.topZones.isNotEmpty()) {
                y += 4
                drawWrappedText("Zonas con mas incidentes:")
                preview.topZones.forEach { (zone, count) ->
                    ensureSpace(16)
                    canvas.drawText("- ${trimForCell(zone, 40)}: $count", margin.toFloat(), y.toFloat(), bodyPaint)
                    y += 14
                }
            }

            if (filters.includeCharts) {
                drawBarChart(
                    title = "Grafica comparativa: Incidentes por tipo",
                    data = preview.byType.entries.sortedByDescending { it.value }.map { it.toPair() },
                    paint = chartPaint
                )
                drawBarChart(
                    title = "Grafica comparativa: Incidentes por estado",
                    data = preview.byStatus.entries.sortedByDescending { it.value }.map { it.toPair() },
                    paint = chartPaintAlt
                )
                drawBarChart(
                    title = "Grafica comparativa: Verificados vs no verificados",
                    data = listOf(
                        "Verificados" to preview.verifiedCount,
                        "No verificados" to preview.unverifiedCount
                    ),
                    paint = chartPaint
                )
                drawBarChart(
                    title = "Comparativa semanal/mensual",
                    data = preview.comparison.map { it.label to it.count },
                    paint = chartPaintAlt
                )
            }

            if (filters.includeIncidentList) {
                drawIncidentTable(preview.incidents)
            }

            drawFooter(canvas)
            document.finishPage(page)

            val outputDir = File(context.filesDir, "reports").apply { mkdirs() }
            val fileName = "SafeCity_reporte_${System.currentTimeMillis()}.pdf"
            val outputFile = File(outputDir, fileName)
            FileOutputStream(outputFile).use { output ->
                document.writeTo(output)
            }
            document.close()
            outputFile
        }
    }

    private fun trimForCell(text: String, maxChars: Int): String {
        val clean = text.trim()
        if (clean.length <= maxChars) return clean
        return clean.take(maxChars - 3) + "..."
    }
}