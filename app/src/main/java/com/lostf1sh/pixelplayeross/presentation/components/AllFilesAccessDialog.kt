package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lostf1sh.pixelplayeross.R
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun AllFilesAccessDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.all_files_access_title)) },
        text = { Text(text = stringResource(id = R.string.all_files_access_description)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(id = R.string.grant_permission_button), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    )
}
