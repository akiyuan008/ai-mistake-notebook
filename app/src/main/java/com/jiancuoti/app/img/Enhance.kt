package com.jiancuoti.app.img

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * 扫描仪式图像增强 v2（自适应）：
 * - 先估计图片整体亮度和对比度，只在确实需要时才增强
 * - 光照不均才做背景归一化；本来均匀的图直接跳过，避免过曝
 * - 字迹加深幅度随对比度自适应，浅色纸不暴力压暗
 */
object Enhance {

    fun process(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return src

        // ---- 0) 采样统计：平均亮度 / 最亮背景 / 暗部占比（粗略字迹覆盖率）----
        val stepX = max(1, w / 128)
        val stepY = max(1, h / 128)
        var samples = 0
        var sumLum = 0f
        var bgLum = 0f          // 背景亮度估计（偏高亮度分位）
        var darkPixels = 0
        val lums = mutableListOf<Int>()
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val p = src.getPixel(x, y)
                val r = (p shr 16 and 0xFF)
                val g = (p shr 8 and 0xFF)
                val b = (p and 0xFF)
                val lum = (r * 299 + g * 587 + b * 114) / 1000
                sumLum += lum
                lums.add(lum)
                if (lum < 90) darkPixels++
                x += stepX
            }
            y += stepY
        }
        samples = lums.size
        if (samples == 0) return src
        val meanLum = sumLum / samples
        lums.sort()
        val p90 = lums[(samples * 0.90).toInt().coerceAtMost(samples - 1)]  // 90 分位 = 纸底
        val p10 = lums[(samples * 0.10).toInt().coerceAtMost(samples - 1)]  // 10 分位 = 字迹
        val contrast = p90 - p10
        bgLum = p90.toFloat()

        // ---- 1) 决策：是否需要处理、强度多大 ----
        // 风格预设：曝光+ 对比+ 锐度+（利于 AI 识别手写体）
        val needBrighten = bgLum < 225f               // 纸底不够亮就提亮
        val needContrast = contrast < 130             // 对比不足就拉伸
        val inkRatio = darkPixels.toFloat() / samples
        if (!needBrighten && !needContrast) return src

        // 光照不均检测：四角+中心背景亮度差
        val cornerLums = listOf(
            regionLum(src, 0, 0, w / 6, h / 6),
            regionLum(src, w - w / 6, 0, w / 6, h / 6),
            regionLum(src, 0, h - h / 6, w / 6, h / 6),
            regionLum(src, w - w / 6, h - h / 6, w / 6, h / 6)
        )
        val unevenness = (cornerLums.max() - cornerLums.min()).toFloat()
        val needFlat = unevenness > 28f               // 明显阴影才做背景归一化

        // 提亮目标：把纸底提到 ~245，但绝不超 252（防过曝）
        val brighten = if (needBrighten) min(1.35f, 245f / max(60f, bgLum)) else 1f
        // 对比拉伸：对比越低拉得越多，有上限
        val contrastGain = if (needContrast) min(1.5f, 115f / max(35f, contrast.toFloat())) else 1f
        // 字迹加深：字越少（标题/公式题）越不能压太狠
        val darken = if (inkRatio < 0.06f) 0.95f else 0.88f

        // ---- 2) 背景光照估计（仅在光照不均时使用）----
        val GN = 12
        val grid: FloatArray
        if (needFlat) {
            grid = FloatArray(GN * GN)
            val cnt = FloatArray(GN * GN)
            var gy = 0
            while (gy < h) {
                var gx = 0
                while (gx < w) {
                    val p = src.getPixel(gx, gy)
                    val lum = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
                    val gi = min(GN - 1, gy * GN / h) * GN + min(GN - 1, gx * GN / w)
                    grid[gi] += lum
                    cnt[gi]++
                    gx += max(1, w / 96)
                }
                gy += max(1, h / 96)
            }
            for (i in 0 until GN * GN) grid[i] = if (cnt[i] > 0) grid[i] / cnt[i] else 255f
        } else {
            grid = FloatArray(GN * GN) { 255f }
        }

        // ---- 3) 单遍处理 ----
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val row = IntArray(w)
        val rowOut = IntArray(w)
        for (yy in 0 until h) {
            src.getPixels(row, 0, w, 0, yy, w, 1)
            for (xx in 0 until w) {
                val p = row[xx]
                val r = (p shr 16 and 0xFF).toFloat()
                val g = (p shr 8 and 0xFF).toFloat()
                val b = (p and 0xFF).toFloat()

                // 背景归一化（仅光照不均时生效）
                var nr = r; var ng = g; var nb = b
                if (needFlat) {
                    val bg = bgAt(xx, yy, w, h, grid, GN)
                    val k = 238f / max(60f, (bg[0] + bg[1] + bg[2]) / 3f)
                    nr = r * k.coerceIn(0.85f, 1.3f)
                    ng = g * k.coerceIn(0.85f, 1.3f)
                    nb = b * k.coerceIn(0.85f, 1.3f)
                }

                // 对比拉伸（围绕中点）
                nr = (nr - 128f) * contrastGain + 128f
                ng = (ng - 128f) * contrastGain + 128f
                nb = (nb - 128f) * contrastGain + 128f

                // 提亮
                nr *= brighten; ng *= brighten; nb *= brighten

                // 字迹加深（只压暗部，幅度自适应）
                val lum = (nr * 299 + ng * 587 + nb * 114) / 1000f
                if (lum < 110f) {
                    nr *= darken; ng *= darken; nb *= darken
                }

                rowOut[xx] = (0xFF shl 24) or
                        (clamp(nr).toInt() shl 16) or
                        (clamp(ng).toInt() shl 8) or
                        clamp(nb).toInt()
            }
            out.setPixels(rowOut, 0, w, 0, yy, w, 1)
        }

        // ---- 4) 轻锐化（unsharp mask 3x3）：增强字迹边缘，利于 AI 识别手写体 ----
        val src2 = out
        val out2 = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val rIn = IntArray(w); val rMid = IntArray(w); val rOut = IntArray(w)
        for (yy in 0 until h) {
            if (yy == 0) { src2.getPixels(rMid, 0, w, 0, 0, w, 1); System.arraycopy(rMid, 0, rIn, 0, w) }
            else src2.getPixels(rIn, 0, w, 0, yy - 1, w, 1)
            if (yy > 0) src2.getPixels(rMid, 0, w, 0, yy, w, 1)
            if (yy < h - 1) src2.getPixels(rOut, 0, w, 0, yy + 1, w, 1)
            else System.arraycopy(rMid, 0, rOut, 0, w)
            val rowSharp = IntArray(w)
            for (xx in 0 until w) {
                val p = rMid[xx]
                val amount = 0.45f   // 锐化强度（手写字迹更清晰）
                fun ch(shift: Int): Int {
                    val c = (p shr shift) and 0xFF
                    val l = (rMid[(xx - 1).coerceAtLeast(0)] shr shift) and 0xFF
                    val r = (rMid[(xx + 1).coerceAtMost(w - 1)] shr shift) and 0xFF
                    val u = (rIn[xx] shr shift) and 0xFF
                    val d = (rOut[xx] shr shift) and 0xFF
                    val blur = (l + r + u + d + c * 4) / 8f
                    return clamp(c + (c - blur) * amount * 2.2f).toInt()
                }
                rowSharp[xx] = (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
            out2.setPixels(rowSharp, 0, w, 0, yy, w, 1)
        }
        return out2
    }

    private fun regionLum(src: Bitmap, x0: Int, y0: Int, w: Int, h: Int): Int {
        if (w <= 0 || h <= 0) return 255
        var sum = 0L; var n = 0
        var y = y0
        val step = max(1, min(w, h) / 8)
        while (y < y0 + h && y < src.height) {
            var x = x0
            while (x < x0 + w && x < src.width) {
                val p = src.getPixel(x, y)
                sum += ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
                n++
                x += step
            }
            y += step
        }
        return if (n > 0) (sum / n).toInt() else 255
    }

    private fun clamp(v: Float): Float = max(0f, min(255f, v))

    private fun bgAt(x: Int, y: Int, w: Int, h: Int, grid: FloatArray, GN: Int): FloatArray {
        val gx = min(GN - 1.001f, x.toFloat() / w * GN)
        val gy = min(GN - 1.001f, y.toFloat() / h * GN)
        val x0 = gx.toInt(); val y0 = gy.toInt()
        val x1 = min(GN - 1, x0 + 1); val y1 = min(GN - 1, y0 + 1)
        val fx = gx - x0; val fy = gy - y0
        val out = FloatArray(3)
        for (ch in 0..2) {
            val g00 = grid[y0 * GN + x0]
            val g10 = grid[y0 * GN + x1]
            val g01 = grid[y1 * GN + x0]
            val g11 = grid[y1 * GN + x1]
            out[ch] = (g00 * (1 - fx) + g10 * fx) * (1 - fy) + (g01 * (1 - fx) + g11 * fx) * fy
        }
        return out
    }
}
