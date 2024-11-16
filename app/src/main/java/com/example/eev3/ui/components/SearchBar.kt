package com.example.eev3.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.example.eev3.ui.theme.ChineseRed

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = { newValue ->
            onQueryChange(newValue)
            if (newValue.contains('\n')) {
                onQueryChange(newValue.replace("\n", ""))
                onSearch()
            }
        },
        modifier = modifier,
        placeholder = { Text("搜索歌曲") },
        singleLine = false,
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Search
        ),
        keyboardActions = KeyboardActions(
            onSearch = { onSearch() }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ChineseRed,
            unfocusedBorderColor = ChineseRed.copy(alpha = 0.5f),
            focusedLabelColor = ChineseRed,
            cursorColor = ChineseRed
        )
    )
}