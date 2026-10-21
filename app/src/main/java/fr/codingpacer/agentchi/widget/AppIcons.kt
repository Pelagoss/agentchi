package fr.codingpacer.agentchi.widget

import fr.codingpacer.agentchi.pet.MascotSprite

/**
 * Icônes de l'appli dans le lanceur : l'icône classique, ou une par espèce et palette de mascotte.
 * Chacune est une entrée `activity-alias` du manifeste (générée par AppIconsTest) ; une seule est active.
 */
object AppIcons {
    const val CLASSIC = ".launcher.Classic"

    val mascots: List<Pair<MascotSprite.Species, MascotSprite.Palette>> =
        MascotSprite.Species.entries.flatMap { s -> MascotSprite.Palette.entries.map { s to it } }

    /** Noms des alias, relatifs au package de l'appli. */
    val aliases: List<String> = listOf(CLASSIC) + mascots.map { (s, p) -> alias(s, p) }

    fun alias(species: MascotSprite.Species, palette: MascotSprite.Palette) = ".launcher.Mascot_${species.name}_${palette.name}"

    /** Nom de la ressource (mipmap, drawable) de l'icône. */
    fun resourceName(species: MascotSprite.Species, palette: MascotSprite.Palette) =
        "ic_mascot_${species.name.lowercase()}_${palette.name.lowercase()}"

    fun resourceName(alias: String): String {
        val (s, p) = mascots.first { (s, p) -> alias(s, p) == alias }
        return resourceName(s, p)
    }
}
