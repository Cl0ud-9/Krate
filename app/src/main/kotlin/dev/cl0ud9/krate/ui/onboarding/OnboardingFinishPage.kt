package dev.cl0ud9.krate.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.ui.util.isShortScreen

// the closing page, centered on its collage; side by side on a phone on its side
@Composable
internal fun FinishPage(handoff: MarkHandoff) {
    if (isShortScreen()) {
        SideBySide(
            words = {
                FinishTitle(TextAlign.Start)
                Spacer(modifier = Modifier.height(16.dp))
                FinishNote(TextAlign.Start)
            },
            art = { OnboardingCollage(icons = FINISH_ICONS, handoff = handoff) },
        )
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FinishTitle(TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))
        OnboardingCollage(icons = FINISH_ICONS, handoff = handoff)
        Spacer(modifier = Modifier.height(32.dp))
        FinishNote(TextAlign.Center)
    }
}

@Composable
private fun FinishTitle(align: TextAlign) {
    Text(
        text = "All set!",
        style = MaterialTheme.typography.displayMedium.copy(fontSize = 34.sp),
        textAlign = align,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FinishNote(align: TextAlign) {
    Text(
        text = "Your Krate is ready. Let's see what's inside.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = align,
        modifier = Modifier.fillMaxWidth(),
    )
}

private val FINISH_ICONS =
    listOf(
        R.drawable.ic_check_circle_rounded,
        R.drawable.ic_krate,
        R.drawable.ic_stat_krate,
        R.drawable.ic_groups_rounded,
        R.drawable.ic_palette_rounded,
    )
