# Guia para criar uma campanha para o jogo

Você vai escrever **um único arquivo JSON** que o jogo importa sem recompilar. O jogo tem física newtoniana real (inércia, gravidade do planeta, empuxo só na direção do nariz, combustível limitado, tempo acelerado). O pacote descreve o universo, as pessoas, as missões, o mistério e os finais; o jogo faz o resto. Nada no pacote executa código.

## Papel do jogador (vale a campanha inteira e não muda durante o jogo)
- `{"tipo": "mercador"}`: o jogador pilota um cargueiro independente, sem armas. Se uma nave hostil o atacar, só pode **lançar despistadores** (cada um tem uma chance aleatória, `chanceDespistar`, de desviar cada míssil).
- `{"tipo": "militar", "faccao": "id"}`: o jogador comanda o **cruzador** dessa facção, com mísseis (`nave.misseis`) e casco mais forte. Se `misseis` e `despistadores` não forem informados, a nave começa com **10 mísseis e 10 despistadores** (e casco 220). Pode abater naves hostis; não dispara em naves da própria facção.
- `escalaTempo` é quantos segundos de jogo passam por segundo real no nível "cruzeiro" do relógio (1440 = um dia de jogo por minuto real; uma viagem de 5 dias dura 5 minutos).
- Naves de facções aparecem como **cruzadores estelares**; `estilo` escolhe o desenho (terrano, marciano, cinturao ou generico).
- **Corpos reais**: um corpo pode orbitar outro (`pai`), com órbita elíptica (`excentricidade`, `argPeriapse`), e ter massa (`gm`) e raio (`raio`): planetas e luas puxam a nave e a destroem se ela encostar rápido demais: com velocidade relativa abaixo de 6 m/s (15 m/s com atmosfera) ela **pousa**. Todo planeta ou lua é pousável (`pousavel`, padrão `true`); marque `"pousavel": false` nos gigantes gasosos. O INTERCEPTAR num corpo pousável faz a descida assistida e o pouso; pousar conta como atracar (`atracar` com o `local` do corpo). Com `escalaAtmosfera` (m) e `velTerminalAtmosfera` (m/s, com paraquedas) o corpo ganha atmosfera com arrasto. Decolar exige empuxo maior que a gravidade de superfície. `tipo: "planeta"` desenha uma esfera (`cor` opcional). O corpo central pode ser uma estrela (`planeta.tipo: "estrela"`; o Sol tem gm 1.32712e20 e raio 6.957e8). Em escala solar, ajuste a nave: `aceleracaoMax` (m/s²) e `velocidadeExaustao` (m/s), por exemplo 6 e 5e6.
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
  "corpos": [{"id": "...", "nome": "...", "curto": "...", "tipo": "estacao|nave|destroco|planeta", "faccao": "id", "raioOrbita": 3.2e8, "fase": 0.0, "tamanho": 150, "atracavel": true, "misseis": 4, "despistadores": 2, "chanceDespistar": 0.4, "pai": "id", "excentricidade": 0.0, "argPeriapse": 0.0, "gm": 0, "raio": 0, "cor": "#RRGGBB", "oculto": false, "defesa": 0.4, "chanceInterceptar": 0.5, "aceleracaoMax": 3.0, "pousavel": true, "escalaAtmosfera": 8500, "velTerminalAtmosfera": 9}],
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
- `inicio`: No começo da partida. Campos: (sem campos)
- `atracar`: Quando a nave atraca em um corpo atracável. Campos: `local` (id de corpo): onde atracou
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
- `atracado`: A nave está atracada (em um lugar específico, se informado). Campos: `local` (id de corpo, opcional): onde (opcional)
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
    {"id": "r_inicio", "quando": {"tipo": "inicio"}, "entao": [{"tipo": "mensagem", "texto": "Missão: descobrir quem desligou o reator do Albatroz."}]},
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

## Como usar
1. Entregue este arquivo a uma IA e peça: "crie uma campanha nova seguindo este guia: [sua ideia]".
2. Salve só o JSON que ela devolver, com extensão .json.
3. No jogo, em Campanhas, toque em Importar. Se houver erros, copie-os e devolva à IA para corrigir.
