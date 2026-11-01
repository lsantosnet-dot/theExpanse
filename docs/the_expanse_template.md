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
- `inicio`: No começo da partida. Campos: (sem campos)
- `atracar`: Quando a nave atraca numa estação/destroço atracável OU pousa num planeta/lua pousável (o pouso conta como atracar). Campos: `local` (id de corpo): onde atracou
- `distancia`: Quando a distância entre dois corpos passa a ficar dentro do limite. Campos: `de` ("jogador" ou id de corpo): origem; `ate` (id de corpo): destino; `menorQue` (número (m), opcional): dispara ao ficar abaixo disto; `maiorQue` (número (m), opcional): dispara ao ficar acima disto
- `tempo`: Quando o tempo de missão passa de um valor. Campos: `depoisDe` (número (s)): segundos de missão
- `fim_dialogo`: Quando um diálogo termina. Campos: `dialogo` (id de diálogo): qual diálogo
- `fato_revelado`: Quando uma pista é descoberta. Campos: `fato` (id de fato): qual pista
- `aproximacao`: Quando a nave chega perto de um corpo e devagar (abordagem). Campos: `corpo` (id de corpo): o outro corpo; `distMenorQue` (número (m)): distância máxima; `velMenorQue` (número (m/s)): velocidade relativa máxima
- `abordado`: Quando uma nave em modo abordar chega ao jogador e o aborda. Campos: `corpo` (id de corpo, opcional): qual nave (opcional)
- `abordagem`: Quando o jogador (militar) tenta abordar uma nave. Campos: `corpo` (id de corpo, opcional): qual nave (opcional); `resultado` (sucesso|falha, opcional): só esse resultado (opcional)
- `casco_abaixo`: Quando o casco cai abaixo de uma porcentagem. Campos: `pct` (número 0-100): limite
- `combustivel_abaixo`: Quando o combustível cai abaixo de uma porcentagem. Campos: `pct` (número 0-100): limite
- `a_cada`: Periodicamente, a cada tantos segundos de missão. Campos: `segundos` (número (s)): intervalo
- `reputacao_na_faixa`: Quando a reputação com uma facção entra numa faixa. Campos: `faccao` (id de facção): qual; `min` (inteiro, opcional): mínimo; `max` (inteiro, opcional): máximo
- `objeto_obtido`: Quando o jogador recebe um objeto. Campos: `objeto` (id de objeto): qual
- `deducao_feita`: Quando uma dedução é feita no caderno. Campos: `id` (id de dedução): qual
- `missao_mudou`: Quando uma missão muda de estado. Campos: `id` (id de missão): qual; `estado` (ativa|concluida|falhou, opcional): só esse estado (opcional)
- `escolta_interceptou`: Quando uma escolta intercepta um míssil que vinha na sua direção. Campos: (sem campos)
- `missil_lancado`: Quando um míssil é lançado. Campos: `por` (inimigo|jogador, opcional): quem lançou (opcional)
- `nave_destruida`: Quando a nave do jogador cai no planeta ou é destruída por mísseis. Campos: (sem campos)
- `corpo_destruido`: Quando uma nave é destruída (por exemplo, abatida por um míssil do jogador). Campos: `corpo` (id de corpo): qual nave

## Condições (`se`: {"tipo": "...", ...})
- `tem_objeto`: O jogador tem o objeto. Campos: `objeto` (id de objeto): qual
- `sabe`: O jogador conhece a pista. Campos: `fato` (id de fato): qual
- `flag`: Uma marca de história está (ou não) ligada. Campos: `nome` (texto): nome da marca; `valor` (verdadeiro/falso, opcional): padrão: verdadeiro
- `reputacao`: Reputação com uma facção dentro de uma faixa (-100 a 100). Campos: `faccao` (id de facção): qual; `min` (inteiro, opcional): mínimo; `max` (inteiro, opcional): máximo
- `creditos`: Créditos suficientes. Campos: `min` (inteiro): mínimo
- `deduziu`: A dedução foi feita no caderno. Campos: `id` (id de dedução): qual
- `tem_carga`: A carga do contrato ainda está a bordo. Campos: (sem campos)
- `papel`: O papel do jogador na campanha (fixo): mercador ou militar. Campos: `valor` (mercador|militar): qual papel
- `missao`: Estado de uma missão: ativa, concluida ou falhou. Campos: `id` (id de missão): qual; `estado` (ativa|concluida|falhou): estado
- `contador`: Valor de um contador numérico da história. Campos: `nome` (texto): nome do contador; `min` (inteiro, opcional): mínimo; `max` (inteiro, opcional): máximo
- `casco`: Casco do jogador em % (0-100). Campos: `min` (número, opcional): mínimo; `max` (número, opcional): máximo
- `combustivel`: Combustível do jogador em % (0-100). Campos: `min` (número, opcional): mínimo; `max` (número, opcional): máximo
- `atracado`: A nave está atracada ou pousada (num lugar específico, se informado). Campos: `local` (id de corpo, opcional): onde (opcional)
- `corpo_vivo`: Uma nave existe (ou não) no jogo. Campos: `corpo` (id de corpo): qual; `valor` (verdadeiro/falso, opcional): padrão: verdadeiro
- `distancia`: Distância entre dois corpos (ou do jogador a um corpo). Campos: `de` ("jogador" ou id de corpo): origem; `ate` (id de corpo): destino; `menorQue` (número (m), opcional): limite; `maiorQue` (número (m), opcional): limite
- `tempo_maior`: O tempo de missão passou de um valor. Campos: `segundos` (número (s)): tempo
- `aleatorio`: Verdadeira com uma probabilidade (sorteada a cada avaliação). Campos: `chance` (número 0-1): probabilidade
- `alvo`: O alvo selecionado é este corpo. Campos: `corpo` (id de corpo): qual
- `e`: Todas as condições. Campos: `condicoes` (lista de condições): todas devem valer
- `ou`: Qualquer condição. Campos: `condicoes` (lista de condições): basta uma
- `nao`: Nega uma condição. Campos: `condicao` (condição): a negada

## Efeitos (em `entao`, `efeitos`, `sucesso`, `falha`)
- `mensagem`: Mostra uma mensagem no registro. Campos: `texto` (texto): a mensagem
- `reputacao`: Muda a reputação com uma facção. Campos: `faccao` (id de facção): qual; `delta` (inteiro): variação
- `creditos`: Soma ou subtrai créditos. Campos: `delta` (inteiro): variação
- `dar_objeto`: Entrega um objeto (sua massa entra no porão). Campos: `objeto` (id de objeto): qual
- `tirar_objeto`: Retira um objeto. Campos: `objeto` (id de objeto): qual
- `revelar_fato`: O jogador descobre uma pista (vai para o caderno). Campos: `fato` (id de fato): qual
- `flag`: Liga ou desliga uma marca de história. Campos: `nome` (texto): nome da marca; `valor` (verdadeiro/falso, opcional): padrão: verdadeiro
- `dialogo`: Abre um diálogo (a simulação pausa). Campos: `id` (id de diálogo): qual
- `noticia`: Publica uma manchete. Campos: `texto` (texto): a manchete
- `final`: Encerra a partida com um final. Campos: `id` (id de final): qual
- `ia`: Muda o comportamento de uma nave: perseguir (só segue), atacar (segue e dispara mísseis no jogador), abordar (chega perto para abordar o jogador), escoltar (acompanha o jogador, intercepta mísseis e ataca hostis), patrulhar (percorre em ciclo a lista `rota` de corpos, com ao menos 2), fugir (afasta-se do jogador com toda a aceleração) ou orbitar. Campos: `corpo` (id de corpo): qual nave; `comportamento` (perseguir|atacar|abordar|escoltar|patrulhar|fugir|orbitar): o que fazer; `rota` (lista de ids de corpos, opcional): só para patrulhar: os pontos da ronda, em ordem
- `alvo`: Seleciona um corpo como alvo. Campos: `corpo` (id de corpo): qual
- `perder_carga`: A carga do contrato é apreendida (a massa some). Campos: (sem campos)
- `missao`: Muda o estado de uma missão. Campos: `id` (id de missão): qual; `estado` (ativa|concluida|falhou): novo estado
- `contador`: Soma `delta` a um contador numérico ou, com `valor`, o define. Campos: `nome` (texto): nome do contador; `delta` (inteiro, opcional): quanto somar; `valor` (inteiro, opcional): valor a definir
- `se`: Executa efeitos conforme uma condição. Campos: `condicao` (condição): a testada; `entao` (lista de efeitos, opcional): se verdadeira; `senao` (lista de efeitos, opcional): se falsa
- `executar`: Executa um bloco de efeitos nomeado (de `blocos`). Campos: `bloco` (id de bloco): qual
- `agendar`: Executa um bloco depois de alguns segundos de missão. Campos: `bloco` (id de bloco): qual; `segundos` (número (s)): daqui a quanto tempo
- `emboscar`: Faz uma nave surgir perto do jogador (mesma velocidade). Combine com `ia` para ela atacar ou abordar. Campos: `corpo` (id de corpo): qual nave; `distancia` (número (m), opcional): a que distância (padrão 20000)
- `revelar_corpo`: Faz aparecer um corpo marcado como `oculto`. Campos: `corpo` (id de corpo): qual
- `ocultar_corpo`: Esconde um corpo. Campos: `corpo` (id de corpo): qual
- `destruir_corpo`: Remove uma nave do jogo (com explosão, por padrão). Campos: `corpo` (id de corpo): qual nave; `explosao` (verdadeiro/falso, opcional): padrão: verdadeiro
- `casco`: Repara ou danifica o casco, em % da capacidade. Campos: `delta` (número): variação em %
- `combustivel`: Abastece ou tira combustível, em % da capacidade. Campos: `delta` (número): variação em %
- `municao`: Soma mísseis e despistadores. Campos: `misseis` (inteiro, opcional): variação; `despistadores` (inteiro, opcional): variação
- `sorteio`: Sorteia entre sucesso e falha. A chance é base + reputação × por, entre min e max. Campos: `chance` (objeto {base, faccao?, por?, min?, max?}): probabilidade (0 a 1); `sucesso` (lista de efeitos, opcional): se der certo; `falha` (lista de efeitos, opcional): se der errado

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

## Exemplo completo (um mistério válido)

```json
{
  "formato": 1,
  "id": "albatroz",
  "titulo": "O silêncio do Albatroz",
  "resumo": "O cargueiro Albatroz perdeu o reator sem explicação. Siga as pistas por quatro lugares e decida quem acusar. Mistério de investigação, sem combate.",
  "autor": "The Expanse (campanha incluída)",
  "papel": {"tipo": "mercador"},
  "escalaTempo": 1440,
  "planeta": {"nome": "Aurélia", "gm": 1.0e17, "raio": 6.0e7},
  "facoes": [
    {"id": "coalizao", "nome": "Coalizão Terrana", "cor": "#5B9BF0", "estilo": "terrano"},
    {"id": "federacao", "nome": "Federação Marciana", "cor": "#E8645A", "estilo": "marciano"},
    {"id": "liga", "nome": "Liga do Cinturão", "cor": "#3FD0B8", "estilo": "cinturao"},
    {"id": "atlas", "nome": "Seguradora Atlas", "cor": "#C9A06A"}
  ],
  "corpos": [
    {"id": "estacao_ceifa", "nome": "Estação Ceifa", "curto": "Ceifa", "tipo": "estacao", "faccao": "liga", "raioOrbita": 3.6e8, "fase": 0.5, "tamanho": 150},
    {"id": "destrocos_albatroz", "nome": "Destroços do Albatroz", "curto": "Albatroz", "tipo": "destroco", "faccao": "liga", "raioOrbita": 3.9e8, "fase": 1.2, "tamanho": 60, "atracavel": true},
    {"id": "lab_helena", "nome": "Laboratório Helena", "curto": "Helena", "tipo": "estacao", "faccao": "coalizao", "raioOrbita": 4.3e8, "fase": 2.2, "tamanho": 120},
    {"id": "arquivo_vesta", "nome": "Arquivo Vesta", "curto": "Vesta", "tipo": "estacao", "faccao": "federacao", "raioOrbita": 5.2e8, "fase": 2.6, "tamanho": 150}
  ],
  "nave": {"nome": "Cutelo", "massaSeca": 180000, "carga": 12000, "combustivel": 30000, "nomeCarga": "12 t de suprimentos", "perto": "estacao_ceifa", "altitude": 30000, "alvo": "destrocos_albatroz"},
  "creditos": 800,
  "reputacao": {"coalizao": 0, "federacao": 5, "liga": 10, "atlas": 0},
  "objetos": [
    {"id": "caixa_preta", "nome": "Caixa-preta do Albatroz", "massa": 40},
    {"id": "relatorio_lab", "nome": "Relatório do laboratório", "massa": 0.1}
  ],
  "fatos": [
    {"id": "f_sem_registro", "texto": "A caixa-preta do Albatroz tem 11 minutos sem registro antes da falha."},
    {"id": "f_homem_terno", "texto": "Um homem de terno embarcou no Albatroz com uma maleta da manutenção."},
    {"id": "f_sabotagem", "texto": "O reator foi desligado por comando remoto, não por falha."},
    {"id": "f_codigo_marte", "texto": "O comando usou um código de acesso da Federação Marciana."},
    {"id": "f_codigo_roubado", "texto": "Esse código foi roubado do Arquivo Vesta seis meses antes."},
    {"id": "f_apolice", "texto": "A Atlas triplicou a apólice do Albatroz três semanas antes do desastre."}
  ],
  "personagens": [
    {"id": "ilse_moraga", "nome": "Ilse Moraga", "papel": "estivadora", "faccao": "liga", "local": "estacao_ceifa", "dialogo": "d_ilse"},
    {"id": "dra_okafor", "nome": "Dra. Okafor", "papel": "perita em registros de voo", "faccao": "coalizao", "local": "lab_helena", "dialogo": "d_okafor"},
    {"id": "noor", "nome": "Noor Haddad", "papel": "arquivista", "faccao": "federacao", "local": "arquivo_vesta", "dialogo": "d_noor"}
  ],
  "dialogos": [
    {
      "id": "d_ilse", "personagem": "ilse_moraga",
      "texto": "“Eu carreguei o Albatroz na véspera. Vi um homem de terno subir a bordo. Terno, aqui no Cinturão? Ninguém usa terno aqui. Mas não falo de graça, capitão.”",
      "escolhas": [
        {"texto": "Pagar 200 créditos pela informação", "detalhe": "Créditos -200. Revela uma pista.", "se": {"tipo": "creditos", "min": 200}, "bloqueio": "Você não tem 200 créditos.",
         "efeitos": [{"tipo": "creditos", "delta": -200}, {"tipo": "revelar_fato", "fato": "f_homem_terno"}]},
        {"texto": "“A caixa-preta tem 11 minutos apagados. O que você viu?”", "detalhe": "Usa a pista da caixa-preta. Liga +2.",
         "se": {"tipo": "sabe", "fato": "f_sem_registro"}, "bloqueio": "Requer a pista da caixa-preta.",
         "efeitos": [{"tipo": "revelar_fato", "fato": "f_homem_terno"}, {"tipo": "reputacao", "faccao": "liga", "delta": 2}]},
        {"texto": "Deixar para lá"}
      ]
    },
    {
      "id": "d_okafor", "personagem": "dra_okafor",
      "texto": "“Registros de voo são a minha vida. Se você trouxe a caixa-preta do Albatroz, eu a leio agora. Sem ela, só posso especular.”",
      "escolhas": [
        {"texto": "Entregar a caixa-preta para análise", "detalhe": "Revela duas pistas e entrega um relatório.",
         "se": {"tipo": "tem_objeto", "objeto": "caixa_preta"}, "bloqueio": "Você precisa da caixa-preta dos destroços.",
         "efeitos": [{"tipo": "revelar_fato", "fato": "f_sabotagem"}, {"tipo": "revelar_fato", "fato": "f_codigo_marte"}, {"tipo": "dar_objeto", "objeto": "relatorio_lab"},
                     {"tipo": "reputacao", "faccao": "coalizao", "delta": 3}], "proximo": "d_okafor_fim"},
        {"texto": "Voltar depois"}
      ]
    },
    {
      "id": "d_okafor_fim", "personagem": "dra_okafor",
      "texto": "“O reator não falhou: alguém o desligou à distância, com um código de comando. E o código é marciano. Mas repare, capitão: código é uma chave, e chaves se roubam.”"
    },
    {
      "id": "d_noor", "personagem": "noor",
      "texto": "“O Arquivo Vesta guarda tudo da Federação: códigos, escalas, apólices. Posso procurar, mas preciso saber o que procurar.”",
      "escolhas": [
        {"texto": "Perguntar sobre o código de comando marciano", "detalhe": "Usa a pista do código marciano.",
         "se": {"tipo": "sabe", "fato": "f_codigo_marte"}, "bloqueio": "Requer a pista do código marciano.",
         "efeitos": [{"tipo": "revelar_fato", "fato": "f_codigo_roubado"}]},
        {"texto": "Pedir os contratos de seguro do Albatroz", "detalhe": "Usa a pista da caixa-preta.",
         "se": {"tipo": "sabe", "fato": "f_sem_registro"}, "bloqueio": "Requer a pista da caixa-preta.",
         "efeitos": [{"tipo": "revelar_fato", "fato": "f_apolice"}]},
        {"texto": "Deixar para lá"}
      ]
    }
  ],
  "regras": [
    {"id": "r_inicio", "quando": {"tipo": "inicio"}, "entao": [{"tipo": "mensagem", "texto": "Missão: descobrir quem desligou o reator do Albatroz. Comece pelos Destroços do Albatroz: ALVO Destroços do Albatroz, depois INTERC."}]},
    {"id": "r_destrocos", "quando": {"tipo": "atracar", "local": "destrocos_albatroz"},
     "entao": [{"tipo": "dar_objeto", "objeto": "caixa_preta"}, {"tipo": "revelar_fato", "fato": "f_sem_registro"}, {"tipo": "mensagem", "texto": "Você recuperou a caixa-preta do Albatroz."}]},
    {"id": "r_dia3", "quando": {"tipo": "tempo", "depoisDe": 259200}, "entao": [{"tipo": "noticia", "texto": "Famílias da tripulação do Albatroz exigem uma investigação independente."}]}
  ],
  "deducoes": [
    {"id": "ded_armacao", "precisa": ["f_codigo_marte", "f_codigo_roubado"], "conclusao": "Alguém usou um código roubado para culpar Marte.", "efeitos": [{"tipo": "flag", "nome": "armacao"}]},
    {"id": "ded_atlas", "precisa": ["f_homem_terno", "f_apolice"], "conclusao": "O homem de terno agia pela Atlas: a apólice foi triplicada antes do desastre.", "efeitos": [{"tipo": "flag", "nome": "atlas_ligada"}]}
  ],
  "missoes": [
    {"id": "m_verdade", "titulo": "O silêncio do Albatroz", "texto": "Descobrir quem desligou o reator do cargueiro Albatroz e acusar o responsável.", "inicial": true}
  ],
  "suspeitos": [
    {"id": "s_marte", "nome": "Federação Marciana", "texto": "O código usado no comando era deles."},
    {"id": "s_terno", "nome": "O homem de terno", "texto": "Embarcou com uma maleta; identidade desconhecida."},
    {"id": "s_atlas", "nome": "Seguradora Atlas", "texto": "Pagou o casco do Albatroz em dois dias."}
  ],
  "acusacoes": [
    {"id": "ac_atlas_prova", "suspeito": "s_atlas",
     "se": {"tipo": "e", "condicoes": [{"tipo": "deduziu", "id": "ded_armacao"}, {"tipo": "deduziu", "id": "ded_atlas"}, {"tipo": "tem_objeto", "objeto": "relatorio_lab"}]},
     "efeitos": [{"tipo": "missao", "id": "m_verdade", "estado": "concluida"}, {"tipo": "reputacao", "faccao": "federacao", "delta": 10}, {"tipo": "reputacao", "faccao": "atlas", "delta": -30}, {"tipo": "final", "id": "verdade"}]},
    {"id": "ac_atlas_parcial", "suspeito": "s_atlas", "se": {"tipo": "deduziu", "id": "ded_atlas"},
     "efeitos": [{"tipo": "missao", "id": "m_verdade", "estado": "concluida"}, {"tipo": "final", "id": "parcial"}]},
    {"id": "ac_atlas_vazia", "suspeito": "s_atlas", "efeitos": [{"tipo": "missao", "id": "m_verdade", "estado": "falhou"}, {"tipo": "reputacao", "faccao": "atlas", "delta": -10}, {"tipo": "final", "id": "sem_provas"}]},
    {"id": "ac_terno_parcial", "suspeito": "s_terno", "se": {"tipo": "deduziu", "id": "ded_atlas"},
     "efeitos": [{"tipo": "missao", "id": "m_verdade", "estado": "concluida"}, {"tipo": "final", "id": "parcial"}]},
    {"id": "ac_terno_vazia", "suspeito": "s_terno", "efeitos": [{"tipo": "missao", "id": "m_verdade", "estado": "falhou"}, {"tipo": "final", "id": "sem_provas"}]},
    {"id": "ac_marte", "suspeito": "s_marte",
     "efeitos": [{"tipo": "missao", "id": "m_verdade", "estado": "falhou"}, {"tipo": "reputacao", "faccao": "federacao", "delta": -40}, {"tipo": "final", "id": "guerra_injusta"}]}
  ],
  "finais": [
    {"id": "verdade", "titulo": "A VERDADE VEM À TONA", "tipo": "vitoria", "manchete": "Atlas é acusada de sabotar o próprio cargueiro; Marte é inocentada e o inquérito é reaberto."},
    {"id": "parcial", "titulo": "VERDADE PELA METADE", "tipo": "neutro", "manchete": "Corretor da Atlas é preso, mas a seguradora escapa ilesa; Marte segue sob suspeita."},
    {"id": "sem_provas", "titulo": "ACUSAÇÃO SEM PROVAS", "tipo": "derrota", "manchete": "Acusação sem provas cai no tribunal; capitão independente é processado por difamação."},
    {"id": "guerra_injusta", "titulo": "ACUSAÇÃO INJUSTA", "tipo": "derrota", "manchete": "Coalizão e Federação trocam tiros no Cinturão após acusação contra Marte; investigação arquivada."}
  ],
  "noticias": [
    "Cargueiro Albatroz perde o reator em órbita de Aurélia; tripulação resgatada.",
    "Seguradora Atlas paga o casco do Albatroz em tempo recorde.",
    "Coalizão e Federação trocam acusações sobre a rota do Albatroz."
  ]
}

```

## Exemplo completo de missão de pouso (Base na Lua: pouso, entrega, decolagem e volta)

```json
{
  "formato": 1,
  "id": "base_lunar",
  "titulo": "Base na Lua (Pouso)",
  "resumo": "Pouse de verdade. Leve suprimentos da Estação Meridiano à superfície da Lua: o piloto automático conduz a descida, você aterrissa na Base Selene e depois decola de volta. Prazo curto paga mais.",
  "autor": "The Expanse (campanha incluída)",
  "papel": {"tipo": "mercador"},
  "escalaTempo": 1440,
  "planeta": {"nome": "Sol", "tipo": "estrela", "gm": 1.32712440018e+20, "raio": 695700000.0},
  "facoes": [
    {"id": "coalizao", "nome": "Coalizão Terrana", "cor": "#5B9BF0", "estilo": "terrano"},
    {"id": "federacao", "nome": "Federação Marciana", "cor": "#E8645A", "estilo": "marciano"},
    {"id": "liga", "nome": "Liga do Cinturão", "cor": "#3FD0B8", "estilo": "cinturao"},
    {"id": "natural", "nome": "Corpos naturais", "cor": "#8A95A8", "estilo": "generico"},
    {"id": "corsarios", "nome": "Corsários da Gralha", "cor": "#C9A06A", "estilo": "generico"}
  ],
  "corpos": [
    {"id": "mercurio", "nome": "Mercúrio", "curto": "Mercúrio", "tipo": "planeta", "faccao": "natural", "raioOrbita": 57909050000.0, "excentricidade": 0.2056, "argPeriapse": 1.3519, "fase": 3.05, "gm": 22031868000000.0, "raio": 2439700.0, "cor": "#A8A29A"},
    {"id": "venus", "nome": "Vênus", "curto": "Vênus", "tipo": "planeta", "faccao": "natural", "raioOrbita": 108208000000.0, "excentricidade": 0.0068, "argPeriapse": 2.2957, "fase": 0.8805, "gm": 324858592000000.0, "raio": 6051800.0, "cor": "#E3C27A", "escalaAtmosfera": 15900, "velTerminalAtmosfera": 10},
    {"id": "terra", "nome": "Terra", "curto": "Terra", "tipo": "planeta", "faccao": "natural", "raioOrbita": 149597870700.0, "excentricidade": 0.0167, "argPeriapse": 1.7967, "fase": 6.2399, "gm": 398600441800000.0, "raio": 6371000.0, "cor": "#4F8FE8", "escalaAtmosfera": 8500, "velTerminalAtmosfera": 9},
    {"id": "lua", "nome": "Lua", "curto": "Lua", "tipo": "planeta", "faccao": "natural", "pai": "terra", "raioOrbita": 384400000.0, "excentricidade": 0.0549, "fase": 2.0, "gm": 4904800000000.0, "raio": 1737400.0, "cor": "#B9B9B0"},
    {"id": "marte", "nome": "Marte", "curto": "Marte", "tipo": "planeta", "faccao": "natural", "raioOrbita": 227939200000.0, "excentricidade": 0.0934, "argPeriapse": 5.865, "fase": 0.338, "gm": 42828370000000.0, "raio": 3389500.0, "cor": "#C8603A", "escalaAtmosfera": 11100, "velTerminalAtmosfera": 45},
    {"id": "ceres", "nome": "Ceres", "curto": "Ceres", "tipo": "planeta", "faccao": "natural", "raioOrbita": 414050000000.0, "excentricidade": 0.0758, "argPeriapse": 2.686, "fase": 3.5, "gm": 62628000000.0, "raio": 473000.0, "cor": "#A39E94"},
    {"id": "jupiter", "nome": "Júpiter", "curto": "Júpiter", "tipo": "planeta", "faccao": "natural", "raioOrbita": 778500000000.0, "excentricidade": 0.0489, "argPeriapse": 0.257, "fase": 0.343, "gm": 1.26686534e+17, "raio": 69911000.0, "cor": "#D9B48A", "pousavel": false},
    {"id": "ganimedes", "nome": "Ganimedes", "curto": "Ganimedes", "tipo": "planeta", "faccao": "natural", "pai": "jupiter", "raioOrbita": 1070400000.0, "excentricidade": 0.0013, "fase": 1.0, "gm": 9887000000000.0, "raio": 2634000.0, "cor": "#9C9488"},
    {"id": "saturno", "nome": "Saturno", "curto": "Saturno", "tipo": "planeta", "faccao": "natural", "raioOrbita": 1433530000000.0, "excentricidade": 0.0565, "argPeriapse": 1.6132, "fase": 5.542, "gm": 3.7931187e+16, "raio": 58232000.0, "cor": "#E0CF9E", "pousavel": false},
    {"id": "encelado", "nome": "Encélado", "curto": "Encélado", "tipo": "planeta", "faccao": "natural", "pai": "saturno", "raioOrbita": 238020000.0, "excentricidade": 0.0047, "fase": 0.3, "gm": 7211000000.0, "raio": 252100.0, "cor": "#EDEFF2"},
    {"id": "reia", "nome": "Reia", "curto": "Reia", "tipo": "planeta", "faccao": "natural", "pai": "saturno", "raioOrbita": 527040000.0, "excentricidade": 0.001, "fase": 2.6, "gm": 153940000000.0, "raio": 763800.0, "cor": "#B8B5AE"},
    {"id": "titan", "nome": "Titã", "curto": "Titã", "tipo": "planeta", "faccao": "natural", "pai": "saturno", "raioOrbita": 1221870000.0, "excentricidade": 0.0288, "fase": 4.1, "gm": 8978100000000.0, "raio": 2574700.0, "cor": "#D6A24A", "escalaAtmosfera": 21000, "velTerminalAtmosfera": 6},
    {"id": "estacao_meridiano", "nome": "Estação Meridiano", "curto": "Meridiano", "tipo": "estacao", "faccao": "coalizao", "pai": "terra", "raioOrbita": 42000000.0, "fase": 0.4, "tamanho": 150},
    {"id": "base_selene", "nome": "Base Selene", "curto": "Selene", "tipo": "estacao", "faccao": "coalizao", "pai": "lua", "raioOrbita": 2600000.0, "fase": 1.1, "tamanho": 120},
    {"id": "arsenal_ares", "nome": "Arsenal Ares", "curto": "Ares", "tipo": "estacao", "faccao": "federacao", "pai": "marte", "raioOrbita": 9400000.0, "fase": 2.2, "tamanho": 150},
    {"id": "porto_ceres", "nome": "Porto Ceres", "curto": "P. Ceres", "tipo": "estacao", "faccao": "liga", "pai": "ceres", "raioOrbita": 1500000.0, "fase": 0.2, "tamanho": 150},
    {"id": "entreposto_ganimedes", "nome": "Entreposto Ganimedes", "curto": "Entreposto", "tipo": "estacao", "faccao": "liga", "pai": "ganimedes", "raioOrbita": 6500000.0, "fase": 0.8, "tamanho": 130},
    {"id": "posto_tita", "nome": "Posto Titã", "curto": "P. Titã", "tipo": "estacao", "faccao": "liga", "pai": "titan", "raioOrbita": 4200000.0, "fase": 1.7, "tamanho": 130},
    {"id": "corsario_vesper", "nome": "Corsário Vésper", "curto": "Vésper", "tipo": "nave", "faccao": "corsarios", "pai": "terra", "raioOrbita": 200000000.0, "fase": 1.0, "tamanho": 70, "atracavel": false, "oculto": true, "misseis": 3, "despistadores": 1, "chanceDespistar": 0.4, "defesa": 0.35, "aceleracaoMax": 8.0}
  ],
  "nave": {"nome": "Cargueiro Selene", "massaSeca": 240000, "carga": 25000, "combustivel": 90000, "nomeCarga": "25 t de equipamento médico e água", "perto": "estacao_meridiano", "altitude": 30000, "alvo": "lua", "casco": 100, "despistadores": 0, "chanceDespistar": 0.5, "aceleracaoMax": 14.0, "velocidadeExaustao": 16000000.0},
  "creditos": 300,
  "reputacao": {"coalizao": 0, "federacao": 0, "liga": 0, "natural": 0, "corsarios": -20},
  "personagens": [
    {"id": "dra_vega", "nome": "Dra. Vega", "papel": "médica-chefe da Base Selene", "faccao": "coalizao"},
    {"id": "capitao_vesper", "nome": "Capitão Vésper", "papel": "capitão do Vésper", "faccao": "corsarios"}
  ],
  "dialogos": [
    {"id": "d_contrato", "personagem": "dra_vega", "texto": "“Cargueiro Selene, aqui é a Dra. Vega, da Base Selene. Nossa enfermaria está sem equipamento e a cisterna está quase seca. A base fica na superfície, na plataforma A-1: o piloto automático faz a descida assistida, mas você precisa de empuxo para frear. Posso pagar mais se chegar em 12 horas.”", "escolhas": [{"texto": "Aceitar o prazo de 12 horas", "detalhe": "Bônus: 2400 créditos se pousar a tempo, 1400 se atrasar. Coalizão +2.", "efeitos": [{"tipo": "flag", "nome": "urgente"}, {"tipo": "reputacao", "faccao": "coalizao", "delta": 2}, {"tipo": "mensagem", "texto": "Prazo de 12 h aceito. Pouse na Lua, na plataforma A-1 da Base Selene."}]}, {"texto": "Entregar sem pressa", "detalhe": "Paga 1400 créditos, sem prazo.", "efeitos": [{"tipo": "mensagem", "texto": "Sem prazo. Pouse na Lua, na plataforma A-1 da Base Selene."}]}]},
    {"id": "d_vesper", "personagem": "capitao_vesper", "texto": "“Cargueiro Selene, aqui é o Vésper. Estamos a um passo de abordar. A carga da Base Selene vale muito para quem tem sede. Pague o pedágio ou levamos tudo.”", "escolhas": [{"texto": "Pagar o pedágio", "detalhe": "300 créditos. O Vésper vai embora.", "se": {"tipo": "creditos", "min": 300}, "bloqueio": "Você não tem 300 créditos.", "efeitos": [{"tipo": "creditos", "delta": -300}, {"tipo": "flag", "nome": "pagou_vesper"}, {"tipo": "destruir_corpo", "corpo": "corsario_vesper", "explosao": false}, {"tipo": "mensagem", "texto": "Pedágio pago. O Vésper se afasta."}]}, {"texto": "Resistir", "detalhe": "35% de chance de repelir. Se falhar, a carga é apreendida.", "efeitos": [{"tipo": "sorteio", "chance": {"base": 0.35}, "sucesso": [{"tipo": "destruir_corpo", "corpo": "corsario_vesper"}, {"tipo": "reputacao", "faccao": "coalizao", "delta": 3}, {"tipo": "mensagem", "texto": "Você repeliu o Vésper!"}], "falha": [{"tipo": "perder_carga"}, {"tipo": "missao", "id": "m_entrega", "estado": "falhou"}, {"tipo": "final", "id": "apreendido"}]}]}]}
  ],
  "blocos": [],
  "regras": [
    {"id": "r_inicio", "quando": {"tipo": "inicio"}, "entao": [{"tipo": "dialogo", "id": "d_contrato"}, {"tipo": "mensagem", "texto": "Missão: pousar na Lua com 25 t de suprimentos. Selecione ALVO Lua e toque em INTERC.: o piloto automático faz a descida."}]},
    {"id": "r_abordado", "quando": {"tipo": "abordado", "corpo": "corsario_vesper"}, "entao": [{"tipo": "dialogo", "id": "d_vesper"}]},
    {"id": "r_prazo", "quando": {"tipo": "tempo", "depoisDe": 43200}, "se": {"tipo": "e", "condicoes": [{"tipo": "flag", "nome": "urgente"}, {"tipo": "nao", "condicao": {"tipo": "flag", "nome": "entregou"}}]}, "entao": [{"tipo": "flag", "nome": "atrasou"}, {"tipo": "mensagem", "texto": "O prazo de 12 horas estourou: o bônus foi perdido, mas a base ainda espera a entrega."}]},
    {"id": "r_pouso_rapido", "quando": {"tipo": "atracar", "local": "lua"}, "se": {"tipo": "e", "condicoes": [{"tipo": "tem_carga"}, {"tipo": "flag", "nome": "urgente"}, {"tipo": "nao", "condicao": {"tipo": "flag", "nome": "atrasou"}}]}, "entao": [{"tipo": "perder_carga"}, {"tipo": "creditos", "delta": 2400}, {"tipo": "flag", "nome": "entregou"}, {"tipo": "flag", "nome": "rapido"}, {"tipo": "reputacao", "faccao": "coalizao", "delta": 5}, {"tipo": "missao", "id": "m_entrega", "estado": "concluida"}, {"tipo": "missao", "id": "m_volta", "estado": "ativa"}, {"tipo": "noticia", "texto": "Base Selene recebe suprimentos dentro do prazo; enfermaria reabre."}, {"tipo": "mensagem", "texto": "Carga entregue a tempo: +2400 créditos. Agora decole e volte à Estação Meridiano."}]},
    {"id": "r_pouso", "quando": {"tipo": "atracar", "local": "lua"}, "se": {"tipo": "e", "condicoes": [{"tipo": "tem_carga"}, {"tipo": "nao", "condicao": {"tipo": "flag", "nome": "entregou"}}]}, "entao": [{"tipo": "perder_carga"}, {"tipo": "creditos", "delta": 1400}, {"tipo": "flag", "nome": "entregou"}, {"tipo": "reputacao", "faccao": "coalizao", "delta": 3}, {"tipo": "missao", "id": "m_entrega", "estado": "concluida"}, {"tipo": "missao", "id": "m_volta", "estado": "ativa"}, {"tipo": "noticia", "texto": "Base Selene recebe equipamento médico e água; a cisterna volta a encher."}, {"tipo": "mensagem", "texto": "Carga entregue: +1400 créditos. Agora decole (precisa de empuxo acima de 1,62 m/s²) e volte à Estação Meridiano."}]},
    {"id": "r_volta_rapido", "quando": {"tipo": "atracar", "local": "estacao_meridiano"}, "se": {"tipo": "e", "condicoes": [{"tipo": "flag", "nome": "entregou"}, {"tipo": "flag", "nome": "rapido"}]}, "entao": [{"tipo": "creditos", "delta": 300}, {"tipo": "missao", "id": "m_volta", "estado": "concluida"}, {"tipo": "final", "id": "rapido"}]},
    {"id": "r_volta", "quando": {"tipo": "atracar", "local": "estacao_meridiano"}, "se": {"tipo": "e", "condicoes": [{"tipo": "flag", "nome": "entregou"}, {"tipo": "nao", "condicao": {"tipo": "flag", "nome": "rapido"}}]}, "entao": [{"tipo": "creditos", "delta": 300}, {"tipo": "missao", "id": "m_volta", "estado": "concluida"}, {"tipo": "final", "id": "entregue"}]},
    {"id": "r_combustivel", "quando": {"tipo": "combustivel_abaixo", "pct": 25}, "entao": [{"tipo": "mensagem", "texto": "ALERTA: combustível abaixo de 25%. Pousar e decolar gasta propelente."}]},
    {"id": "r_destruida", "quando": {"tipo": "nave_destruida"}, "entao": [{"tipo": "missao", "id": "m_entrega", "estado": "falhou"}, {"tipo": "final", "id": "destruido"}]}
  ],
  "missoes": [
    {"id": "m_entrega", "titulo": "Suprimentos para a Lua", "texto": "Pousar na plataforma A-1 da Base Selene, na superfície da Lua, com 25 t de equipamento médico e água. Selecione ALVO Lua e use INTERC. para a descida assistida.", "inicial": true},
    {"id": "m_volta", "titulo": "Volta para casa", "texto": "Decolar da Lua e atracar na Estação Meridiano, em órbita da Terra."}
  ],
  "finais": [
    {"id": "rapido", "titulo": "ENTREGA RELÂMPAGO", "tipo": "vitoria", "manchete": "Cargueiro pousa na Lua dentro do prazo e volta à órbita; Base Selene reabre a enfermaria."},
    {"id": "entregue", "titulo": "SUPRIMENTOS ENTREGUES", "tipo": "vitoria", "manchete": "Cargueiro entrega suprimentos na Base Selene e decola de volta; a base agradece."},
    {"id": "destruido", "titulo": "NAVE DESTRUÍDA", "tipo": "derrota", "manchete": "Cargueiro Selene se perde na rota para a Lua; Coalizão abre inquérito."},
    {"id": "apreendido", "titulo": "CARGA APREENDIDA", "tipo": "derrota", "manchete": "Corsários abordam cargueiro entre a Terra e a Lua e levam os suprimentos da Base Selene."}
  ],
  "noticias": [
    "Base Selene pede suprimentos: enfermaria sem equipamento e cisterna quase seca.",
    "Coalizão reforça a ponte de carga Terra–Lua.",
    "Corsários do Vésper são vistos entre a Terra e a Lua."
  ],
  "emboscadas": [
    {"id": "e_vesper", "nave": "corsario_vesper", "faccao": "corsarios", "chance": 0.2, "perto": "terra", "raio": 600000000.0, "alvo": "lua", "comportamento": "abordar", "distancia": 25000, "intervalo": 7200, "mensagem": "CONTATO HOSTIL: o Corsário Vésper surgiu a 25 km!", "efeitos": [{"tipo": "noticia", "texto": "Corsários rondam a ponte de carga Terra–Lua."}]}
  ]
}

```

## Como usar
1. Entregue este arquivo a uma IA e peça: "crie uma campanha nova seguindo este guia: [sua ideia]".
2. Salve só o JSON que ela devolver, com extensão .json.
3. No jogo, em Campanhas, toque em Importar. Se houver erros, copie-os e devolva à IA para corrigir.
