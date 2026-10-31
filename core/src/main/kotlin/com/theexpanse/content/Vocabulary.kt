package com.theexpanse.content

/** Campo de um tipo do vocabulário: serve tanto para validar o JSON quanto para gerar o guia da IA. */
class Field(val name: String, val type: String, val required: Boolean, val doc: String)

class Spec(val name: String, val doc: String, val fields: List<Field>) {
    val keys: Set<String> = fields.map { it.name }.toSet() + "tipo"
}

/** O que o motor sabe fazer. Pacotes combinam estas peças; mecânicas novas exigem atualizar o app. */
object Vocabulary {
    private fun f(name: String, type: String, doc: String, required: Boolean = true) = Field(name, type, required, doc)

    val triggers = listOf(
        Spec("inicio", "No começo da partida.", emptyList()),
        Spec("atracar", "Quando a nave atraca em um corpo atracável.", listOf(f("local", "id de corpo", "onde atracou"))),
        Spec("distancia", "Quando a distância entre dois corpos passa a ficar dentro do limite.", listOf(
            f("de", "\"jogador\" ou id de corpo", "origem"), f("ate", "id de corpo", "destino"),
            f("menorQue", "número (m)", "dispara ao ficar abaixo disto", false), f("maiorQue", "número (m)", "dispara ao ficar acima disto", false),
        )),
        Spec("tempo", "Quando o tempo de missão passa de um valor.", listOf(f("depoisDe", "número (s)", "segundos de missão"))),
        Spec("fim_dialogo", "Quando um diálogo termina.", listOf(f("dialogo", "id de diálogo", "qual diálogo"))),
        Spec("fato_revelado", "Quando uma pista é descoberta.", listOf(f("fato", "id de fato", "qual pista"))),
        Spec("aproximacao", "Quando a nave chega perto de um corpo e devagar (abordagem).", listOf(
            f("corpo", "id de corpo", "o outro corpo"), f("distMenorQue", "número (m)", "distância máxima"), f("velMenorQue", "número (m/s)", "velocidade relativa máxima"),
        )),
        Spec("abordado", "Quando uma nave em modo abordar chega ao jogador e o aborda.", listOf(f("corpo", "id de corpo", "qual nave (opcional)", false))),
        Spec("abordagem", "Quando o jogador (militar) tenta abordar uma nave.", listOf(f("corpo", "id de corpo", "qual nave (opcional)", false), f("resultado", "sucesso|falha", "só esse resultado (opcional)", false))),
        Spec("casco_abaixo", "Quando o casco cai abaixo de uma porcentagem.", listOf(f("pct", "número 0-100", "limite"))),
        Spec("combustivel_abaixo", "Quando o combustível cai abaixo de uma porcentagem.", listOf(f("pct", "número 0-100", "limite"))),
        Spec("a_cada", "Periodicamente, a cada tantos segundos de missão.", listOf(f("segundos", "número (s)", "intervalo"))),
        Spec("reputacao_na_faixa", "Quando a reputação com uma facção entra numa faixa.", listOf(f("faccao", "id de facção", "qual"), f("min", "inteiro", "mínimo", false), f("max", "inteiro", "máximo", false))),
        Spec("objeto_obtido", "Quando o jogador recebe um objeto.", listOf(f("objeto", "id de objeto", "qual"))),
        Spec("deducao_feita", "Quando uma dedução é feita no caderno.", listOf(f("id", "id de dedução", "qual"))),
        Spec("missao_mudou", "Quando uma missão muda de estado.", listOf(f("id", "id de missão", "qual"), f("estado", "ativa|concluida|falhou", "só esse estado (opcional)", false))),
        Spec("escolta_interceptou", "Quando uma escolta intercepta um míssil que vinha na sua direção.", emptyList()),
        Spec("missil_lancado", "Quando um míssil é lançado.", listOf(f("por", "inimigo|jogador", "quem lançou (opcional)", false))),
        Spec("nave_destruida", "Quando a nave do jogador cai no planeta ou é destruída por mísseis.", emptyList()),
        Spec("corpo_destruido", "Quando uma nave é destruída (por exemplo, abatida por um míssil do jogador).", listOf(f("corpo", "id de corpo", "qual nave"))),
    )

    val conditions = listOf(
        Spec("tem_objeto", "O jogador tem o objeto.", listOf(f("objeto", "id de objeto", "qual"))),
        Spec("sabe", "O jogador conhece a pista.", listOf(f("fato", "id de fato", "qual"))),
        Spec("flag", "Uma marca de história está (ou não) ligada.", listOf(f("nome", "texto", "nome da marca"), f("valor", "verdadeiro/falso", "padrão: verdadeiro", false))),
        Spec("reputacao", "Reputação com uma facção dentro de uma faixa (-100 a 100).", listOf(
            f("faccao", "id de facção", "qual"), f("min", "inteiro", "mínimo", false), f("max", "inteiro", "máximo", false),
        )),
        Spec("creditos", "Créditos suficientes.", listOf(f("min", "inteiro", "mínimo"))),
        Spec("deduziu", "A dedução foi feita no caderno.", listOf(f("id", "id de dedução", "qual"))),
        Spec("tem_carga", "A carga do contrato ainda está a bordo.", emptyList()),
        Spec("papel", "O papel do jogador na campanha (fixo): mercador ou militar.", listOf(f("valor", "mercador|militar", "qual papel"))),
        Spec("missao", "Estado de uma missão: ativa, concluida ou falhou.", listOf(f("id", "id de missão", "qual"), f("estado", "ativa|concluida|falhou", "estado"))),
        Spec("contador", "Valor de um contador numérico da história.", listOf(f("nome", "texto", "nome do contador"), f("min", "inteiro", "mínimo", false), f("max", "inteiro", "máximo", false))),
        Spec("casco", "Casco do jogador em % (0-100).", listOf(f("min", "número", "mínimo", false), f("max", "número", "máximo", false))),
        Spec("combustivel", "Combustível do jogador em % (0-100).", listOf(f("min", "número", "mínimo", false), f("max", "número", "máximo", false))),
        Spec("atracado", "A nave está atracada (em um lugar específico, se informado).", listOf(f("local", "id de corpo", "onde (opcional)", false))),
        Spec("corpo_vivo", "Uma nave existe (ou não) no jogo.", listOf(f("corpo", "id de corpo", "qual"), f("valor", "verdadeiro/falso", "padrão: verdadeiro", false))),
        Spec("distancia", "Distância entre dois corpos (ou do jogador a um corpo).", listOf(f("de", "\"jogador\" ou id de corpo", "origem"), f("ate", "id de corpo", "destino"), f("menorQue", "número (m)", "limite", false), f("maiorQue", "número (m)", "limite", false))),
        Spec("tempo_maior", "O tempo de missão passou de um valor.", listOf(f("segundos", "número (s)", "tempo"))),
        Spec("aleatorio", "Verdadeira com uma probabilidade (sorteada a cada avaliação).", listOf(f("chance", "número 0-1", "probabilidade"))),
        Spec("alvo", "O alvo selecionado é este corpo.", listOf(f("corpo", "id de corpo", "qual"))),
        Spec("e", "Todas as condições.", listOf(f("condicoes", "lista de condições", "todas devem valer"))),
        Spec("ou", "Qualquer condição.", listOf(f("condicoes", "lista de condições", "basta uma"))),
        Spec("nao", "Nega uma condição.", listOf(f("condicao", "condição", "a negada"))),
    )

    val effects = listOf(
        Spec("mensagem", "Mostra uma mensagem no registro.", listOf(f("texto", "texto", "a mensagem"))),
        Spec("reputacao", "Muda a reputação com uma facção.", listOf(f("faccao", "id de facção", "qual"), f("delta", "inteiro", "variação"))),
        Spec("creditos", "Soma ou subtrai créditos.", listOf(f("delta", "inteiro", "variação"))),
        Spec("dar_objeto", "Entrega um objeto (sua massa entra no porão).", listOf(f("objeto", "id de objeto", "qual"))),
        Spec("tirar_objeto", "Retira um objeto.", listOf(f("objeto", "id de objeto", "qual"))),
        Spec("revelar_fato", "O jogador descobre uma pista (vai para o caderno).", listOf(f("fato", "id de fato", "qual"))),
        Spec("flag", "Liga ou desliga uma marca de história.", listOf(f("nome", "texto", "nome da marca"), f("valor", "verdadeiro/falso", "padrão: verdadeiro", false))),
        Spec("dialogo", "Abre um diálogo (a simulação pausa).", listOf(f("id", "id de diálogo", "qual"))),
        Spec("noticia", "Publica uma manchete.", listOf(f("texto", "texto", "a manchete"))),
        Spec("final", "Encerra a partida com um final.", listOf(f("id", "id de final", "qual"))),
        Spec("ia", "Muda o comportamento de uma nave: perseguir (só segue), atacar (segue e dispara mísseis no jogador), abordar (chega perto para abordar o jogador), escoltar (acompanha o jogador, intercepta mísseis e ataca hostis), patrulhar (percorre em ciclo a lista `rota` de corpos, com ao menos 2), fugir (afasta-se do jogador com toda a aceleração) ou orbitar.", listOf(f("corpo", "id de corpo", "qual nave"), f("comportamento", "perseguir|atacar|abordar|escoltar|patrulhar|fugir|orbitar", "o que fazer"), f("rota", "lista de ids de corpos", "só para patrulhar: os pontos da ronda, em ordem", false))),
        Spec("alvo", "Seleciona um corpo como alvo.", listOf(f("corpo", "id de corpo", "qual"))),
        Spec("perder_carga", "A carga do contrato é apreendida (a massa some).", emptyList()),
        Spec("missao", "Muda o estado de uma missão.", listOf(f("id", "id de missão", "qual"), f("estado", "ativa|concluida|falhou", "novo estado"))),
        Spec("contador", "Soma `delta` a um contador numérico ou, com `valor`, o define.", listOf(f("nome", "texto", "nome do contador"), f("delta", "inteiro", "quanto somar", false), f("valor", "inteiro", "valor a definir", false))),
        Spec("se", "Executa efeitos conforme uma condição.", listOf(f("condicao", "condição", "a testada"), f("entao", "lista de efeitos", "se verdadeira", false), f("senao", "lista de efeitos", "se falsa", false))),
        Spec("executar", "Executa um bloco de efeitos nomeado (de `blocos`).", listOf(f("bloco", "id de bloco", "qual"))),
        Spec("agendar", "Executa um bloco depois de alguns segundos de missão.", listOf(f("bloco", "id de bloco", "qual"), f("segundos", "número (s)", "daqui a quanto tempo"))),
        Spec("emboscar", "Faz uma nave surgir perto do jogador (mesma velocidade). Combine com `ia` para ela atacar ou abordar.", listOf(f("corpo", "id de corpo", "qual nave"), f("distancia", "número (m)", "a que distância (padrão 20000)", false))),
        Spec("revelar_corpo", "Faz aparecer um corpo marcado como `oculto`.", listOf(f("corpo", "id de corpo", "qual"))),
        Spec("ocultar_corpo", "Esconde um corpo.", listOf(f("corpo", "id de corpo", "qual"))),
        Spec("destruir_corpo", "Remove uma nave do jogo (com explosão, por padrão).", listOf(f("corpo", "id de corpo", "qual nave"), f("explosao", "verdadeiro/falso", "padrão: verdadeiro", false))),
        Spec("casco", "Repara ou danifica o casco, em % da capacidade.", listOf(f("delta", "número", "variação em %"))),
        Spec("combustivel", "Abastece ou tira combustível, em % da capacidade.", listOf(f("delta", "número", "variação em %"))),
        Spec("municao", "Soma mísseis e despistadores.", listOf(f("misseis", "inteiro", "variação", false), f("despistadores", "inteiro", "variação", false))),
        Spec("sorteio", "Sorteia entre sucesso e falha. A chance é base + reputação × por, entre min e max.", listOf(
            f("chance", "objeto {base, faccao?, por?, min?, max?}", "probabilidade (0 a 1)"),
            f("sucesso", "lista de efeitos", "se der certo", false), f("falha", "lista de efeitos", "se der errado", false),
        )),
    )

    fun spec(list: List<Spec>, name: String) = list.firstOrNull { it.name == name }

    private fun describe(list: List<Spec>): String = list.joinToString("\n") { s ->
        val fields = if (s.fields.isEmpty()) "(sem campos)" else s.fields.joinToString("; ") { "`${it.name}` (${it.type}${if (it.required) "" else ", opcional"}): ${it.doc}" }
        "- `${s.name}`: ${s.doc} Campos: $fields"
    }

    /** Guia em português para uma IA gerar pacotes válidos. */
    fun guide(): String = """
# Guia para criar uma campanha para o jogo

Você vai escrever **um único arquivo JSON** que o jogo importa sem recompilar. O jogo tem física newtoniana real (inércia, gravidade do planeta, empuxo só na direção do nariz, combustível limitado, tempo acelerado). O pacote descreve o universo, as pessoas, as missões, o mistério e os finais; o jogo faz o resto. Nada no pacote executa código.

## Papel do jogador (vale a campanha inteira e não muda durante o jogo)
- `{"tipo": "mercador"}`: o jogador pilota um cargueiro independente, sem armas. Se uma nave hostil o atacar, só pode **lançar despistadores** (cada um tem uma chance aleatória, `chanceDespistar`, de desviar cada míssil).
- `{"tipo": "militar", "faccao": "id"}`: o jogador comanda o **cruzador** dessa facção, com mísseis (`nave.misseis`) e casco mais forte. Se `misseis` e `despistadores` não forem informados, a nave começa com **10 mísseis e 10 despistadores** (e casco 220). Pode abater naves hostis; não dispara em naves da própria facção.
- `escalaTempo` é quantos segundos de jogo passam por segundo real no nível "cruzeiro" do relógio (1440 = um dia de jogo por minuto real; uma viagem de 5 dias dura 5 minutos).
- Naves de facções aparecem como **cruzadores estelares**; `estilo` escolhe o desenho (terrano, marciano, cinturao ou generico).
- **Corpos reais**: um corpo pode orbitar outro (`pai`), com órbita elíptica (`excentricidade`, `argPeriapse`), e ter massa (`gm`) e raio (`raio`): planetas e luas puxam a nave e a destroem se ela encostar. `tipo: "planeta"` desenha uma esfera (`cor` opcional). O corpo central pode ser uma estrela (`planeta.tipo: "estrela"`; o Sol tem gm 1.32712e20 e raio 6.957e8). Em escala solar, ajuste a nave: `aceleracaoMax` (m/s²) e `velocidadeExaustao` (m/s), por exemplo 6 e 5e6.
- **Escolta e abordagem**: `ia` com `escoltar` faz uma nave acompanhar o jogador, interceptar mísseis (`chanceInterceptar`) e atacar hostis. `ia` com `abordar` faz uma nave chegar ao jogador e abordá-lo (gatilho `abordado`). O jogador militar pode abordar uma nave de perto e devagar (gatilho `abordagem`; a chance de sucesso é 1 − `defesa` do corpo).
- Uma nave hostil só dispara se um efeito `ia` com `atacar` a liberar. Use `corpo_destruido` para reagir quando o jogador (militar) a abater.

## Regras
- Todo texto em português do Brasil. Nomes, facções e lugares **originais** (não use personagens, nomes ou enredo de obras existentes).
- Todos os `id` são minúsculos, sem espaços (`porto_kaua`). Referências a ids que não existem são erros.
- Distâncias em metros, tempos em segundos, massas em kg. O planeta padrão tem `gm` 1e17 e raio 6e7; as órbitas das estações ficam entre 1e8 e 8e8 m. Uma viagem entre duas estações leva de 5 a 12 horas de jogo com o piloto automático.
- A nave começa perto de um corpo (`nave.perto`), com a carga do contrato a bordo. Ao atracar, o combustível é reabastecido.
- **Todo mistério precisa ter solução alcançável**: toda pista usada numa dedução ou condição precisa ser revelada por alguma regra ou diálogo; todo objeto exigido precisa ser entregue por algum efeito. O jogo simula o pacote ao importar e recusa se encontrar um final inalcançável.
- Cada final deve ter `manchete` (uma frase de jornal).

## Estrutura do arquivo
```
{
  "formato": 1, "id": "...", "titulo": "...", "resumo": "...", "autor": "...",
  "papel": {"tipo": "mercador"} ou {"tipo": "militar", "faccao": "id da facção"},
  "escalaTempo": 1440,
  "planeta": {"nome": "Aurélia", "gm": 1e17, "raio": 6e7},
  "facoes": [{"id": "...", "nome": "...", "cor": "#RRGGBB", "estilo": "terrano|marciano|cinturao|generico"}],
  "corpos": [{"id": "...", "nome": "...", "curto": "...", "tipo": "estacao|nave|destroco|planeta", "faccao": "id", "raioOrbita": 3.2e8, "fase": 0.0, "tamanho": 150, "atracavel": true, "misseis": 4, "despistadores": 2, "chanceDespistar": 0.4, "pai": "id", "excentricidade": 0.0, "argPeriapse": 0.0, "gm": 0, "raio": 0, "cor": "#RRGGBB", "oculto": false, "defesa": 0.4, "chanceInterceptar": 0.5, "aceleracaoMax": 3.0}],
  "nave": {"nome": "...", "massaSeca": 240000, "carga": 40000, "combustivel": 32000, "nomeCarga": "...", "perto": "id de corpo", "altitude": 40000, "alvo": "id de corpo", "casco": 100, "misseis": 0, "despistadores": 4, "chanceDespistar": 0.5},
  "creditos": 500, "reputacao": {"id da facção": 0},
  "objetos": [{"id": "...", "nome": "...", "massa": 40}],
  "fatos": [{"id": "...", "texto": "..."}],
  "personagens": [{"id": "...", "nome": "...", "papel": "...", "faccao": "id", "local": "id de corpo", "dialogo": "id de diálogo", "se": condição}],
  "dialogos": [{"id": "...", "personagem": "id", "texto": "...", "escolhas": [{"texto": "...", "detalhe": "...", "se": condição, "bloqueio": "por que está bloqueada", "efeitos": [efeitos], "proximo": "id de diálogo"}]}],
  "regras": [{"id": "...", "quando": gatilho, "se": condição, "entao": [efeitos], "repetir": false}],
  "deducoes": [{"id": "...", "precisa": ["id de fato", "id de fato"], "conclusao": "...", "efeitos": [efeitos], "automatica": false}],
  "missoes": [{"id": "...", "titulo": "...", "texto": "...", "inicial": true}],
  "suspeitos": [{"id": "...", "nome": "...", "texto": "..."}],
  "acusacoes": [{"id": "...", "suspeito": "id", "se": condição, "efeitos": [efeitos, normalmente um final]}],
  "tentativasAcusacao": 3,
  "finais": [{"id": "...", "titulo": "...", "manchete": "...", "tipo": "vitoria|derrota|neutro"}],
  "noticias": ["manchetes iniciais"],
  "blocos": [{"id": "...", "efeitos": [efeitos]}]
}
```
Um diálogo sem `escolhas` tem só o botão "Continuar". Num diálogo, `{chance}` em `detalhe` mostra a chance do primeiro sorteio da escolha. Uma acusação escolhe o suspeito: vale a primeira cujo `se` seja verdadeiro (ponha a mais exigente primeiro e uma sem `se` por último).

## Gatilhos (`quando`: {"tipo": "...", ...})
${describe(triggers)}

## Condições (`se`: {"tipo": "...", ...})
${describe(conditions)}

## Efeitos (em `entao`, `efeitos`, `sucesso`, `falha`)
${describe(effects)}

## Como montar um mistério
1. Liste as pistas (`fatos`) e onde cada uma é descoberta (um diálogo, ou uma regra `atracar`).
2. Faça a ordem importar: uma pista só aparece se o jogador já tem outra (`se: {"tipo": "sabe", ...}`) ou um objeto (`tem_objeto`).
3. `deducoes` combinam pistas no caderno e liberam consequências.
4. `acusacoes` decidem o final conforme o que o jogador sabe e carrega. Uma acusação cujo final é de `derrota` é um palpite errado: gasta uma das `tentativasAcusacao` (padrão 3), aplica só as penalidades (reputação, créditos), descarta o suspeito e o jogo continua; na última tentativa o final acontece. Finais de vitória ou neutros encerram o caso na hora. Use 1 para voltar à regra de uma só acusação.
5. Distâncias reais dão peso à história: um personagem que foge em 10 dias obriga o jogador a escolher a rota.
""".trimIndent()
}
