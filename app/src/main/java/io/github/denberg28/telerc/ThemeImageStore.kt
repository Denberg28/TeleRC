package io.github.denberg28.telerc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** Decode off the UI thread. A failed import never replaces the existing image. */
object ThemeImageStore {
    private fun file(context: Context) = File(context.filesDir, "theme-background.jpg")
    fun exists(context: Context) = file(context).isFile
    fun load(context: Context): Bitmap? = if (exists(context)) BitmapFactory.decodeFile(file(context).path) else null
    fun remove(context: Context) { AtomicFile(file(context)).delete() }
    fun importImage(context: Context, uri: Uri): Bitmap {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16384)
            var count = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                count += read
                require(count <= ThemeImagePolicy.MAX_BYTES) { "Choose an image smaller than 20 MB" }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: error("Image could not be read")
        require(bytes.isNotEmpty()) { "Image is empty" }
        val bitmap = if (Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            val size = ThemeImagePolicy.target(info.size.width, info.size.height) ?: error("Image dimensions unsupported")
            decoder.setTargetSize(size.first, size.second)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val sample = ThemeImagePolicy.sample(bounds.outWidth, bounds.outHeight)
            require(sample > 0) { "Image format unsupported" }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("Image cannot be decoded")
            val orientation = try { ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) } catch (_: Exception) { 1 }
            val matrix = Matrix().apply { when (orientation) {
                2 -> setScale(-1f, 1f); 3 -> setRotate(180f); 4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }; 6 -> setRotate(90f)
                7 -> { setRotate(270f); postScale(-1f, 1f) }; 8 -> setRotate(270f)
            } }
            if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { if (it !== decoded) decoded.recycle() }
        }
        val output = AtomicFile(file(context))
        val stream = output.startWrite()
        try {
            require(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)) { "Image could not be saved" }
            output.finishWrite(stream)
        } catch (error: Exception) { output.failWrite(stream); throw error }
        return bitmap
    }
}
