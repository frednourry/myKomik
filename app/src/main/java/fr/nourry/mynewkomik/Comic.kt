package fr.nourry.mynewkomik

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
data class Comic(val file: File) : Parcelable
