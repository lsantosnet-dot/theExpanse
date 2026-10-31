package com.theexpanse.render

/** Espaço de desenho fixo (paisagem). O app escala para caber na tela, mantendo a proporção. */
object Layout {
    const val W = 844f
    const val H = 390f

    /** A cena (cabine ou câmera externa) ocupa a parte de cima; o painel de controles, a de baixo. */
    const val SCENE_H = 240f
    const val PANEL_Y = SCENE_H
    const val PANEL_H = H - SCENE_H
}
