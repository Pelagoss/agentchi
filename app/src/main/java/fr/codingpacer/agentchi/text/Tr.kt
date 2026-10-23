package fr.codingpacer.agentchi.text

import java.util.Locale

/**
 * Langue de l'interface : français si la montre (ou la langue choisie pour l'appli) est en français, anglais sinon.
 * Lue à chaque appel, pour suivre un changement de langue sans redémarrer l'appli.
 */
val isFrench: Boolean get() = Locale.getDefault().language == "fr"

/** Texte selon la langue : tr("Nourrir", "Feed"). Comme Mascot.tr dans Stores/source/mascot.js. */
fun tr(fr: String, en: String): String = if (isFrench) fr else en
