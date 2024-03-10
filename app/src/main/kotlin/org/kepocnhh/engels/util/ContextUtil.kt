package org.kepocnhh.engels.util

import android.content.Context
import android.widget.Toast

internal fun Context.showToast(
    message: String,
    duration: Int = Toast.LENGTH_SHORT,
) {
    Toast.makeText(this, message, duration).show()
}
