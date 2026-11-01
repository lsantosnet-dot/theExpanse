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
        Spec("atracar", "Quando a nave atraca numa estação/destroço atracável OU pousa num planeta/lua pousável (o pouso conta como atracar).", listOf(f("local", "id de corpo", "onde atracou"))),
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
        Spec("atracado", "A nave está atracada ou pousada (num lugar específico, se informado).", listOf(f("local", "id de corpo", "onde (opcional)", false))),
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
- **Corpos reais**: um corpo pode orbitar outro (`pai`), com órbita elíptica (`excentricidade`, `argPeriapse`), e ter massa (`gm`) e raio (`raio`): planetas e luas puxam a nave e a destroem se ela encostar rápido demais: com velocidade relativa abaixo de 6 m/s (15 m/s com atmosfera) ela **pousa**. Todo planeta ou lua é pousável (`pousavel`, padrão `true`); marque `"pousavel": false` nos gigantes gasosos. O INTERCEPTAR num corpo pousável faz a descida assistida e o pouso; pousar conta como atracar (`atracar` com o `local` do corpo). Com `escalaAtmosfera` (m) e `velTerminalAtmosfera` (m/s, com paraquedas) o corpo ganha atmosfera com arrasto. Decolar exige empuxo maior que a gravidade de superfície. `tipo: "planeta"` desenha uma esfera (`cor` opcional). O corpo central pode ser uma estrela (`planeta.tipo: "estrela"`; o Sol tem gm 1.32712e20 e raio 6.957e8). Em escala solar, ajuste a nave: `aceleracaoMax` (m/s²) e `velocidadeExaustao` (m/s), por exemplo 14 e 1,6e7 (14 m/s² permite decolar até da Terra; 6 e 5e6 bastam se você só orbita e atraca).
- **Missões de pouso**: para uma missão que termina na superfície, use um planeta ou lua com `pousavel` (o padrão para `tipo: "planeta"`; `false` nos gigantes gasosos) e o gatilho `atracar` com o `local` do corpo. Exemplo: `{"id": "r_pouso", "quando": {"tipo": "atracar", "local": "lua"}, "se": {"tipo": "tem_carga"}, "entao": [{"tipo": "perder_carga"}, {"tipo": "creditos", "delta": 1400}, {"tipo": "missao", "id": "m_entrega", "estado": "concluida"}]}`. Aponte `nave.alvo` para o corpo, e o jogador usa INTERCEPTAR: o piloto automático faz a descida assistida. Para encerrar só ao voltar, use `atracar` na estação de origem (o jogador precisa decolar; isso exige empuxo maior que a gravidade de superfície, `gm / raio²`). **A Terra é uma opção de pouso e de decolagem**, como qualquer planeta ou lua pousável: na descida a atmosfera ajuda (paraquedas), e para decolar a nave precisa de empuxo maior que a gravidade de superfície. Por isso, em campanhas de escala solar, use `aceleracaoMax` de **14 m/s² ou mais** (e `velocidadeExaustao` ~1,6e7): com 14, a nave decola da Terra a partir de 80% de empuxo; com 9,8 (1 g) ela pousaria na Terra mas **nunca sairia** (a validação avisa). O jogo ajusta o empuxo sozinho ao tocar em DECOLAR. Gravidade de superfície (m/s²) e `aceleracaoMax` mínimo para decolar (a 100%): Terra 9,82 (10,1); Vênus 8,87 (9,1); Mercúrio 3,70 (3,8); Marte 3,73 (3,8); Titã 1,35 (1,4); Ganimedes 1,43 (1,5); Lua 1,62 (1,7); Ceres 0,28 (0,3). Uma missão "vá à Terra e volte" funciona assim: `nave.alvo` na Terra, gatilho `atracar` com `local: "terra"` para a entrega, e outro `atracar` na estação de origem para o final. Toque a menos de 6 m/s é pouso (15 m/s se o corpo tem atmosfera); acima disso a nave é destruída.
- **Escolta e abordagem**: `ia` com `escoltar` faz uma nave acompanhar o jogador, interceptar mísseis (`chanceInterceptar`) e atacar hostis. `ia` com `abordar` faz uma nave chegar ao jogador e abordá-lo (gatilho `abordado`). O jogador militar pode abordar uma nave de perto e devagar (gatilho `abordagem`; a chance de sucesso é 1 − `defesa` do corpo).
- Uma nave hostil só dispara se um efeito `ia` com `atacar` a liberar. Use `corpo_destruido` para reagir quando o jogador (militar) a abater.

## Regras
- Todo texto em português do Brasil. Nomes, facções e lugares **originais** (não use personagens, nomes ou enredo de obras existentes).
- Todos os `id` são minúsculos, sem espaços (`porto_kaua`). Referências a ids que não existem são erros.
- Distâncias em metros, tempos em segundos, massas em kg. O planeta padrão tem `gm` 1e17 e raio 6e7; as órbitas das estações ficam entre 1e8 e 8e8 m. Uma viagem entre duas estações leva de 5 a 12 horas de jogo com o piloto automático.
- A nave começa perto de um corpo (`nave.perto`), com a carga do contrato a bordo. Ao atracar, o combustível é reabastecido.
- **Todo mistério precisa ter solução alcançável**: toda pista usada numa dedução ou condição precisa ser revelada por alguma regra ou diálogo; todo objeto exigido precisa ser entregue por algum efeito. O jogo simula o pacote ao importar e recusa se encontrar um final inalcançável.
- Cada final deve ter `manchete` (uma frase de jornal).
- **Diga ao jogador qual alvo marcar.** No painel, `ALVO` mostra o corpo selecionado e o jogador troca de alvo tocando em `ALVO`; o piloto automático (INTERCEPTAR) vai ao alvo selecionado. A nave já começa com `nave.alvo` selecionado, mas a mensagem inicial (`r_inicio`) e o `texto` da missão devem citar o **nome do corpo de destino** e deixar claro se é uma estação (atraca) ou um planeta/lua (pousa). Exemplo certo: "levar o gelo ao Arsenal Ares, a estação em órbita de Marte (o planeta não é o destino). Marque ALVO Arsenal Ares." Nunca escreva só "leve a Marte" quando o destino é uma estação.
- **Em campanhas com viagem, inclua `emboscadas`** (veja a seção "Emboscadas dinâmicas"): o jogador não deve conhecer o risco de antemão, e o jogo sorteia sozinho conforme facção, reputação, região, rota e carga.
- **Δv e empuxo andam juntos.** O jogo simula o piloto automático ao importar e recusa o pacote se o Δv da nave (`velocidadeExaustao` × ln da razão de massas) não chega ao destino. Se você subir `aceleracaoMax` (por exemplo para 14, para decolar da Terra), a viagem exige mais Δv: suba também `velocidadeExaustao` (1,6e7 funciona na escala solar) ou o combustível.

## Estrutura do arquivo
```
{
  "formato": 1, "id": "...", "titulo": "...", "resumo": "...", "autor": "...",
  "papel": {"tipo": "mercador"} ou {"tipo": "militar", "faccao": "id da facção"},
  "escalaTempo": 1440,
  "planeta": {"nome": "Aurélia", "gm": 1e17, "raio": 6e7},
  "facoes": [{"id": "...", "nome": "...", "cor": "#RRGGBB", "estilo": "terrano|marciano|cinturao|generico"}],
  "corpos": [{"id": "...", "nome": "...", "curto": "...", "tipo": "estacao|nave|destroco|planeta", "faccao": "id", "raioOrbita": 3.2e8, "fase": 0.0, "tamanho": 150, "atracavel": true, "misseis": 4, "despistadores": 2, "chanceDespistar": 0.4, "pai": "id", "excentricidade": 0.0, "argPeriapse": 0.0, "gm": 0, "raio": 0, "cor": "#RRGGBB", "oculto": false, "defesa": 0.4, "chanceInterceptar": 0.5, "aceleracaoMax": 3.0, "pousavel": true, "escalaAtmosfera": 8500, "velTerminalAtmosfera": 9}],
  "nave": {"nome": "...", "massaSeca": 240000, "carga": 40000, "combustivel": 32000, "nomeCarga": "...", "perto": "id de corpo", "altitude": 40000, "alvo": "id de corpo", "casco": 100, "misseis": 0, "despistadores": 4, "chanceDespistar": 0.5, "aceleracaoMax": 6.0, "velocidadeExaustao": 5e6},
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
  "blocos": [{"id": "...", "efeitos": [efeitos]}],
  "emboscadas": [{"id": "...", "nave": "id de corpo tipo nave", "faccao": "id", "chance": 0.3, "perto": "id de corpo", "raio": 1e9, "alvo": "id de corpo", "comportamento": "abordar|atacar|perseguir", "distancia": 25000, "intervalo": 21600, "repetir": false, "mensagem": "texto", "efeitos": [efeitos]}]
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

## Emboscadas dinâmicas (o jogo sorteia sozinho; o autor não escreve a sorte)
Para campanhas com viagem, **declare de 1 a 3 `emboscadas`**: cada uma diz quem pode atacar, de qual facção, onde e com que chance base. O motor faz o resto, a cada `intervalo` segundos de voo (padrão 6 h): sorteia, e se der "sim" a nave surge a `distancia` metros do jogador e age (`abordar`, `atacar` ou `perseguir`). O jogador nunca sabe de antemão se haverá emboscada.
- `nave`: um corpo de `tipo: "nave"` da lista `corpos` (pode ser `"oculto": true`). Uma nave por emboscada; ela só ataca uma vez (a menos de `repetir: true`) e nunca duas ao mesmo tempo. Há 12 h de folga entre emboscadas.
- `faccao`: a facção da nave. **A reputação do jogador com ela muda a chance**: reputação −100 → ×2; 0 → ×1; +100 → ×0,2. Ou seja, facções que o jogador irritou aparecem mais, e as aliadas quase nunca atacam.
- `chance`: probabilidade base de **cada sorteio** (0 a 1). Use 0,15 a 0,35: com vários sorteios na viagem o risco total fica entre 30% e 70%. Sem carga a bordo, a chance cai pela metade; com uma nave de escolta por perto, cai pela metade de novo. O teto é 95%.
- `perto` e `raio` (m): só sorteia enquanto o jogador está a esse raio do corpo, a **região** da emboscada (por exemplo, perto de Ceres para corsários do Cinturão). `alvo`: só sorteia quando o jogador seleciona esse corpo como destino, a **rota**. Combine os dois para "quem vai de Ceres a Marte corre risco perto de Ceres".
- Varie: use facções e regiões diferentes (corsários perto do cinturão, uma fragata da Coalizão perto da Terra, uma patrulha da Federação perto de Marte), cada uma com sua `chance`. Dê reputações iniciais que tornem algumas mais prováveis que outras.
- `mensagem` (opcional) e `efeitos` (opcional: `noticia`, `flag`, `dialogo`...) rodam quando ela acontece. O motor também liga a flag `emboscada_<id>`; use-a em regras e condições.
- As consequências vêm das **regras normais**: para `abordar`, escreva uma regra `abordado` com o diálogo (pagar pedágio, resistir) e `sorteio`; para `atacar`, regras de `casco_abaixo`, `nave_destruida` e `missil_lancado`. Veja a Rota do Gelo.
- **Não** simule a sorte com `se: aleatorio` no gatilho `tempo`: a regra seria testada a cada instante e acabaria acontecendo quase com certeza. Use `emboscadas`.

## Como montar uma missão de pouso (entrega na superfície)
1. Use planetas e luas reais no `planeta`/`corpos` (Sistema Solar: Terra, Lua, Marte, Ceres, Titã...). Todo `tipo: "planeta"` é pousável por padrão; marque `"pousavel": false` nos gigantes gasosos (Júpiter, Saturno), que não têm superfície. Atmosfera é opcional (`escalaAtmosfera`, `velTerminalAtmosfera`): Terra 8500 e 9, Marte 11100 e 45.
2. Aponte `nave.alvo` para o planeta ou lua de destino e use a nave de escala solar: `aceleracaoMax` 14 e `velocidadeExaustao` 1,6e7. A **Terra** é pousável e permite decolar (a 80% de empuxo ou mais); a Lua e Marte também.
3. O pouso conta como atracar: gatilho `{"tipo": "atracar", "local": "lua"}` com `se: {"tipo": "tem_carga"}`, e efeitos `perder_carga` (descarrega), `creditos` e `missao`. Para uma segunda etapa, ative outra missão (`missao` com `estado: "ativa"`) e termine com `atracar` na estação de origem: o jogador precisa decolar de volta (o botão DECOLAR ajusta o empuxo sozinho).
4. Um prazo vira bônus: `tempo` com `depoisDe` marca `flag` de atraso, e as regras de entrega escolhem o pagamento pela flag.
5. Na mensagem inicial e no `texto` da missão, cite o corpo de destino e diga: "Selecione ALVO <corpo> e toque em INTERC.: o piloto automático faz a descida." O exemplo completo mais abaixo (Base na Lua) segue esse modelo.
""".trimIndent()
}
