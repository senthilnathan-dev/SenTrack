package `in`.senthilnathan_dev.sentrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import `in`.senthilnathan_dev.sentrack.ui.theme.SenTrackTheme

/** Single-activity entry point. All screens are Compose destinations hosted here. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SenTrackTheme {
                Surface(modifier = Modifier.fillMaxSize()) { Placeholder() }
            }
        }
    }
}

/** Temporary Phase 0 screen; replaced by the real home in later phases. */
@Composable
private fun Placeholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "SenTrack", style = MaterialTheme.typography.headlineLarge)
    }
}
