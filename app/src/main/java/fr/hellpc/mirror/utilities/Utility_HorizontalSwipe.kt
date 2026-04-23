/*
 * Copyright (c) 2026 HellPC (https://github.com/He11PC).
 * This file is part of Mirror, multiprotocol backup application.
 *
 * Mirror is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * See AGENTS.md for AI usage policy.
 *
 * This program is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details <https://www.gnu.org/licenses/>.
 */

package fr.hellpc.mirror.utilities

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

abstract class Utility_HorizontalSwipe(val context: Context) : View.OnTouchListener {

    companion object {
        const val SWIPE_MIN = 50
        const val SWIPE_VELOCITY_MIN = 100
    }

    private val detector = GestureDetector(context, GestureListener())

    override fun onTouch(view: View, event: MotionEvent) = detector.onTouchEvent(event)

    abstract fun onRightSwipe()

    abstract fun onLeftSwipe()

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float) : Boolean {
            if(e1 == null) return false

            val deltaY = e2.y - (e1.y)
            val deltaX = e2.x - (e1.x)

            if(abs(deltaX) < abs(deltaY)) return false

            if(abs(deltaX) < SWIPE_MIN && abs(velocityX) < SWIPE_VELOCITY_MIN) return false

            if(deltaX > 0) onRightSwipe() else onLeftSwipe()

            return true
        }
    }
}