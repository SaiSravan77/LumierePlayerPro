package com.lumiere.player.enhancement

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class EnhanceParams(
    var enabled: Boolean      = true,
    var sharpness: Float      = 0.6f,
    var noise: Float          = 0.4f,
    var contrast: Float       = 1.15f,
    var brightness: Float     = 1.05f,
    var saturation: Float     = 1.2f,
    var warmth: Float         = 0.3f,
    var shadow: Float         = 0.05f,
    var faceEnhance: Boolean  = true,
    var sceneAware: Boolean   = true,
    var hdrSim: Boolean       = false,
    var deinterlace: Boolean  = false,
    var upscale: Boolean      = true
) : Parcelable {
    companion object {
        val CLASSIC_90S = EnhanceParams(sharpness=0.6f,  noise=0.4f, contrast=1.15f, brightness=1.05f, saturation=1.2f,  warmth=0.3f,  shadow=0.05f)
        val VHS         = EnhanceParams(sharpness=0.3f,  noise=0.6f, contrast=1.1f,  brightness=0.98f, saturation=1.35f, warmth=0.5f,  shadow=0.08f)
        val CINEMA      = EnhanceParams(sharpness=0.7f,  noise=0.3f, contrast=1.25f, brightness=1.0f,  saturation=1.05f, warmth=0.1f,  shadow=0.1f)
        val VIVID       = EnhanceParams(sharpness=0.8f,  noise=0.2f, contrast=1.3f,  brightness=1.08f, saturation=1.6f,  warmth=0.0f,  shadow=0.02f)
        val BW          = EnhanceParams(sharpness=0.75f, noise=0.5f, contrast=1.35f, brightness=1.02f, saturation=0.0f,  warmth=0.0f,  shadow=0.06f)
        val NEUTRAL     = EnhanceParams(sharpness=0.4f,  noise=0.2f, contrast=1.0f,  brightness=1.0f,  saturation=1.0f,  warmth=0.0f,  shadow=0.0f)
        val NIGHT       = EnhanceParams(sharpness=0.5f,  noise=0.7f, contrast=1.2f,  brightness=1.15f, saturation=0.9f,  warmth=0.2f,  shadow=0.12f)
        val OUTDOOR     = EnhanceParams(sharpness=0.75f, noise=0.2f, contrast=1.2f,  brightness=1.0f,  saturation=1.4f,  warmth=-0.1f, shadow=0.03f)

        val ALL_PRESETS = listOf(
            "Classic 90s" to CLASSIC_90S,
            "VHS"         to VHS,
            "Cinema"      to CINEMA,
            "Vivid"       to VIVID,
            "B&W"         to BW,
            "Neutral"     to NEUTRAL,
            "Night"       to NIGHT,
            "Outdoor"     to OUTDOOR
        )
    }

    fun copyFrom(other: EnhanceParams) {
        enabled     = other.enabled
        sharpness   = other.sharpness
        noise       = other.noise
        contrast    = other.contrast
        brightness  = other.brightness
        saturation  = other.saturation
        warmth      = other.warmth
        shadow      = other.shadow
        faceEnhance = other.faceEnhance
        sceneAware  = other.sceneAware
        hdrSim      = other.hdrSim
        deinterlace = other.deinterlace
        upscale     = other.upscale
    }
}
