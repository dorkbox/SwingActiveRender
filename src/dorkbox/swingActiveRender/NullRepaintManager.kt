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

import javax.swing.JComponent
import javax.swing.RepaintManager
import javax.swing.SwingUtilities

/**
 * The NullRepaintManager is a RepaintManager that doesn't do any repainting. Useful when all of the rendering is done manually by the
 * application.
 */
class NullRepaintManager : RepaintManager() {
    companion object {
        /**
         * Installs the NullRepaintManager onto the EDT (WARNING: This disables painting/rendering by the EDT, for the entire JVM)
         */
        fun install() {
            SwingUtilities.invokeLater {
                val repaintManager: RepaintManager = NullRepaintManager()
                repaintManager.isDoubleBufferingEnabled = false
                setCurrentManager(repaintManager)
            }
        }
    }

    override fun addInvalidComponent(c: JComponent) {
        // do nothing
    }

    override fun addDirtyRegion(c: JComponent, x: Int, y: Int, w: Int, h: Int) {
        // do nothing
    }

    override fun markCompletelyDirty(c: JComponent) {
        // do nothing
    }

    override fun paintDirtyRegions() {
        // do nothing
    }
}
