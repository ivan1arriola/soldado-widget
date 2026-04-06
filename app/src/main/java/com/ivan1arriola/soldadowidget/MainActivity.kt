package com.ivan1arriola.soldadowidget

import android.content.Intent
import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

class MainActivity : AppCompatActivity() {

    private var taps = 0
    private var guardMode = false
    private var idleAnimator: AnimatorSet? = null
    private lateinit var soldierImage: ImageView
    private lateinit var soldierPhrase: TextView

    private val soldierFrames = listOf(
        R.drawable.soldado_frame_0,
        R.drawable.soldado_frame_1,
        R.drawable.soldado_frame_2,
        R.drawable.soldado_frame_3,
        R.drawable.soldado_frame_4,
        R.drawable.soldado_frame_5
    )

    private val tapPhraseRes = listOf(
        R.string.soldado_phrase_1,
        R.string.soldado_phrase_2,
        R.string.soldado_phrase_3,
        R.string.soldado_phrase_4,
        R.string.soldado_phrase_5,
        R.string.soldado_phrase_idle
    )

    private val orderReactions = listOf(
        R.drawable.soldado_frame_1 to R.string.soldado_phrase_order_march,
        R.drawable.soldado_frame_0 to R.string.soldado_phrase_order_scan,
        R.drawable.soldado_frame_2 to R.string.soldado_phrase_order_salute
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val rootLayout = findViewById<View>(R.id.rootLayout)
        applySystemInsets(rootLayout)

        val soldierContainer = findViewById<View>(R.id.soldierContainer)
        soldierImage = findViewById(R.id.mainSoldierImage)
        soldierPhrase = findViewById(R.id.mainSoldierPhrase)

        val btnOrder = findViewById<Button>(R.id.btnOrder)
        val btnReset = findViewById<Button>(R.id.btnReset)
        val btnGoToConfig = findViewById<Button>(R.id.btnGoToConfig)

        btnGoToConfig.setOnClickListener {
            startActivity(Intent(this, ConfigActivity::class.java))
        }

        // Toque corto: reaccion aleatoria o modo guardia.
        soldierContainer.setOnClickListener {
            taps++
            FeedbackEffects.playTap(this)

            playTapReaction()

            if (guardMode) {
                soldierImage.setImageResource(R.drawable.soldado_frame_2)
                soldierPhrase.text = getString(R.string.soldado_phrase_guard_on)
                emphasizePhraseBubble()
                return@setOnClickListener
            }

            val nextFrame = when {
                taps > 15 -> 3 // Agotado
                taps > 8 -> (1..5).random() // Muy inquieto
                else -> listOf(0, 1, 4, 5).random() // Movimientos normales + parpadeo/mirar
            }

            val phrase = when {
                taps == 1 -> getString(R.string.soldado_phrase_surprise)
                taps > 15 -> getString(R.string.soldado_phrase_sleep)
                taps > 10 -> getString(R.string.soldado_phrase_angry)
                taps > 5 -> getString(R.string.soldado_phrase_tired)
                else -> getString(tapPhraseRes.random())
            }

            soldierImage.setImageResource(soldierFrames[nextFrame])
            soldierPhrase.text = phrase
            emphasizePhraseBubble()
        }

        // Toque largo: activa/desactiva modo guardia.
        soldierContainer.setOnLongClickListener {
            guardMode = !guardMode
            FeedbackEffects.playLongPress(this)
            if (guardMode) {
                soldierImage.setImageResource(R.drawable.soldado_frame_2)
                soldierPhrase.text = getString(R.string.soldado_phrase_guard_on)
            } else {
                soldierImage.setImageResource(R.drawable.soldado_frame_0)
                soldierPhrase.text = getString(R.string.soldado_phrase_guard_off)
            }
            emphasizePhraseBubble()
            true
        }

        // Boton extra de interaccion: da una orden y el soldado reacciona.
        btnOrder.setOnClickListener {
            FeedbackEffects.playCommand(this)
            val (frameRes, phraseRes) = orderReactions.random()
            soldierImage.setImageResource(frameRes)
            soldierPhrase.text = getString(phraseRes)
            playCommandReaction()
            emphasizePhraseBubble()
        }

        btnReset.setOnClickListener {
            FeedbackEffects.playTap(this)
            taps = 0
            guardMode = false
            soldierImage.setImageResource(R.drawable.soldado_frame_0)
            soldierPhrase.text = getString(R.string.soldado_phrase_idle)
            soldierImage.rotation = 0f
            soldierImage.scaleX = 1f
            soldierImage.scaleY = 1f
            soldierImage.translationY = 0f
            soldierImage.translationX = 0f

            // Limpiar tambien las SharedPreferences del widget.
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            prefs.edit {
                clear()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startIdleAnimation()
    }

    override fun onPause() {
        stopIdleAnimation()
        super.onPause()
    }

    private fun applySystemInsets(root: View) {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = initialLeft + bars.left,
                top = initialTop + bars.top,
                right = initialRight + bars.right,
                bottom = initialBottom + bars.bottom
            )
            insets
        }

        ViewCompat.requestApplyInsets(root)
    }

    private fun startIdleAnimation() {
        stopIdleAnimation()

        val breatheY = ObjectAnimator.ofFloat(soldierImage, View.TRANSLATION_Y, 0f, -7f, 0f).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val swayX = ObjectAnimator.ofFloat(soldierImage, View.TRANSLATION_X, 0f, 2.5f, -2.5f, 0f).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val gentleScaleX = ObjectAnimator.ofFloat(soldierImage, View.SCALE_X, 1f, 1.04f, 1f).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val gentleScaleY = ObjectAnimator.ofFloat(soldierImage, View.SCALE_Y, 1f, 0.98f, 1f).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        idleAnimator = AnimatorSet().apply {
            playTogether(breatheY, swayX, gentleScaleX, gentleScaleY)
            start()
        }
    }

    private fun stopIdleAnimation() {
        idleAnimator?.cancel()
        idleAnimator = null
    }

    private fun playTapReaction() {
        // Cancela inercia de toques previos para que la animación se vea limpia.
        soldierImage.animate().cancel()

        soldierImage.animate()
            .scaleX(1.18f)
            .scaleY(0.9f)
            .rotationBy(7f)
            .setDuration(85)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                soldierImage.animate()
                    .scaleX(0.95f)
                    .scaleY(1.1f)
                    .rotationBy(-11f)
                    .setDuration(110)
                    .setInterpolator(OvershootInterpolator(1.2f))
                    .withEndAction {
                        soldierImage.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .rotation(0f)
                            .setDuration(130)
                            .setInterpolator(OvershootInterpolator(1.4f))
                            .start()
                    }
                    .start()
            }
            .start()
    }

    private fun playCommandReaction() {
        soldierImage.animate().cancel()
        soldierImage.animate()
            .rotationBy(10f)
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(120)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                soldierImage.animate()
                    .rotation(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180)
                    .setInterpolator(OvershootInterpolator(1.3f))
                    .start()
            }
            .start()
    }

    private fun emphasizePhraseBubble() {
        soldierPhrase.animate().cancel()
        soldierPhrase.alpha = 0.78f
        soldierPhrase.scaleX = 0.97f
        soldierPhrase.scaleY = 0.97f
        soldierPhrase.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    companion object {
        private const val PREFS_NAME = "soldado_widget_prefs"
    }
}
