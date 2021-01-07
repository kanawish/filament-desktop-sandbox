package com.kanawish

import com.curiouscreature.kotlin.math.Float2
import com.curiouscreature.kotlin.math.Float3
import com.curiouscreature.kotlin.math.Float4
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.VertexBuffer
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.InputStream
import java.lang.reflect.InvocationTargetException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels

const val INT_SIZE = 4
const val FLOAT_SIZE = 4
const val SHORT_SIZE = 2

fun Array<Vertex>.bufferSize() = this.size * Vertex.sizeOf()

fun ByteBuffer.putFloat2(float2: Float2): ByteBuffer {
    putFloat(float2.x)
    putFloat(float2.y)
    return this
}

fun ByteBuffer.putFloat3(float3: Float3): ByteBuffer {
    putFloat(float3.x)
    putFloat(float3.y)
    putFloat(float3.z)
    return this
}

fun ByteBuffer.putFloat4(float4: Float4): ByteBuffer {
    putFloat(float4.x)
    putFloat(float4.y)
    putFloat(float4.z)
    putFloat(float4.w)
    return this
}

fun ByteBuffer.put(v: Vertex): ByteBuffer {
    putFloat(v.pos.x)
    putFloat(v.pos.y)
    putFloat(v.col.x)
    putFloat(v.col.y)
    putFloat(v.col.z)
    putFloat(v.col.w)
    return this
}

private fun findResourceFile(resourceFilename:String): File {
    return File(object {}.javaClass.getResource(resourceFilename).file)
}

private fun findResourceInputStream(resourceName: String): InputStream {
    return object {}.javaClass.getResourceAsStream(resourceName)
}

/**
 *
 * Notes on `use`, see https://www.baeldung.com/kotlin-try-with-resources
 *
 */
private fun readUncompressedResourceFile(resourceFilename: String): ByteBuffer {
    val file = findResourceFile(resourceFilename)
    file.inputStream().use { inputStream ->
        val dst = ByteBuffer.allocate(file.length().toInt())
        val src = Channels.newChannel(inputStream)
        // NOTE: `src.use{ }` seems redundant given parent `use {}`, but original called "close()" so TBD
        src.use { src.read(dst) }

        return dst.apply { rewind() }
    }
}

fun readUncompressedResourceStream(resourceName: String): ByteBuffer {
    findResourceInputStream(resourceName).use { inputStream ->
        val allBytes = inputStream.readAllBytes()
        val dst = ByteBuffer.wrap(allBytes)
        val src = Channels.newChannel(inputStream)
        // NOTE: `src.use{ }` seems redundant given parent `use {}`, but original called "close()" so TBD
        src.use { src.read(dst) }

        return dst.apply { rewind() }
    }
}

fun walkResource(filename: String) {
    val resourceFile = findResourceFile(filename)
    resourceFile
        .walkTopDown()
        .forEach { file ->
            if(file.isFile) println(file.absoluteFile)
        }
}

fun Engine.loadMaterial(resourceName: String): Material {
    return readUncompressedResourceStream(resourceName).let { buffer ->
        Material.Builder()
            .payload(buffer, buffer.remaining())
            .build(this)
    }
}

fun Engine.setVertexBuffer(vertices:Array<Vertex>): VertexBuffer {
    val vertexData = ByteBuffer.allocate(vertices.bufferSize())
        .order(ByteOrder.nativeOrder())
        .apply {
            vertices.forEach { v ->
                putFloat2(v.pos)
                putFloat4(v.col)
            }
        }
        .flip()

    val vertexBuffer = VertexBuffer.Builder()
        .bufferCount(1)
        .vertexCount(vertices.size)
        .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT2, 0, Vertex.sizeOf())
        .attribute(VertexBuffer.VertexAttribute.COLOR, 0, VertexBuffer.AttributeType.FLOAT4, 2 * FLOAT_SIZE, Vertex.sizeOf())
        .build(this)

    vertexBuffer.setBufferAt(this, 0, vertexData)

    return vertexBuffer
}

fun Engine.setIndexBuffer(vertices:Array<Vertex>): IndexBuffer {
    val indexData = ByteBuffer.allocate(vertices.size * SHORT_SIZE)
        .order(ByteOrder.nativeOrder())
        .apply { for (i in vertices.indices) putShort(i.toShort()) }
        .flip()

    val indexBuffer = IndexBuffer.Builder()
        .indexCount(vertices.size)
        .bufferType(IndexBuffer.Builder.IndexType.USHORT)
        .build(this)
    indexBuffer.setBuffer(this,indexData)
    return indexBuffer
}

const val DEFAULT_SCALE_FACTOR = 1

fun calculateDeviceScaleFactor():Int {
    val defaultScreenDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
    val screenDeviceClass: Class<*> = defaultScreenDevice.javaClass
    if (screenDeviceClass.canonicalName == "sun.awt.CGraphicsDevice") {
        try {
            val getScaleFactorMethod = screenDeviceClass.getDeclaredMethod("getScaleFactor")
            val result = getScaleFactorMethod.invoke(defaultScreenDevice)
            return result as Int
        } catch (e: NoSuchMethodException) {
            e.printStackTrace()
            System.err.println("Unable to determine scale factor of screen, defaulting to 1")
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
            System.err.println("Unable to determine scale factor of screen, defaulting to 1")
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
            System.err.println("Unable to determine scale factor of screen, defaulting to 1")
        }
    }

    return DEFAULT_SCALE_FACTOR
}

const val MILI = 1000L
const val NANO = 1000000000L
