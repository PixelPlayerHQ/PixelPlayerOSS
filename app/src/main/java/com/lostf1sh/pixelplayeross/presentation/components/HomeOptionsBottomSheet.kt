package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.R

@Composable
fun HomeOptionsBottomSheet(
    onNavigateToMashup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(bottom = 32.dp)) { // Padding for gesture bar
        ListItem(
            headlineContent = { Text(stringResource(R.string.home_option_dj_mashup)) },
            leadingContent = {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_instant_mix_24),
                    contentDescription = stringResource(R.string.home_option_dj_mashup)
                )
            },
            modifier = Modifier
                .padding(20.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onNavigateToMashup)
        )
    }
}