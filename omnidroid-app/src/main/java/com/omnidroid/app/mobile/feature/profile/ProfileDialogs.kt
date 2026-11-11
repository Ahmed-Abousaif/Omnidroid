package com.omnidroid.app.mobile.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.chromeColors
import com.omnidroid.app.mobile.shared.compose.ui.chromeContentColor
import com.omnidroid.app.mobile.shared.compose.ui.chromeMutedContentColor

@Composable
internal fun EditProfileTagDialog(
    currentTag: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var tag by rememberSaveable(currentTag) { mutableStateOf(currentTag) }
    val save = { if (tag.isNotBlank()) onConfirm(tag.trim()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = chromeColors.menu,
        titleContentColor = chromeContentColor,
        textContentColor = chromeMutedContentColor,
        title = { Text(stringResource(R.string.profile_change_tag)) },
        text = {
            OutlinedTextField(
                value = tag,
                onValueChange = { tag = it.take(UserProfileStore.MAX_TAG_LENGTH) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.profile_tag_hint)) },
                supportingText = { Text("${tag.length} / ${UserProfileStore.MAX_TAG_LENGTH}") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )
        },
        confirmButton = {
            TextButton(onClick = save, enabled = tag.isNotBlank()) { Text(stringResource(R.string.profile_save_tag)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
internal fun ProfilePhotoDialog(
    hasCustomPhoto: Boolean,
    onDismiss: () -> Unit,
    onChoosePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = chromeColors.menu,
        titleContentColor = chromeContentColor,
        textContentColor = chromeMutedContentColor,
        title = { Text(stringResource(R.string.profile_change_picture)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onChoosePhoto, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.profile_choose_photo))
                }
                if (hasCustomPhoto) {
                    TextButton(onClick = onRemovePhoto, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.profile_remove_picture))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
