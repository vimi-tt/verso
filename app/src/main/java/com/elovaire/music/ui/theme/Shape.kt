package elovaire.music.droidbeauty.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
object ElovaireRadii {
    val topBar: Dp = 0.dp
    val listItem: Dp = 0.dp
    val dock: Dp = 24.dp
    val module: Dp = 16.dp
    val card: Dp = 16.dp
    val tile: Dp = 12.dp
    val button: Dp = 18.dp
    val artwork: Dp = 16.dp
    val artworkSmall: Dp = 8.dp
    val input: Dp = 16.dp
    val dialog: Dp = 20.dp
    val pill: Dp = 999.dp
}

fun elovaireShapes(): Shapes {
    return Shapes(
        extraSmall = RoundedCornerShape(ElovaireRadii.artworkSmall),
        small = RoundedCornerShape(ElovaireRadii.tile),
        medium = RoundedCornerShape(ElovaireRadii.card),
        large = RoundedCornerShape(ElovaireRadii.dialog),
        extraLarge = RoundedCornerShape(ElovaireRadii.dock),
    )
}
