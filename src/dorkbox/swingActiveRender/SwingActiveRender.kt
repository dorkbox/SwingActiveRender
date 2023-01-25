/*
 * Copyright 2023 dorkbox, llc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dorkbox.swingActiveRender

import dorkbox.updates.Updates.add
import java.awt.Component
import java.awt.EventQueue
import java.util.concurrent.*
import javax.swing.SwingUtilities

/**
 * Contains all the appropriate logic to setup and render via "Active" rendering (instead of "Passive" rendering).
 *
 * This permits us to render components OFF of the EDT - even though there are other frames/components that are ON the EDT.
 * <br></br>
 * Because we still want to react to mouse events, etc on the EDT, we do not completely remove the EDT -- we merely allow us
 * to "synchronize" the EDT object to our thread. It's a little-bit hacky, but it works beautifully, and permits MUCH nicer animations.
 * <br></br>
 */
@Suppress("MemberVisibilityCanBePrivate")
object SwingActiveRender {
    private var activeRenderThread: Thread? = null

    val activeRenders: MutableList<Component> = ArrayList()
    val activeRenderEvents: MutableList<ActionHandlerLong> = CopyOnWriteArrayList()

    // volatile, so that access triggers thread synchrony, since 1.6. See the Java Language Spec, Chapter 17
    @Volatile
    var hasActiveRenders = false

    private val renderLoop: Runnable = ActiveRenderLoop()

    /**
     * Gets the version number.
     */
    val version = "1.2"

    init {
        // Add this project to the updates system, which verifies this class + UUID + version information
        add(SwingActiveRender::class.java, "0dfec3d996f3420d82a864c6cd5a2646", version)
    }

    /**
     * Enables the component to to added to an "Active Render" thread, at a target "Frames-per-second". This is to support smooth, swing-based
     * animations.
     *
     *
     * This works by removing this object from EDT updates and manually calls paint on the component, updating it on our own thread, but
     * still remaining synchronized with the EDT.
     *
     * @param component the component to add to the ActiveRender thread.
     */
    fun addActiveRender(component: Component) {
        // this should be on the EDT
        if (!EventQueue.isDispatchThread()) {
            SwingUtilities.invokeLater { addActiveRender(component) }
            return
        }
        component.ignoreRepaint = true

        synchronized(activeRenders) {
            if (!hasActiveRenders) {
                setupActiveRenderThread()
            }

            hasActiveRenders = true
            activeRenders.add(component)
        }
    }

    /**
     * Removes a component from the ActiveRender queue. This should happen when the component is closed.
     *
     * @param component the component to remove
     */
    fun removeActiveRender(component: Component) {
        // this should be on the EDT
        if (!EventQueue.isDispatchThread()) {
            SwingUtilities.invokeLater { removeActiveRender(component) }
            return
        }

        synchronized(activeRenders) {
            activeRenders.remove(component)
            val hadActiveRenders = activeRenders.isNotEmpty()
            hasActiveRenders = hadActiveRenders
            if (!hadActiveRenders) {
                activeRenderThread = null
            }
        }

        component.ignoreRepaint = false
    }

    /**
     * Specifies an ActionHandler to be called when the ActiveRender thread starts to render at each tick.
     *
     * @param handler the handler to add
     */
    fun addActiveRenderFrameStart(handler: ActionHandlerLong) {
        synchronized(activeRenders) {
            activeRenderEvents.add(handler)
        }
    }

    /**
     * Potentially SLOW calculation, as it compares each entry in a queue for equality
     *
     * @param handler this is the handler to check
     *
     * @return true if this handler already exists in the active render, on-frame-start queue
     */
    fun containsActiveRenderFrameStart(handler: ActionHandlerLong): Boolean {
        synchronized(activeRenders) {
            return activeRenderEvents.contains(handler)
        }
    }

    /**
     * Removes the handler from the on-frame-start queue
     *
     * @param handler the handler to remove
     */
    fun removeActiveRenderFrameStart(handler: ActionHandlerLong) {
        synchronized(activeRenders) {
            activeRenderEvents.remove(handler)
        }
    }

    /**
     * Creates (if necessary) the active-render thread. When there are no active-render targets, this thread will exit
     */
    private fun setupActiveRenderThread() {
        if (activeRenderThread != null) {
            return
        }

        SynchronizedEventQueue.install()

        activeRenderThread = Thread(renderLoop, "AWT-ActiveRender")
        activeRenderThread!!.isDaemon = true
        activeRenderThread!!.start()
    }
}
