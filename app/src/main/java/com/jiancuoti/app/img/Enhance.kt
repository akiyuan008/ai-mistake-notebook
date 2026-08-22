package com.jiancuoti.app.img

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/** 扫描仪式图像增强：去阴影去底色、提亮纸张、加深字迹 */
object Enhance {

    fun process(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        // 1) 低分辨率背景光照估计（16x16 网格）
        val GN = 16
        val grid = FloatArray(GN * GN * 3)
        val cnt = FloatArray(GN * GN)
        val stepX = max(1, w / 64)
        val stepY = max(1, h / 64)
        val px = IntArray(1)
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                src.getPixel(x, y).let { p ->
                    val gi = min(GN - 1, y * GN / h) * GN + min(GN - 1, x * GN / w)
                    grid[gi * 3] += (p shr 16 and 0xFF).toFloat()
                    grid[gi * 3 + 1] += (p shr 8 and 0xFF).toFloat()
                    grid[gi * 3 + 2] += (p and 0xFF).toFloat()
                    cnt[gi]++
                }
                x += stepX
            }
            y += stepY
        }
        for (i in 0 until GN * GN) {
            if (cnt[i] > 0) {
                grid[i * 3] /= cnt[i]; grid[i * 3 + 1] /= cnt[i]; grid[i * 3 + 2] /= cnt[i]
            } else {
                grid[i * 3] = 255f; grid[i * 3 + 1] = 255f; grid[i * 3 + 2] = 255f
            }
        }

        // 2) 归一化 + 对比度曲线
        val row = IntArray(w)
        for (yy in 0 until h) {
            src.getPixels(row, 0, w, 0, yy, w, 1)
            for (xx in 0 until w) {
                val p = row[xx]
                val r = (p shr 16 and 0xFF).toFloat()
                val g = (p shr 8 and 0xFF).toFloat()
                val b = (p and 0xFF).toFloat()
                val bg = bgAt(xx, yy, w, h, grid, GN)
                var nr = norm(r, bg[0])
                var ng = norm(g, bg[1])
                var nb = norm(b, bg[2])
                // 加深字迹
                if (nr < 120) nr *= 0.82f
                if (ng < 120) ng *= 0.82f
                if (nb < 120) nb *= 0.82f
                row[xx] = (0xFF shl 24) or
                        (clamp(nr).toInt() shl 16) or
                        (clamp(ng).toInt() shl 8) or
                        clamp(nb).toInt()
            }
            out.setPixels(row, 0, w, 0, yy, w, 1)
        }
        return out
    }

    private fun norm(v: Float, bg: Float): Float {
        var n = v / max(50f, bg) * 255f
        n = (n - 128f) * 1.18f + 128f
        return n
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
            val g00 = grid[(y0 * GN + x0) * 3 + ch]
            val g10 = grid[(y0 * GN + x1) * 3 + ch]
            val g01 = grid[(y1 * GN + x0) * 3 + ch]
            val g11 = grid[(y1 * GN + x1) * 3 + ch]
            out[ch] = (g00 * (1 - fx) + g10 * fx) * (1 - fy) + (g01 * (1 - fx) + g11 * fx) * fy
        }
        return out
    }
}
