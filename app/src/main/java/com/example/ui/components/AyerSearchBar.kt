package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AyerGold
import com.example.ui.theme.AyerPrimary
import com.example.ui.theme.AyerSurfaceElevated
import com.example.ui.theme.AyerSurfaceVariant

@Composable
fun AyerSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchSubmitted: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholderText: String = "بحث عن بث أو دولة أو الرمز السري... 🔍"
) {
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = query,
        onValueChange = { newText ->
            onQueryChange(newText)
            // Immediately check for secret unlock as user types
            if (newText.length >= 4) {
                onSearchSubmitted(newText)
            }
        },
        placeholder = {
            Text(
                text = placeholderText,
                fontSize = 12.sp,
                color = Color.Gray
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "بحث",
                tint = AyerGold,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "مسح",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "حماية مشفرة",
                    tint = AyerSurfaceVariant,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp)
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("ayer_universal_search_bar"),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = AyerSurfaceElevated,
            unfocusedContainerColor = AyerSurfaceElevated,
            focusedBorderColor = AyerPrimary,
            unfocusedBorderColor = AyerSurfaceVariant.copy(alpha = 0.6f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                onSearchSubmitted(query)
                focusManager.clearFocus()
            }
        )
    )
}
