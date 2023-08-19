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

import dorkbox.propertyLoader.Property
import java.awt.Graphics
import java.awt.Toolkit
import java.util.concurrent.locks.*
import kotlin.concurrent.withLock

/**
 * Loop that controls the active rendering process
 */
class ActiveRenderLoop : Runnable {
    @Volatile
    lateinit var currentThread: Thread

    private val lock = ReentrantLock()
    private var started = false;
    private val condition = lock.newCondition()
    private val runTimeCondition = lock.newCondition()

    companion object {
        /**
         * How many frames per second we want the Swing ActiveRender thread to run at
         *
         * ### NOTE:
         *
         * The ActiveRenderLoop replaces the Swing EDT (only for specified JFrames) in order to enable smoother animations. It is also
         * important to REMEMBER -- if you add a component to an actively managed JFrame, YOU MUST make sure to call
         * [javax.swing.JComponent.setIgnoreRepaint] otherwise this component will "fight" on the EDT for updates.
         *
         * You can ALSO completely disable the Swing EDT by calling [NullRepaintManager.install]
         */
        @Property(description = "How many frames per second the Swing ActiveRender thread will run at.")
        @Volatile
        var TARGET_FPS = 30
    }

    override fun run() {
        currentThread = Thread.currentThread()

        lock.withLock {
            started = true
            condition.signal()
        }

        var lastTime = System.nanoTime()
        val defaultToolkit = Toolkit.getDefaultToolkit()

        // 30 FPS is usually just fine. This isn't a game where we need 60+ FPS. We permit this to be changed though, just in case it is.
        @Suppress("LocalVariableName")
        val OPTIMAL_TIME = (1000000000 / TARGET_FPS).toLong()
        var graphics: Graphics? = null

        while (true) {
            // if we have NO active renderers, just wait for one.

            while (SwingActiveRender.hasActiveRenders) {
                try {
                    val now = System.nanoTime()
                    val updateDeltaNanos = now - lastTime
                    lastTime = now

                    // is a copy-on-write, and it must eventually be correct.
                    val renderEvents = SwingActiveRender.activeRenderEvents
                    renderEvents.forEach {
                        it.invoke(updateDeltaNanos)
                    }

                    // this needs to be synchronized because we don't want to our canvas removed WHILE we are rendering it.
                    val activeRenders = SwingActiveRender.activeRenders
                    for (component in activeRenders) {
                        if (!component.isDisplayable) {
                            continue
                        }

                        // maybe the frame was closed, so we must be in a try/catch issue #11
                        try {
                            graphics = component.graphics
                            component.paint(graphics)
                        } catch (e: Exception) {
                            // the canvas can be closed as well. can get a "java.lang.IllegalStateException: Component must have a valid peer" if
                            // it's already be closed during the getDrawGraphics call.
                            e.printStackTrace()
                        } finally {
                            graphics?.dispose()

                            // Sync the display on some systems (on macOS/Linux, this fixes event queue problems)
                            // This must be synchronized for every component (sadly) otherwise only the NEWEST component will get updated
                            defaultToolkit.sync()
                        }
                    }

                    try {
                        // Converted to int before the division, because IDIV is
                        // 1 order magnitude faster than LDIV (and INTs work for us anyways)
                        // see: http://www.cs.nuim.ie/~jpower/Research/Papers/2008/lambert-qapl08.pdf
                        // Also, down-casting (long -> int) is not expensive w.r.t IDIV/LDIV
                        val l = (lastTime - System.nanoTime() + OPTIMAL_TIME).toInt()
                        val millis = l / 1000000

                        if (millis > 1) {
                            Thread.sleep(millis.toLong())
                        } else {
                            // try to keep the CPU from getting slammed. We couldn't match our target FPS, so loop again
                            Thread.yield()
                        }
                    } catch (ignored: InterruptedException) {
                    }
                } catch (e: Exception) {
                    // there was an unexpected problem
                    e.printStackTrace()
                }
            }

            lock.withLock {
                runTimeCondition.await()
            }
        }
    }

    fun signalStart() {
        lock.withLock {
            runTimeCondition.signal()
        }
    }

    fun waitForStartup() {
        lock.withLock {
            if (!started) {
                condition.await()
            }
        }
    }
}
