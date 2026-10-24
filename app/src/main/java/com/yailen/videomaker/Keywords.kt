package com.yailen.videomaker

import java.text.Normalizer

/** Algo que se dibuja en pantalla cuando el texto lo menciona. */
class Visual(val emoji: String?, val color: Int?, val label: String)

private class Tok(val raw: String, val norm: String)

object Keywords {
    private fun c(v: Long) = v.toInt()

    private val colors: Map<String, Int> = mapOf(
        "rojo" to c(0xFFE53935), "roja" to c(0xFFE53935),
        "azul" to c(0xFF1E88E5), "celeste" to c(0xFF4FC3F7),
        "amarillo" to c(0xFFFDD835), "amarilla" to c(0xFFFDD835),
        "verde" to c(0xFF43A047),
        "naranja" to c(0xFFFB8C00), "anaranjado" to c(0xFFFB8C00), "anaranjada" to c(0xFFFB8C00),
        "morado" to c(0xFF8E24AA), "morada" to c(0xFF8E24AA), "violeta" to c(0xFF8E24AA),
        "rosado" to c(0xFFF48FB1), "rosada" to c(0xFFF48FB1), "rosa" to c(0xFFF48FB1),
        "negro" to c(0xFF212121), "negra" to c(0xFF212121),
        "blanco" to c(0xFFFFFFFF), "blanca" to c(0xFFFFFFFF),
        "gris" to c(0xFF9E9E9E),
        "marron" to c(0xFF6D4C41), "cafe" to c(0xFF6D4C41),
        "dorado" to c(0xFFFFC107), "dorada" to c(0xFFFFC107),
        "turquesa" to c(0xFF00ACC1)
    )

    private val objects: Map<String, String> = mapOf(
        "sol" to "☀️", "luna" to "🌙", "estrella" to "⭐", "nube" to "☁️", "lluvia" to "🌧️",
        "arcoiris" to "🌈", "montana" to "⛰️", "playa" to "🏖️", "fuego" to "🔥", "agua" to "💧",
        "arbol" to "🌳", "flor" to "🌸", "casa" to "🏠",
        "manzana" to "🍎", "pera" to "🍐", "banano" to "🍌", "banana" to "🍌", "platano" to "🍌",
        "uva" to "🍇", "fresa" to "🍓", "limon" to "🍋", "sandia" to "🍉", "zanahoria" to "🥕",
        "tomate" to "🍅", "maiz" to "🌽", "pan" to "🍞", "queso" to "🧀", "huevo" to "🥚",
        "leche" to "🥛", "helado" to "🍦", "pastel" to "🎂", "torta" to "🎂",
        "gato" to "🐱", "perro" to "🐶", "vaca" to "🐮", "cerdo" to "🐷", "gallina" to "🐔",
        "pollito" to "🐥", "caballo" to "🐴", "oveja" to "🐑", "pato" to "🦆", "pez" to "🐟",
        "pajaro" to "🐦", "ave" to "🐦", "mariposa" to "🦋", "elefante" to "🐘", "leon" to "🦁",
        "mono" to "🐵", "conejo" to "🐰", "rana" to "🐸", "tortuga" to "🐢", "abeja" to "🐝",
        "carro" to "🚗", "coche" to "🚗", "auto" to "🚗", "bus" to "🚌", "autobus" to "🚌",
        "avion" to "✈️", "barco" to "⛵", "tren" to "🚂", "bicicleta" to "🚲", "cohete" to "🚀",
        "globo" to "🎈", "pelota" to "⚽", "balon" to "⚽", "regalo" to "🎁", "libro" to "📚",
        "lapiz" to "✏️", "reloj" to "⏰", "campana" to "🔔", "corazon" to "❤️",
        "musica" to "🎵", "cancion" to "🎵", "guitarra" to "🎸", "tambor" to "🥁",
        "telefono" to "📱", "camisa" to "👕", "zapato" to "👟", "sombrero" to "🎩"
    )

    private fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    private fun <T> look(map: Map<String, T>, n: String): T? =
        map[n] ?: map[n.removeSuffix("s")] ?: map[n.removeSuffix("es")]

    /** Devuelve hasta 4 elementos (colores, objetos o "objeto + color") mencionados en la frase. */
    fun extract(text: String): List<Visual> {
        val toks = text.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }.map { Tok(it, norm(it)) }
        val used = BooleanArray(toks.size)
        val out = ArrayList<Visual>()
        for (i in toks.indices) {
            if (used[i]) continue
            val ci = look(colors, toks[i].norm)
            val oi = if (ci == null) look(objects, toks[i].norm) else null
            if (ci == null && oi == null) continue
            val pj = listOf(i - 1, i + 1).firstOrNull { j ->
                j in toks.indices && !used[j] && (
                    if (ci != null) look(colors, toks[j].norm) == null && look(objects, toks[j].norm) != null
                    else look(colors, toks[j].norm) != null
                )
            }
            used[i] = true
            if (pj == null) {
                out += if (ci != null) Visual(null, ci, toks[i].raw) else Visual(oi, null, toks[i].raw)
            } else {
                used[pj] = true
                val color = ci ?: look(colors, toks[pj].norm)
                val emoji = oi ?: look(objects, toks[pj].norm)
                val (a, b) = if (pj < i) toks[pj] to toks[i] else toks[i] to toks[pj]
                out += Visual(emoji, color, "${a.raw} ${b.raw}")
            }
        }
        return out.distinctBy { it.label.lowercase() }.take(4)
    }
}
