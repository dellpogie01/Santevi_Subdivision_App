package com.example.data.util

import android.util.Log

enum class OperationType {
    GET, LIST, CREATE, UPDATE, DELETE
}

fun handleFirestoreError(error: Exception, operation: OperationType, path: String) {
    Log.e(
        "FirestoreError",
        "{\"operation\":\"$operation\",\"path\":\"$path\",\"error\":\"${error.localizedMessage ?: error.message}\"}",
        error
    )
}
