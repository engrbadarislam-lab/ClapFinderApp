package com.clapfinder.app.model

/**
 * A selectable alert sound. Sounds are synthesised at runtime (see [com.clapfinder.app.util.SoundSynth])
 * so the app has zero binary audio assets and stays tiny. Drop real .mp3 files into res/raw and
 * switch SoundSynth to MediaPlayer if you prefer recorded audio.
 */
enum class SoundItem(val id: Int, val label: String, val emoji: String) {
    STEAM_AIR_HORN(0, "Steam Air Horn", "\uD83D\uDCE3"),
    BABY_LAUGH(1, "Baby Laugh", "\uD83D\uDC76"),
    BOOM(2, "Boom", "\uD83D\uDCA3"),
    DOG(3, "Dog", "\uD83D\uDC36"),
    CAT(4, "Cat", "\uD83D\uDC31"),
    CHURCH(5, "Church", "\u26EA"),
    DOOR_BELL(6, "Door Bell", "\uD83D\uDD14"),
    HORN(7, "Horn", "\uD83D\uDCEF"),
    DRUM(8, "Drum", "\uD83E\uDD41");

    companion object {
        fun fromId(id: Int): SoundItem = entries.firstOrNull { it.id == id } ?: STEAM_AIR_HORN
    }
}
