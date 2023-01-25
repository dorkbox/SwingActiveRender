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

/**
 * Loop that controls the active rendering process
 */
class ActiveRenderLoop : Runnable {
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
        @Property
        var TARGET_FPS = 30
    }

    override fun run() {
        var lastTime = System.nanoTime()

        // 30 FPS is usually just fine. This isn't a game where we need 60+ FPS. We permit this to be changed though, just in case it is.
        val OPTIMAL_TIME = (1000000000 / TARGET_FPS).toLong()
        var graphics: Graphics? = null

        while (SwingActiveRender.hasActiveRenders) {
            val now = System.nanoTime()
            val updateDeltaNanos = now - lastTime
            lastTime = now

            // not synchronized, because we don't care. The worst case, is one frame of animation behind.
            for (i in SwingActiveRender.activeRenderEvents.indices) {
                val actionHandlerLong = SwingActiveRender.activeRenderEvents[i]
                actionHandlerLong.handle(updateDeltaNanos)
            }

            // this needs to be synchronized because we don't want to our canvas removed WHILE we are rendering it.
            synchronized(SwingActiveRender.activeRenders) {
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
                        if (graphics != null) {
                            graphics!!.dispose()
                        }
                    }
                }
            }

            // Sync the display on some systems (on Linux, this fixes event queue problems)
            Toolkit.getDefaultToolkit().sync()
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
        }
    }
}
