package com.dacodahub.physicallink.feature.devices

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.dacodahub.physicallink.R

internal const val PRIVACY_POLICY_URL = "https://dacodahub.github.io/physicallink/privacy/"

/** Launch directly: package-visibility filtering can make resolveActivity incorrectly report no browser. */
internal fun openPrivacyPolicy(context: Context): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri())
        .addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

@Composable
internal fun PrivacyPolicyAction(openPolicy: (Context) -> Boolean = ::openPrivacyPolicy) {
    val context = LocalContext.current
    var unableToOpen by remember { mutableStateOf(false) }
    TextButton(onClick = { unableToOpen = !openPolicy(context) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.privacy_policy))
    }
    if (unableToOpen) AlertDialog(
        onDismissRequest = { unableToOpen = false },
        title = { Text(stringResource(R.string.privacy_policy_open_failed)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.privacy_policy_browser_help))
                SelectionContainer { Text(PRIVACY_POLICY_URL) }
            }
        },
        confirmButton = { TextButton(onClick = { unableToOpen = false }) { Text(stringResource(R.string.close_privacy_message)) } }
    )
}
