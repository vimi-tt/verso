package elovaire.music.droidbeauty.app.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing

object MotionDuration {
    const val Micro = 100
    const val Quick = 150
    const val Fast = 160
    const val Standard = 200
    const val Medium = 240
    const val Spacious = 250
    const val Screen = 240
    const val ScreenFade = 180
    const val ScreenSlide = 240
    const val ScreenExpand = 240
    const val Player = 540
    const val PlayerFade = 260
    const val Component = 200
    const val TopBarActionExit = 150
    const val TopBarActionEnter = 180
    const val ChromeResize = 200
    const val Emphasized = 250
    const val TopLevelEnter = 220
    const val TopLevelExit = 180
    const val DetailEnter = 240
    const val DetailExit = 200
    const val AlbumDetail = 250
    const val FullScreenEnter = 240
    const val FullScreenExit = 200
    const val QueueMenuEnter = 240
    const val QueueMenuExit = 200
    const val ListReveal = 200
    const val ListPlacement = 200
}

object MotionEasing {
    val SoftOut = FastOutSlowInEasing
    val FadeIn = LinearOutSlowInEasing
    val FadeOut = FastOutLinearInEasing
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val RefinedDecelerate = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val RefinedAccelerate = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
}

object MotionScale {
    const val ChromePressed = 0.965f
    const val CompactPressed = 0.975f
    const val MediaPressed = 0.975f
    const val PrimaryPressed = 0.965f
    const val SelectionPressed = 0.975f
    const val TogglePressed = 0.965f
    const val DestructivePressed = 0.975f
    const val PlayerOverlayEnter = 0.995f
    const val PlayerOverlayExit = 0.992f
}
