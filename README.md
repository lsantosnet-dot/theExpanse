# The Expanse (título provisório)

Jogo de nave com **física newtoniana** e **política espacial** para Android (Kotlin, só paisagem). O universo e as
histórias são **originais**, no espírito da ficção científica "hard" de colonização, sem personagens, nomes ou enredo
de obras existentes. Tudo o que é história vive em **pacotes JSON importáveis**: dá para criar campanhas novas (com
ajuda de uma IA) e jogá-las no aparelho sem recompilar.

## O que o jogo tem

- **Física real**: gravidade do planeta, inércia, empuxo só na direção do nariz (para frear é preciso girar e queimar),
  combustível pela equação do foguete, corpos em órbita analítica, previsão balística, tempo de ×1 a ×5000 com limite
  automático de segurança, atracação e colisão.
- **Piloto automático**: interceptar, alinhar ao prógrado/retrógrado, parar o giro. Alerta "INVERTER AGORA".
- **Cabine e câmera externa**, que alternam. Ao queimar, a nave solta um jato de energia azul em movimento; na cabine
  isso vira luz azul, vibração e o jato na câmera traseira. Ao frear, a nave vira de verdade (propulsores de manobra,
  estrelas em risco).
- **Mapa tático**, **plano de rota** (tempo e combustível reais de 3 níveis de empuxo, simulados com o próprio piloto
  automático), **estações** com pessoas para conversar, **diálogos com escolhas**, **caderno de investigação**
  (pistas → deduções → acusação), **porão** (objetos têm massa e pesam na física), **reputação** de facções,
  **notícias**, **finais** e **salvamento automático**.
- **Perseguição**: a fragata do Cálice passa a ser integrada (3 m/s², para a 20 km) quando você corta o rádio.
- **Campanhas** incluídas: **Cálice** (política e rádio, 8 finais) e **O silêncio do Albatroz** (mistério de
  investigação em 4 lugares, 6 pistas, 3 suspeitos, 4 finais).

### Telas (geradas pelo próprio código em `docs/preview`)

| Início | Importação recusada |
|---|---|
| ![](docs/preview/01_inicio.png) | ![](docs/preview/02_erros_importacao.png) |

| Acelerando (cabine) | Frenando de costas (externa) |
|---|---|
| ![](docs/preview/03_cabine_aceleracao.png) | ![](docs/preview/07_externa_frenando.png) |

| Mapa tático | Plano de rota |
|---|---|
| ![](docs/preview/08_mapa.png) | ![](docs/preview/09_rota.png) |

| Rádio da fragata | Caderno |
|---|---|
| ![](docs/preview/10_radio.png) | ![](docs/preview/15_caderno_acusar.png) |

Mais: [virada de 180° na cabine](docs/preview/sequencia_virada_cabine.png) ·
[virada na externa](docs/preview/sequencia_virada_externa.png) · [estação](docs/preview/12_estacao_destrocos.png) ·
[diálogo](docs/preview/13_dialogo_ilse.png) · [porão](docs/preview/16_porao.png) ·
[notícias](docs/preview/17_noticias.png) · [final](docs/preview/20_final_albatroz.png).

## Criar uma campanha nova

1. No app, em **Campanhas → Exportar template**: sai um arquivo com o guia, o vocabulário completo (gatilhos,
   condições e efeitos) e um exemplo válido. A cópia atual está em [`docs/the_expanse_template.md`](docs/the_expanse_template.md).
2. Entregue o arquivo a uma IA e peça "crie uma campanha nova seguindo este guia: [sua ideia]". Salve o JSON.
3. **Importar pacote JSON** (ou "Abrir com…" no Android). O jogo decodifica, valida e só então instala.

JSON é **dado, não código**: o pacote combina o que o motor sabe fazer (órbitas, naves, facções, diálogos, regras,
pistas, deduções, finais), mas não inventa mecânica nova. Nada executa no aparelho, e nenhuma IA é usada durante o jogo.

### O validador

Erros citam o caminho do trecho, em português, para devolver à IA:

```
regras[1].entao[0]: efeito 'dar_objto' desconhecido (válidos: mensagem, reputacao, ..., dar_objeto, ...)
O final 'verdade' nunca é alcançável: a acusação 'ac_atlas_prova' exige: a dedução 'ded_armacao' nunca é alcançável.
corpo 'lab': órbita de 50000000 m fica dentro ou colada no planeta (raio 60000000 m).
nave: Δv de 1500 m/s é pequeno demais para viagens entre estações (mínimo 2000 m/s; ...).
```

Ele confere referências, órbitas, **física** (o piloto automático consegue chegar às estações citadas?) e a
**alcançabilidade do mistério**: simula as regras de forma abstrata e acusa pistas que ninguém revela, objetos que
ninguém entrega, deduções impossíveis e finais inalcançáveis (exige ao menos um final de vitória).

## Estrutura

```
core/     Kotlin/JVM puro (sem Android), testado
  core/     Vec2, Config, World/Body, Ship, Autopilot, Simulation (inclui corpos dinâmicos e salvamento)
  content/  Model, Vocabulary (fonte única do guia e da validação), Decoder, Validator, Template, Builtin
  game/     GameSession (motor de história: gatilhos, efeitos, diálogos, deduções), RoutePlanner, Storage/Host
  render/   lista de desenho (Prim), Fx, CockpitView, ExternalView, Hud, Screens, AppController
  resources/packs/  calice.json, albatroz.json
preview/  rasteriza a lista de desenho em PNG (Java2D) — gera docs/preview
app/      Android: Compose Canvas desenha a lista; seletor de arquivos, compartilhar, área de transferência
```

Toda a interface é uma lista de primitivas num espaço fixo de 844×390, montada no `core`; o Android e o preview só a
desenham. Por isso as telas e os fluxos de toque são testados sem aparelho.

## Como rodar

```
./gradlew :core:test            # 41 testes: física, pacotes, validador, história, telas e fluxos de toque
./gradlew :preview:run          # gera os PNGs em docs/preview e o template
./gradlew :app:assembleDebug    # APK (precisa de Android SDK: ANDROID_HOME ou local.properties com sdk.dir)
```

## Validação

O piloto automático reproduz os números do protótipo web (passo de 0,14 s): atraca no Porto Kauã em 27.152 s (50%),
21.248 s (100%) e 41.493 s (20%); o rádio chama em 18.910 s; a distância mínima ao centro de Aurélia é de 265.086 km.
Há testes de partida completa das duas campanhas (inclusive todos os finais do Cálice e a solução do mistério pelo
piloto automático), salvamento e carga, importação de pacotes válidos e quebrados e reinício com estado inicial exato.

## O que não foi verificado

- **O APK compila (`assembleDebug`), mas nunca foi executado** em aparelho nem em emulador. Toque, multitoque, FPS,
  tela cheia, seletor de arquivos, "Abrir com…" e área de transferência estão por testar de verdade.
- O carregamento das campanhas incluídas (resources do `core`) foi conferido dentro do APK, mas não em execução.
- Os PNGs vêm do rasterizador Java2D; a fonte e o anti-serrilhado no Android serão um pouco diferentes.

## Próximos passos

Sistema Solar real (elementos orbitais) e vista 3D; escoltas e abordagens como mecânica do motor; mais tipos de
gatilho e efeito (o vocabulário é o que limita os pacotes); edição de nome dos jogos salvos; mais de um salvamento
por campanha.
