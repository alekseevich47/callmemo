package com.alekseevich47.callmemo.ui.components

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alekseevich47.callmemo.ui.theme.CallMemoBlue

/**
 * Shared chrome FAB: circular shadow from the first frame (no square elevation flash),
 * aligned above glass tabs via [ChromeFabBottomPadding] + nav-bar inset.
 */
@Composable
fun ChromeFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = ChromeFabBottomPadding,
    containerColor: Color = CallMemoBlue,
    contentColor: Color = Color.White,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .navigationBarsPadding()
            .padding(end = 22.dp, bottom = bottomPadding)
            .shadow(
                elevation = 10.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.28f),
                spotColor = Color.Black.copy(alpha = 0.36f),
            ),
        shape = CircleShape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
        )
    }
}
