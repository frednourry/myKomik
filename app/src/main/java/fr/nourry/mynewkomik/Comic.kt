package fr.nourry.mynewkomik

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import java.io.File

@Parcelize
data class Comic(val file: File) : Parcelable {
    var nbPages: Int = -1
}