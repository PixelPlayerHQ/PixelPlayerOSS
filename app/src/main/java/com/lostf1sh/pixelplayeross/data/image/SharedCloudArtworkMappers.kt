package com.lostf1sh.pixelplayeross.data.image

import android.net.Uri
import coil.map.Mapper
import coil.request.Options
import com.lostf1sh.pixelplayeross.data.provider.SharedArtworkContentProvider

/*
 * The media session rewrites cloud artwork to `content://<pkg>.artwork/cloud/...` for every
 * controller, our own included, so the player UI, notification, and widgets receive that URI.
 * Loading it in-process goes through SharedArtworkContentProvider, which decodes the cover with
 * this same ImageLoader while the outer decode blocks reading the pipe. Coil allows only a few
 * concurrent bitmap decodes, so a handful of such loads hold every decode slot and the inner
 * decodes they wait on never start: all artwork stays stuck on its placeholder until restart.
 * Mapping back to the raw cloud URI lets the Navidrome/Jellyfin fetchers serve it directly.
 */

class SharedCloudArtworkStringMapper(private val packageName: String) : Mapper<String, Uri> {
    override fun map(data: String, options: Options): Uri? =
        SharedArtworkContentProvider.parseCloudArtworkUri(data, packageName)?.let(Uri::parse)
}

class SharedCloudArtworkUriMapper(private val packageName: String) : Mapper<Uri, Uri> {
    override fun map(data: Uri, options: Options): Uri? {
        if (data.authority != SharedArtworkContentProvider.authority(packageName)) return null
        return SharedArtworkContentProvider.parseCloudArtworkUri(data.toString(), packageName)
            ?.let(Uri::parse)
    }
}
