package com.medoo.autoblocker

import android.content.Context
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils

object ContactUtils {
    fun isSavedContact(context: Context, number: String): Boolean {
        val normalized = PhoneNumberUtils.normalizeNumber(number)
        if (normalized.isBlank()) return false

        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
            .buildUpon()
            .appendPath(normalized)
            .build()

        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup._ID),
            null,
            null,
            null
        )?.use { cursor ->
            return cursor.moveToFirst()
        }
        return false
    }
}
