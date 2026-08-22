package com.jiancuoti.app.img

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/** 八点透视裁剪：单应性变换 */
object Perspective {

    data class Pt(var x: Float, var y: Float)

    /** 将源图按四角点(像素坐标)透视矫正为矩形输出 */
    fun warp(src: Bitmap, srcPts: List<Pt>, outW: Int, outH: Int): Bitmap {
        val dstPts = listOf(Pt(0f, 0f), Pt(outW.toFloat(), 0f), Pt(outW.toFloat(), outH.toFloat()), Pt(0f, outH.toFloat()))
        val H = solveHomography(srcPts, dstPts)
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        if (H == null) {
            // 降级：包围盒裁剪
            val xs = srcPts.map { it.x }; val ys = srcPts.map { it.y }
            val x0 = max(0f, xs.minOrNull() ?: 0f).toInt()
            val y0 = max(0f, ys.minOrNull() ?: 0f).toInt()
            val x1 = min(src.width - 1f, xs.maxOrNull() ?: 0f).toInt()
            val y1 = min(src.height - 1f, ys.maxOrNull() ?: 0f).toInt()
            return try {
                Bitmap.createBitmap(src, x0, y0, max(2, x1 - x0), max(2, y1 - y0))
            } catch (_: Exception) { out }
        }
        val h1 = H[0]; val h2 = H[1]; val h3 = H[2]; val h4 = H[3]
        val h5 = H[4]; val h6 = H[5]; val h7 = H[6]; val h8 = H[7]
        val sw = src.width; val sh = src.height
        val srcRow = IntArray(sw)
        val outRow = IntArray(outW)
        for (yy in 0 until outH) {
            for (xx in 0 until outW) {
                val den = h7 * xx + h8 * yy + 1f
                val sx = (h1 * xx + h2 * yy + h3) / den
                val sy = (h4 * xx + h5 * yy + h6) / den
                outRow[xx] = if (sx < 0f || sy < 0f || sx > sw - 1f || sy > sh - 1f) 0
                else bilinear(src, sx, sy)
            }
            out.setPixels(outRow, 0, outW, 0, yy, outW, 1)
        }
        return out
    }

    private val rowCache = HashMap<Int, IntArray>()
    private fun bilinear(src: Bitmap, sx: Float, sy: Float): Int {
        val x0 = sx.toInt(); val y0 = sy.toInt()
        val fx = sx - x0; val fy = sy - y0
        val x1 = min(x0 + 1, src.width - 1); val y1 = min(y0 + 1, src.height - 1)
        val r0 = rowCache.getOrPut(y0) { IntArray(src.width).also { src.getPixels(it, 0, src.width, 0, y0, src.width, 1) } }
        val r1 = if (y1 == y0) r0 else rowCache.getOrPut(y1) { IntArray(src.width).also { src.getPixels(it, 0, src.width, 0, y1, src.width, 1) } }
        val p00 = r0[x0]; val p10 = r0[x1]; val p01 = r1[x0]; val p11 = r1[x1]
        fun ch(shift: Int): Int {
            val v = (((p00 shr shift and 0xFF) * (1 - fx) + (p10 shr shift and 0xFF) * fx) * (1 - fy)
                    + ((p01 shr shift and 0xFF) * (1 - fx) + (p11 shr shift and 0xFF) * fx) * fy)
            return v.toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 8 * 2) or (ch(8) shl 8) or ch(0)
    }

    /** 解 8 参数单应性（dst -> src 映射），高斯消元 */
    private fun solveHomography(srcPts: List<Pt>, dstPts: List<Pt>): FloatArray? {
        val A = Array(8) { FloatArray(8) }
        val B = FloatArray(8)
        for (i in 0..3) {
            val x = dstPts[i].x; val y = dstPts[i].y
            val X = srcPts[i].x; val Y = srcPts[i].y
            A[2 * i][0] = x; A[2 * i][1] = y; A[2 * i][2] = 1f
            A[2 * i][6] = -x * X; A[2 * i][7] = -y * X; B[2 * i] = X
            A[2 * i + 1][3] = x; A[2 * i + 1][4] = y; A[2 * i + 1][5] = 1f
            A[2 * i + 1][6] = -x * Y; A[2 * i + 1][7] = -y * Y; B[2 * i + 1] = Y
        }
        for (col in 0..7) {
            var piv = col
            for (r in col + 1..7) if (kotlin.math.abs(A[r][col]) > kotlin.math.abs(A[piv][col])) piv = r
            val ta = A[col]; A[col] = A[piv]; A[piv] = ta
            val tb = B[col]; B[col] = B[piv]; B[piv] = tb
            if (kotlin.math.abs(A[col][col]) < 1e-9f) return null
            for (r in 0..7) {
                if (r == col) continue
                val f = A[r][col] / A[col][col]
                for (c in col..7) A[r][c] -= f * A[col][c]
                B[r] -= f * B[col]
            }
        }
        return FloatArray(8) { B[it] / A[it][it] }
    }
}
