package com.jondude1.orbit.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jondude1.orbit.data.Orbit

/** Small pop-up for catching a thought from the share sheet, QS tile or a launcher shortcut. */
class QuickCaptureActivity : ComponentActivity() {

    companion object {
        const val ACTION_CAPTURE = "com.jondude1.orbit.CAPTURE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = sharedText(intent)
        val voice = intent?.getBooleanExtra("voice", false) == true ||
            intent?.getStringExtra("voice") == "true"

        setContent {
            OrbitTheme {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                ) {
                    Column(Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Catch a thought", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            TextButton(onClick = { finish() }) { Text("Close") }
                        }
                        Spacer(Modifier.height(12.dp))
                        CaptureBox(
                            initialText = shared,
                            autoFocus = !voice,
                            startWithVoice = voice,
                            minLines = 3,
                            onSaved = { t ->
                                Toast.makeText(
                                    this@QuickCaptureActivity,
                                    "Caught. Back ${Orbit.whenText(t.surfaceAt)}.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                finish()
                            },
                        )
                    }
                }
            }
        }
    }

    private fun sharedText(i: Intent?): String {
        if (i?.action != Intent.ACTION_SEND) return ""
        val subject = i.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty().trim()
        val text = i.getStringExtra(Intent.EXTRA_TEXT).orEmpty().trim()
        return when {
            subject.isNotEmpty() && text.isNotEmpty() && !text.startsWith(subject) -> "$subject\n$text"
            text.isNotEmpty() -> text
            else -> subject
        }
    }
}
