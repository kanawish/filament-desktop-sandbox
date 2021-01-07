package com.kanawish

import com.curiouscreature.kotlin.math.*
import com.google.android.filament.*
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.math.cos
import kotlin.math.sin

fun diagnostics() {
    println("-----------")
    System.getProperties().forEach{
        println("${it.key} / ${it.value}")
    }

    println("-----------")
    val resourceName = "/materials/baked_color.filamat"
    println("Quick resource loading test:")
    readUncompressedResourceStream(resourceName).also { buffer ->
        println("""buffer: 
            limit() -> ${buffer.limit()}
            remaining() -> ${buffer.remaining()}
        """)
    }
    println("-----------")

    val max = (Runtime.getRuntime().maxMemory() / 1048576).toInt()
    println("Max mem: $max MB")

    println("Java home: " + System.getProperty("java.home"))
    println("Arch. data model: " + System.getProperty("sun.arch.data.model"))


}

fun main() {
    diagnostics()

    // AWT/OpenGL workarounds
    System.setProperty("sun.java2d.d3d", "false")
    System.setProperty("sun.java2d.noddraw", "true")
    System.setProperty("sun.awt.noerasebackground", "true")
    System.setProperty("sun.awt.erasebackgroundonresize", "false")

    SwingUtilities.invokeLater {
        try {
            awtSandbox()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

}

private fun awtSandbox() {
    val f = JFrame()
    f.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    f.layout = BorderLayout()

    f.size = Dimension(800, 600)
    f.isVisible = true

    val t = Thread {
        Filament.init()
        val entityManager = EntityManager.get()
        val engine = Engine.create()
        val renderer = engine.createRenderer()
        val camera = engine.createCamera(entityManager.create())
        val view = engine.createView()
        val scene = engine.createScene()
        val transformManager = engine.transformManager

        val updateSize = AtomicBoolean(true)

        // TODO: FilamentCanvas should fix jank issues found with FilamentPanel (GC,etc.)
//        val canvas = FilamentCanvas() // FIXME: This stays blank when trying to render.
        val canvas = FilamentPanel() // NOTE: This works rendering triangle, but janks, seems to trigger a lot of GC based on my profiler runs.
        try {
            SwingUtilities.invokeAndWait {
                f.add(canvas, BorderLayout.CENTER)
                f.background = Color.MAGENTA
                canvas.background = null
                f.addComponentListener(object : ComponentAdapter() {
                    override fun componentResized(e: ComponentEvent) {
                        updateSize.set(true)
                    }
                })
                f.revalidate()
            }
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
        }

        try {
            val skybox = Skybox.Builder().color(0.1f, 0.125f, 0.25f, 1.0f).build(engine)
            scene.skybox = skybox

            view.isPostProcessingEnabled = false

            view.scene = scene

            // Material work
            val material = engine.loadMaterial("/materials/baked_color.filamat")

            // Mesh work
            val vertexBuffer = engine.setVertexBuffer(triangleVertices)
            val indexBuffer = engine.setIndexBuffer(triangleVertices)

            // To create a renderable we first create a generic entity
            val renderable = entityManager.create()

            // We then create a renderable component on that entity
            // A renderable is made of several primitives; in this case we declare only 1
            RenderableManager.Builder(1)
                // Overall bounding box of the renderable
                .boundingBox(Box(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.01f))
                // Sets the mesh data of the first primitive
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer, 0, 3)
                // Sets the material of the first primitive
                .material(0, material.defaultInstance)
                .build(engine, renderable)

            // Add the entity to the scene to render it
            scene.addEntity(renderable)
            view.camera = camera

            println("Starting rendering loop...")
            while (true) {
                if (updateSize.compareAndSet(true, false)) {
                    val width = canvas.width
                    val height = canvas.height
                    view.viewport = Viewport(0, 0, width, height)
                    val zoom = 1.5
                    val aspect = width.toDouble() / height.toDouble()
                    camera.setProjection(
                        Camera.Projection.ORTHO,
                        -aspect * zoom, aspect * zoom, -zoom, zoom, 0.0, 10.0)
                    println("VP: " + width + "x" + height)
                }

                // NOTE: Update rotation "fast enough" (originally setup to investigate FilamentPanel jank issue)
                val angle = (System.nanoTime() / NANO.toDouble()) * 65
                val transformMatrix = rotation(Float3(0f, 0f, 1f), angle.toFloat())
                transformManager.setTransform(
                    transformManager.getInstance(renderable),
                    transformMatrix.toFloatArray()
                )

                // Render
                if (canvas.beginFrame(engine, renderer)) {
                    renderer.render(view)
                    canvas.endFrame(renderer)
                }

                try {
                    Thread.sleep(16) // TODO: once FilamentCanvas working, swap in a game-loop approach instead.
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        } finally {
            canvas.destroy(engine)
        }
    }
    t.isDaemon = true
    t.priority = Thread.MAX_PRIORITY
    t.start()
}

data class Vertex(val pos:Float2, val col:Float4) {
     companion object {
         fun sizeOf() = 2 * FLOAT_SIZE + 4 * FLOAT_SIZE
     }
}

val triangleVertices = arrayOf(
    Vertex(Float2(1f,0f),Float4(1f,0f,0f,1f)),
    Vertex(Float2(cos(PI*2/3),sin(PI*2/3)),Float4(0f,1f,0f,1f)),
    Vertex(Float2(cos(PI*4/3),sin(PI*4/3)),Float4(0f,0f,1f,1f))
)
