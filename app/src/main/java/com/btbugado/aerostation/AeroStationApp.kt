package com.btbugado.aerostation

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder

/**
 * ImageLoader global com suporte a animados e vídeo: GIF e WebP animado
 * tocam nas capas e na grade do álbum em vez de congelar no primeiro
 * quadro, e vídeos geram thumbnail de verdade (frame de 1s, pra pular
 * quadros pretos de abertura).
 */
class AeroStationApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(VideoFrameDecoder.Factory())
            }
            .build()
}
