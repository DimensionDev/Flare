package dev.dimension.flare.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import dev.dimension.flare.media.UgoiraAnimation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.min

internal class UgoiraResolutionUnsupported : Exception()

/** One loop, no audio; source timestamps are never quantized to a constant frame rate. */
internal suspend fun encodeUgoira(
    animation: UgoiraAnimation,
    destination: File,
    smaller: Boolean,
    progress: suspend (Float) -> Unit,
): Unit =
    withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(animation.frames.first().file, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid Ugoira image" }
        val scale = if (smaller) min(1.0, 1920.0 / maxOf(bounds.outWidth, bounds.outHeight)) else 1.0
        val contentWidth = maxOf(1, (bounds.outWidth * scale).toInt())
        val contentHeight = maxOf(1, (bounds.outHeight * scale).toInt())
        val mime = MediaFormat.MIMETYPE_VIDEO_AVC
        val candidates = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder && mime in it.supportedTypes }
        val codecInfo =
            candidates.firstOrNull { info ->
                val caps = info.getCapabilitiesForType(mime)
                val video = caps.videoCapabilities ?: return@firstOrNull false
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible in caps.colorFormats &&
                    video.isSizeSupported(
                        align(contentWidth, maxOf(2, video.widthAlignment)),
                        align(contentHeight, maxOf(2, video.heightAlignment)),
                    )
            } ?: throw UgoiraResolutionUnsupported()
        val caps = codecInfo.getCapabilitiesForType(mime)
        val videoCaps = checkNotNull(caps.videoCapabilities)
        val width = align(contentWidth, maxOf(2, videoCaps.widthAlignment))
        val height = align(contentHeight, maxOf(2, videoCaps.heightAlignment))
        val decodeOptions =
            BitmapFactory.Options().apply {
                if (smaller) {
                    while (bounds.outWidth / (inSampleSize.coerceAtLeast(1) * 2) >= contentWidth &&
                        bounds.outHeight / (inSampleSize.coerceAtLeast(1) * 2) >= contentHeight
                    ) {
                        inSampleSize = inSampleSize.coerceAtLeast(1) * 2
                    }
                }
            }
        val decodedPixels =
            bounds.outWidth.toLong() / decodeOptions.inSampleSize.coerceAtLeast(1) *
                (bounds.outHeight / decodeOptions.inSampleSize.coerceAtLeast(1))
        if ((decodedPixels + width.toLong() * height) * 4 > Runtime.getRuntime().maxMemory() / 2) throw UgoiraResolutionUnsupported()
        val nominalRate = (1000.0 * animation.frames.size / animation.durationMillis).coerceIn(1.0, 60.0)
        val format =
            MediaFormat.createVideoFormat(mime, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(
                    MediaFormat.KEY_BIT_RATE,
                    videoCaps.bitrateRange.clamp(
                        (width.toDouble() * height * nominalRate * .25).coerceIn(4_000_000.0, 100_000_000.0).toInt(),
                    ),
                )
                setInteger(MediaFormat.KEY_FRAME_RATE, ceil(nominalRate).toInt())
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT601_NTSC)
                setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                if (android.os.Build.VERSION.SDK_INT >= 29) setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
            }
        val codec = MediaCodec.createByCodecName(codecInfo.name)
        var muxer: MediaMuxer? = null
        var started = false
        var track = -1
        var codecStarted = false
        var completed = false
        val bufferInfo = MediaCodec.BufferInfo()
        try {
            try {
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            } catch (
                _: IllegalArgumentException,
            ) {
                throw UgoiraResolutionUnsupported()
            }
            codec.start()
            codecStarted = true
            val output = MediaMuxer(destination.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = output
            var inputIndex = 0
            var sentEnd = false
            var outputEnded = false
            var lastProgress = android.os.SystemClock.elapsedRealtime()
            while (!outputEnded) {
                currentCoroutineContext().ensureActive()
                if (!sentEnd) {
                    val bufferIndex = codec.dequeueInputBuffer(10_000)
                    if (bufferIndex >= 0) {
                        if (inputIndex == animation.frames.size) {
                            codec.queueInputBuffer(bufferIndex, 0, 0, animation.durationMillis * 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sentEnd = true
                        } else {
                            val frame = animation.frames[inputIndex]
                            val frameBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(frame.file, frameBounds)
                            require(
                                frameBounds.outWidth == bounds.outWidth && frameBounds.outHeight == bounds.outHeight,
                            ) { "Inconsistent Ugoira frame dimensions" }
                            val decoded = BitmapFactory.decodeFile(frame.file, decodeOptions) ?: error("Invalid Ugoira image")
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try {
                                val canvas = Canvas(bitmap)
                                canvas.drawColor(Color.WHITE)
                                canvas.drawBitmap(decoded, null, Rect(0, 0, contentWidth, contentHeight), Paint(Paint.FILTER_BITMAP_FLAG))
                                decoded.recycle()
                                val inputSize = checkNotNull(codec.getInputBuffer(bufferIndex)).capacity()
                                val image = checkNotNull(codec.getInputImage(bufferIndex)) { "Encoder does not expose YUV input" }
                                // ponytail: CPU conversion handles vendor strides; use a GPU surface if profiling requires it.
                                val pixels = IntArray(width * 2)
                                for (row in 0 until height) {
                                    currentCoroutineContext().ensureActive()
                                    if (row % 2 == 0) bitmap.getPixels(pixels, 0, width, 0, row, width, 2)
                                    for (column in 0 until width) {
                                        val pixel = pixels[(row % 2) * width + column]
                                        val r = Color.red(pixel)
                                        val g = Color.green(pixel)
                                        val b = Color.blue(pixel)
                                        image.planes[0].put(column, row, ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16)
                                        if (row % 2 == 0 && column % 2 == 0) {
                                            var red = 0
                                            var green = 0
                                            var blue = 0
                                            for (y in row..row + 1) {
                                                for (x in column..column + 1) {
                                                    val p = pixels[(y % 2) * width + x]
                                                    red += Color.red(p)
                                                    green += Color.green(p)
                                                    blue += Color.blue(p)
                                                }
                                            }
                                            red /= 4
                                            green /= 4
                                            blue /= 4
                                            image.planes[1].put(
                                                column / 2,
                                                row / 2,
                                                ((-38 * red - 74 * green + 112 * blue + 128) shr 8) + 128,
                                            )
                                            image.planes[2].put(
                                                column / 2,
                                                row / 2,
                                                ((112 * red - 94 * green - 18 * blue + 128) shr 8) + 128,
                                            )
                                        }
                                    }
                                }
                                image.close()
                                codec.queueInputBuffer(bufferIndex, 0, inputSize, animation.frameStartMillis(inputIndex) * 1000, 0)
                            } finally {
                                bitmap.recycle()
                                decoded.recycle()
                            }
                            inputIndex++
                            progress(inputIndex.toFloat() / animation.frames.size)
                        }
                        lastProgress = android.os.SystemClock.elapsedRealtime()
                    }
                }
                var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                while (outputIndex >= 0 || outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        check(!started)
                        track = output.addTrack(codec.outputFormat)
                        output.start()
                        started = true
                    } else {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && bufferInfo.size > 0) {
                            check(started)
                            output.writeSampleData(track, checkNotNull(codec.getOutputBuffer(outputIndex)), bufferInfo)
                        }
                        outputEnded = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                    lastProgress = android.os.SystemClock.elapsedRealtime()
                    outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                }
                check(android.os.SystemClock.elapsedRealtime() - lastProgress < 60_000) { "Ugoira encoder stalled" }
            }
            check(started)
            // MediaMuxer uses this EOS timestamp to retain the final frame's full duration.
            bufferInfo.set(0, 0, animation.durationMillis * 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            output.writeSampleData(track, ByteBuffer.allocate(0), bufferInfo)
            output.stop()
            started = false
            completed = true
        } finally {
            if (codecStarted) runCatching { codec.stop() }
            codec.release()
            if (started) runCatching { muxer?.stop() }
            muxer?.release()
            if (!completed) destination.delete()
        }
    }

private fun align(
    size: Int,
    alignment: Int,
): Int = (size + alignment - 1) / alignment * alignment

private fun android.media.Image.Plane.put(
    x: Int,
    y: Int,
    value: Int,
) {
    buffer.put(y * rowStride + x * pixelStride, value.coerceIn(0, 255).toByte())
}
