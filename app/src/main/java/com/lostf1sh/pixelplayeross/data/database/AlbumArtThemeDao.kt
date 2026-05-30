package com.lostf1sh.pixelplayeross.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AlbumArtThemeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTheme(theme: AlbumArtThemeEntity)

    @Query(
        "SELECT * FROM album_art_themes WHERE albumArtUriString = :uriString AND paletteStyle = :paletteStyle"
    )
    suspend fun getThemeByUriAndStyle(uriString: String, paletteStyle: String): AlbumArtThemeEntity?

    @Query("DELETE FROM album_art_themes WHERE albumArtUriString IN (:uriStrings)")
    suspend fun deleteThemesByUris(uriStrings: List<String>)
}
