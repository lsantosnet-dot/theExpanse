package com.theexpanse.render

import com.theexpanse.core.Body
import com.theexpanse.game.RouteOption
import com.theexpanse.game.RouteOutcome
import com.theexpanse.game.RoutePlanJob

/** Verificação feita antes de engajar o INTERCEPTAR: roda o piloto automático em cópias da simulação e diz se a rota presta. */
class Preflight(val job: RoutePlanJob, val thrustPct: Int, val targetName: String) {
    val progress: Float get() = (job.pcts.indices.map { job.progress(it) }.average()).toFloat()
    val mine: RouteOption? get() = job.options.getOrNull(job.pcts.indexOf(thrustPct))
}

/** Resultado de uma rota inviável: o motivo e, se existir, um empuxo que daria certo. */
class Verdict(val reason: String, val detail: String, val alternative: RouteOption?, val station: Body? = null)

object PreflightText {
    fun ok(o: RouteOption): Boolean = o.outcome == RouteOutcome.DOCKS || o.outcome == RouteOutcome.NOT_DOCKABLE

    /** [stationOf] acha a estação atracável que orbita o corpo onde a rota termina (se houver). */
    fun verdict(p: Preflight, bodyOf: (String) -> Body? = { null }, stationOf: (Body) -> Body? = { null }): Verdict? {
        val o = p.mine ?: return null
        if (ok(o)) return null
        val reason = when {
            o.outcome == RouteOutcome.CRASHES && o.crashedInto != null -> "A ROTA TERMINA EM COLISÃO COM ${o.crashedInto.uppercase()}"
            o.outcome == RouteOutcome.CRASHES -> "A NAVE SERIA DESTRUÍDA NO CAMINHO"
            o.fuelOut -> "COMBUSTÍVEL INSUFICIENTE PARA CHEGAR"
            else -> "A ROTA NÃO CHEGA A ${p.targetName.uppercase()} NO TEMPO SIMULADO"
        }
        val hit = if (o.outcome == RouteOutcome.CRASHES) o.crashedInto?.let(bodyOf) else null
        val detail = when {
            hit != null && hit.kind == com.theexpanse.content.BodyKind.PLANET && !hit.landable -> "${hit.name} não tem superfície onde pousar (gigante gasoso). Mire numa estação em órbita, ou em outro corpo."
            hit != null && hit.landable -> "A nave não consegue frear para pousar em ${hit.name} com ${p.thrustPct}% de empuxo (gravidade de superfície ${com.theexpanse.render.Fmt.dec(hit.def.gm / (hit.def.radius * hit.def.radius), 2)} m/s²). Aumente o empuxo ou escolha outro corpo."
            o.outcome == RouteOutcome.CRASHES -> "O piloto automático já tenta desviar, mas a velocidade e a distância não deixam. Mude o alvo, o empuxo ou corrija o rumo na mão."
            o.fuelOut -> "A nave ficaria à deriva. Use outro alvo, ou um empuxo que gaste menos."
            else -> "Mesmo com ${p.thrustPct}% de empuxo a viagem não termina. Tente outro empuxo ou outro alvo."
        }
        val alt = p.job.options.filterNotNull().filter { it.thrustPct != p.thrustPct && ok(it) }.minByOrNull { it.seconds }
        val station = hit?.let(stationOf)
        val hint = if (station != null) " ${station.name} orbita ${hit.name}: mire nela e atraque." else ""
        return Verdict(reason, detail + hint, alt, station)
    }
}
