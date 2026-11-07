package com.bqsqwad.share.ui

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.bqsqwad.share.R

@Composable
fun BrandLogo(modifier: Modifier = Modifier, contentDescription: String? = null) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("bq_brand_assets", Context.MODE_PRIVATE) }
    var logoUri by remember(prefs) { mutableStateOf(prefs.getString("logo_uri", null)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { values, key ->
            if (key == "logo_uri") logoUri = values.getString("logo_uri", null)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    if (logoUri.isNullOrBlank()) {
        Image(painter = painterResource(R.drawable.brand_logo), contentDescription = contentDescription, modifier = modifier, contentScale = ContentScale.Fit)
    } else {
        AsyncImage(model = Uri.parse(logoUri), contentDescription = contentDescription, modifier = modifier, contentScale = ContentScale.Fit)
    }
}
