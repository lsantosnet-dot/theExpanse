# Guia para criar uma campanha para o jogo

Você vai escrever **um único arquivo JSON** que o jogo importa sem recompilar. O jogo tem física newtoniana real (inércia, gravidade do planeta, empuxo só na direção do nariz, combustível limitado, tempo acelerado). O pacote descreve o universo, as pessoas, as missões, o mistério e os finais; o jogo faz o resto. Nada no pacote executa código.

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
  "planeta": {"nome": "Aurélia", "gm": 1e17, "raio": 6e7},
  "facoes": [{"id": "...", "nome": "...", "cor": "#RRGGBB"}],
  "corpos": [{"id": "...", "nome": "...", "curto": "...", "tipo": "estacao|nave|destroco", "faccao": "id", "raioOrbita": 3.2e8, "fase": 0.0, "tamanho": 150, "atracavel": true}],
  "nave": {"nome": "...", "massaSeca": 240000, "carga": 40000, "combustivel": 32000, "nomeCarga": "...", "perto": "id de corpo", "altitude": 40000, "alvo": "id de corpo"},
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
  "finais": [{"id": "...", "titulo": "...", "manchete": "...", "tipo": "vitoria|derrota|neutro"}],
  "noticias": ["manchetes iniciais"]
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
- `nave_destruida`: Quando a nave cai no planeta. Campos: (sem campos)

## Condições (`se`: {"tipo": "...", ...})
- `tem_objeto`: O jogador tem o objeto. Campos: `objeto` (id de objeto): qual
- `sabe`: O jogador conhece a pista. Campos: `fato` (id de fato): qual
- `flag`: Uma marca de história está (ou não) ligada. Campos: `nome` (texto): nome da marca; `valor` (verdadeiro/falso, opcional): padrão: verdadeiro
- `reputacao`: Reputação com uma facção dentro de uma faixa (-100 a 100). Campos: `faccao` (id de facção): qual; `min` (inteiro, opcional): mínimo; `max` (inteiro, opcional): máximo
- `creditos`: Créditos suficientes. Campos: `min` (inteiro): mínimo
- `deduziu`: A dedução foi feita no caderno. Campos: `id` (id de dedução): qual
- `tem_carga`: A carga do contrato ainda está a bordo. Campos: (sem campos)
- `missao`: Estado de uma missão: ativa, concluida ou falhou. Campos: `id` (id de missão): qual; `estado` (ativa|concluida|falhou): estado
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
- `ia`: Muda o comportamento de uma nave: perseguir ou orbitar. Campos: `corpo` (id de corpo): qual nave; `comportamento` (perseguir|orbitar): o que fazer
- `alvo`: Seleciona um corpo como alvo. Campos: `corpo` (id de corpo): qual
- `perder_carga`: A carga do contrato é apreendida (a massa some). Campos: (sem campos)
- `missao`: Muda o estado de uma missão. Campos: `id` (id de missão): qual; `estado` (ativa|concluida|falhou): novo estado
- `sorteio`: Sorteia entre sucesso e falha. A chance é base + reputação × por, entre min e max. Campos: `chance` (objeto {base, faccao?, por?, min?, max?}): probabilidade (0 a 1); `sucesso` (lista de efeitos, opcional): se der certo; `falha` (lista de efeitos, opcional): se der errado

## Como montar um mistério
1. Liste as pistas (`fatos`) e onde cada uma é descoberta (um diálogo, ou uma regra `atracar`).
2. Faça a ordem importar: uma pista só aparece se o jogador já tem outra (`se: {"tipo": "sabe", ...}`) ou um objeto (`tem_objeto`).
3. `deducoes` combinam pistas no caderno e liberam consequências.
4. `acusacoes` decidem o final conforme o que o jogador sabe e carrega.
5. Distâncias reais dão peso à história: um personagem que foge em 10 dias obriga o jogador a escolher a rota.

## Exemplo completo (um mistério válido)

```json
{
  "formato": 1,
  "id": "albatroz",
  "titulo": "O silêncio do Albatroz",
  "resumo": "O cargueiro Albatroz perdeu o reator sem explicação. Siga as pistas por quatro lugares e decida quem acusar. Mistério de investigação, sem combate.",
  "autor": "The Expanse (campanha incluída)",
  "planeta": {"nome": "Aurélia", "gm": 1.0e17, "raio": 6.0e7},
  "facoes": [
    {"id": "coalizao", "nome": "Coalizão Terrana", "cor": "#5B9BF0"},
    {"id": "federacao", "nome": "Federação Marciana", "cor": "#E8645A"},
    {"id": "liga", "nome": "Liga do Cinturão", "cor": "#3FD0B8"},
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
