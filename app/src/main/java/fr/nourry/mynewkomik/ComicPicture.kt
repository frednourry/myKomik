package fr.nourry.mynewkomik

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import java.io.File

@Parcelize
data class ComicPicture(val file: File) : Parcelable
