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

package dorkbox.render

import dorkbox.swingActiveRender.SwingActiveRender
import java.awt.*
import javax.swing.JWindow
import javax.swing.SwingUtilities

class RenderTypeTest2 : JWindow() {
    private var squareX = 50
    private val squareY = 50
    private val squareSize = 50

    init {
        isVisible = true
        setSize(WIDTH, HEIGHT)
        setLocationRelativeTo(null)
        ignoreRepaint = true
        createBufferStrategy(2)

        SwingActiveRender.add(this)
    }

    private fun update() {
        // Update the game state (e.g., move the square)
        squareX += 2
        if (squareX > WIDTH) {
            squareX = -squareSize
        }
    }

    override fun paint(g11: Graphics) {
        update()

        // Get the graphics context from the buffer strategy
        val g = bufferStrategy.drawGraphics

        // Draw the square directly on the JWindow
        g.clearRect(0, 0, width, height) // Clear the screen
        g.color = Color.BLUE
        g.fillRect(squareX, squareY, squareSize, squareSize)

        // Dispose the graphics context and show the buffer
        g.dispose()
        bufferStrategy.show()
    }

    companion object {
        private const val WIDTH = 800
        private const val HEIGHT = 600

        fun getMonitorAtLocation(pos: Point): GraphicsDevice? {
            val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
            val screenDevices = ge.screenDevices

            for (device1 in screenDevices) {
                val gc = device1.defaultConfiguration
                val screenBounds = gc.bounds
                if (screenBounds.contains(pos)) {
                    return device1
                }
            }

            return null
        }

        fun showOnSameScreenAsMouse_Center(frame: Container) {
            val mouseLocation = MouseInfo.getPointerInfo().location
            val monitorAtMouse = getMonitorAtLocation(mouseLocation)
            val bounds = monitorAtMouse!!.defaultConfiguration.bounds
            frame.setLocation(bounds.x + bounds.width / 2 - frame.width / 2, bounds.y + bounds.height / 2 - frame.height / 2)
        }

        @JvmStatic
        fun main(args: Array<String>) {
            SwingUtilities.invokeLater {
                val renderTypeTest2 = RenderTypeTest2()
                showOnSameScreenAsMouse_Center(renderTypeTest2)
                renderTypeTest2.isVisible = true
            }
        }
    }
}
