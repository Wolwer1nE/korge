package com.soywiz.korge.view

import com.soywiz.klock.*
import com.soywiz.korge.internal.*
import com.soywiz.korge.view.*
import com.soywiz.korim.bitmap.Bitmap
import com.soywiz.korim.bitmap.BmpSlice
import com.soywiz.korio.async.Signal
import com.soywiz.korma.geom.vector.VectorPath

inline fun Container.sprite(
    initialAnimation: SpriteAnimation, anchorX: Double = 0.0, anchorY: Double = 0.0, callback: @ViewsDslMarker Sprite.() -> Unit = {}
): Sprite = Sprite(initialAnimation, anchorX, anchorY).addTo(this).apply(callback)

inline fun Container.sprite(
    texture: BmpSlice, anchorX: Double = 0.0, anchorY: Double = 0.0, callback: @ViewsDslMarker Sprite.() -> Unit = {}
): Sprite = Sprite(texture, anchorX, anchorY).addTo(this).apply(callback)

inline fun Container.sprite(
    texture: Bitmap, anchorX: Double = 0.0, anchorY: Double = 0.0, callback: @ViewsDslMarker Sprite.() -> Unit = {}
): Sprite = Sprite(texture, anchorX, anchorY).addTo(this).apply(callback)

/**
 * A [Sprite] is basically an [Image] with added abilities to display a [SpriteAnimation]
 * The regular usage is to initialize the [Sprite] with one [SpriteAnimation]. The first
 * displayed bitmap will be the first element of the [SpriteAnimation]s spriteStack.
 * @property animationRequested Boolean
 * @property animationNumberOfFramesRequested Int
 * @property onAnimationCompleted Signal<SpriteAnimation>
 * @property onAnimationStopped Signal<SpriteAnimation>
 * @property onAnimationStarted Signal<SpriteAnimation>
 * @property animationLooped Boolean
 * @property lastAnimationFrameTime TimeSpan
 * @property animationRequestedDuration TimeSpan
 * @property spriteDisplayTime TimeSpan
 * @property currentAnimation SpriteAnimation?
 * @property currentSpriteIndex Int
 * @constructor It is possible to initialize a [Sprite] with a static [Bitmap] or [BmpSlice].
 * This will be exchanged when starting a [SpriteAnimation] with one of the available play functions
 */
open class Sprite(
    bitmap: Bitmap,
    anchorX: Double = 0.0,
    anchorY: Double = anchorX,
    hitShape: VectorPath? = null,
    smoothing: Boolean = true) : Image(bitmap, anchorX, anchorY, hitShape, smoothing) {
    constructor(
        bmpSlice: BmpSlice,
        anchorX: Double = 0.0,
        anchorY: Double = anchorX,
        hitShape: VectorPath? = null,
        smoothing: Boolean = true) : this(bmpSlice.bmp, anchorX, anchorY, hitShape, smoothing)

    constructor(
        initialAnimation: SpriteAnimation,
        anchorX: Double = 0.0,
        anchorY: Double = anchorX,
        hitShape: VectorPath? = null,
        smoothing: Boolean = true) : this(initialAnimation.firstSprite, anchorX, anchorY, hitShape, smoothing) {
        currentAnimation = initialAnimation
        setFrame(0)
    }

    private var animationRequested = false
    private var animationNumberOfFramesRequested = 0
        set(value) {
            if (value == 0) {
                stopAnimation()
                when (animationType) {
                    AnimationType.STANDARD -> triggerEvent(onAnimationCompleted)
                    else -> triggerEvent(onAnimationStopped)
                }
            }
            field = value
        }
    private var animationType = AnimationType.STANDARD

    private var _onAnimationCompleted: Signal<SpriteAnimation>? = null
    private var _onAnimationStopped: Signal<SpriteAnimation>? = null
    private var _onAnimationStarted: Signal<SpriteAnimation>? = null

    val onAnimationCompleted : Signal<SpriteAnimation>
        get(){
            if (_onAnimationCompleted == null) _onAnimationCompleted = Signal()
            return _onAnimationCompleted!!
        }

    val onAnimationStopped : Signal<SpriteAnimation>
        get() {
            if (_onAnimationStopped == null) _onAnimationStopped = Signal()
            return _onAnimationStopped!!
        }

    val onAnimationStarted : Signal<SpriteAnimation>
        get() {
            if (_onAnimationStarted == null) _onAnimationStarted = Signal()
            return _onAnimationStarted!!
        }

    var spriteDisplayTime: TimeSpan = 50.ms
    private var animationLooped = false
    private var lastAnimationFrameTime: TimeSpan = 0.ms
    private var animationRemainingDuration: TimeSpan = 0.ms
        set(value) {
            if (value <= 0.ms && animationType == AnimationType.DURATION) {
                stopAnimation()
                triggerEvent(_onAnimationCompleted)
            }
            field = value
        }

    private var currentAnimation: SpriteAnimation? = null
    private var currentSpriteIndex = 0
        set(value) {
            setFrame(value)
            field = value
        }
    private var reversed = false

    init {
        addUpdater { frameTime ->
            if (animationRequested) {
                nextSprite(frameTime)
            }
        }
    }

    fun playAnimation(
        spriteAnimation: SpriteAnimation? = currentAnimation,
        spriteDisplayTime: TimeSpan = this.spriteDisplayTime,
        startFrame: Int = -1,
        reversed: Boolean = false) =
        updateCurrentAnimation(
            spriteAnimation = spriteAnimation,
            spriteDisplayTime = spriteDisplayTime,
            startFrame = if (startFrame >= 0) startFrame else currentSpriteIndex,
            reversed = reversed,
            type = AnimationType.STANDARD)

    fun playAnimation(
        times: Int = 1,
        spriteAnimation: SpriteAnimation? = currentAnimation,
        spriteDisplayTime: TimeSpan = this.spriteDisplayTime,
        startFrame: Int = -1,
        reversed: Boolean = false) =
        updateCurrentAnimation(
            spriteAnimation = spriteAnimation,
            spriteDisplayTime = spriteDisplayTime,
            animationCyclesRequested = times,
            startFrame = if (startFrame >= 0) startFrame else currentSpriteIndex,
            reversed = reversed,
            type = AnimationType.STANDARD
        )

    fun playAnimationForDuration(
        duration: TimeSpan,
        spriteAnimation: SpriteAnimation? = currentAnimation,
        spriteDisplayTime: TimeSpan = this.spriteDisplayTime,
        startFrame: Int = -1,
        reversed: Boolean = false) =
        updateCurrentAnimation(
            spriteAnimation = spriteAnimation,
            spriteDisplayTime = spriteDisplayTime,
            duration = duration,
            startFrame = if (startFrame >= 0) startFrame else currentSpriteIndex,
            reversed = reversed,
            type = AnimationType.DURATION
        )

    fun playAnimationLooped(
        spriteAnimation: SpriteAnimation? = currentAnimation,
        spriteDisplayTime: TimeSpan = this.spriteDisplayTime,
        startFrame: Int = -1,
        reversed: Boolean = false) =
        updateCurrentAnimation(
            spriteAnimation = spriteAnimation,
            spriteDisplayTime = spriteDisplayTime,
            startFrame = if (startFrame >= 0) startFrame else currentSpriteIndex,
            looped = true,
            reversed = reversed,
            type = AnimationType.LOOPED
        )

    fun stopAnimation() {
        animationRequested = false
        triggerEvent(_onAnimationStopped)
    }

    private fun nextSprite(frameTime: TimeSpan) {
        lastAnimationFrameTime += frameTime
        if (lastAnimationFrameTime + frameTime >= this.spriteDisplayTime) {
            when (animationType) {
                AnimationType.STANDARD -> {
                    if (animationNumberOfFramesRequested > 0) {
                        animationNumberOfFramesRequested--
                    }
                }
                AnimationType.DURATION -> {
                    animationRemainingDuration -= lastAnimationFrameTime
                }
                AnimationType.LOOPED -> {

                }
            }
            setFrame(if (reversed) --currentSpriteIndex else ++currentSpriteIndex)
            lastAnimationFrameTime = 0.ms
        }
    }

    private fun updateCurrentAnimation(
        spriteAnimation: SpriteAnimation?,
        spriteDisplayTime: TimeSpan = this.spriteDisplayTime,
        animationCyclesRequested: Int = 1,
        duration: TimeSpan = 0.ms,
        startFrame: Int = 0,
        looped: Boolean = false,
        reversed: Boolean = false,
        type: AnimationType = AnimationType.STANDARD
    ) {
        triggerEvent(_onAnimationStarted)
        this.spriteDisplayTime = spriteDisplayTime
        currentAnimation = spriteAnimation
        animationLooped = looped
        animationRemainingDuration = duration
        currentSpriteIndex = startFrame
        this.reversed = reversed
        animationType = type
        animationRequested = true
        currentAnimation?.let {
            this.animationNumberOfFramesRequested = if (!looped) animationCyclesRequested * it.spriteStackSize else it.spriteStackSize
        }
    }

    fun setFrame(index: Int) {
        bitmap = currentAnimation?.getSprite(index) ?: bitmap
    }

    private fun triggerEvent(signal: Signal<SpriteAnimation>?) {
        if (signal != null) currentAnimation?.let { signal.invoke(it) }
    }
}

enum class AnimationType {
    STANDARD, LOOPED, DURATION
}
