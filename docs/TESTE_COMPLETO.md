# Roteiro de teste completo (no aparelho)

Tudo abaixo já é coberto por testes automáticos no `core` (`./gradlew :core:test`) e por capturas em `docs/preview`,
mas **o app nunca rodou num aparelho**: este roteiro é para você conferir o que só o aparelho mostra (toque, FPS,
arquivos). Gere o APK no Android Studio (`app` → Run).

## 1. Início e salvamentos
- [ ] A tela de início lista 5 campanhas (Cálice, Albatroz, Patrulha, Emboscada, Rota do Gelo), cada uma com o papel
      (mercador ou militar).
- [ ] **JOGAR** abre o voo. No menu (**MENU → SALVAR JOGO**) aparece "Jogo salvo". Salve duas vezes.
- [ ] **MENU → SAVES**: aparecem "Jogo 2", "Jogo 1" e "Automático". **NOME** abre uma caixa de texto (teclado): renomeie.
      **✕** apaga só no segundo toque. **CARREGAR** volta ao ponto salvo.
- [ ] Na tela de início, **CONTINUAR** carrega o mais recente e **SAVES · n** abre a lista.

## 2. Voo, câmera e controles
- [ ] **GIRAR ◀ ▶** e **MOTOR** funcionam segurando, e juntos (dois dedos).
- [ ] **VER EXTERNA / VER 3D / VER CABINE** alterna as três vistas.
- [ ] Câmera: **⟲ ⟳** giram segurando; **arrastar um dedo** na cena gira (na 3D, vertical inclina); **pinça** dá zoom;
      **●** centraliza. **▲ ▼** inclinam a 3D.
- [ ] Chegada no painel: **CHEGADA EM** (tempo de jogo) e **REAL ×1.440** (tempo real). Escolha **×1,4k** (cruzeiro):
      5 dias de jogo devem durar cerca de 5 minutos reais.

## 3. Cálice (mercador, política)
- [ ] Rádio da fragata por volta de 19.000 s com 4 escolhas; a 3 fica bloqueada se Marte < −29.
- [ ] Escolher **Cortar o rádio**: a fragata persegue. Finais com manchetes diferentes.

## 4. Emboscada (mercador, mísseis)
- [ ] Rádio logo no início; **Cortar o rádio e acelerar**: surge **ALERTA DE MÍSSIL · IMPACTO EM …**.
- [ ] **DESPISTADOR ×4** acende e funciona (cada míssil, ~50%); mensagem "funcionou" ou "falhou".
- [ ] Impacto: clarão vermelho, vibração e o casco cai; dois impactos destroem a nave.

## 5. Patrulha (militar)
- [ ] Cruzador da Coalizão (desenho próprio). Chamado do Corvo perto dele: ordenar rendição, abrir fogo ou deixar passar.
- [ ] **MÍSSIL ×8** acende quando o Corvo é alvo e está a menos de 150 km. **ABORDAR** acende a menos de 1 km e menos de 12 m/s.
- [ ] Atracar na Doca Meridiano repara o casco e recarrega os mísseis.
- [ ] Botão **RADAR** ao lado de MÍSSIL: **pisca** com míssil em voo (inclusive o seu) ou hostil armado a menos de 200 km.
- [ ] No radar: alvo travado (**TRAVADO**), anéis de alcance verde (seu) e vermelho (deles), mísseis com tempo até o impacto,
      **SAIR** volta à vista anterior (o botão voltar do Android também sai do radar).
- [ ] **Arrastar a nave**: aparece a seta âmbar da meta; o piloto passa a **VELOCIDADE**, gira e queima até a velocidade
      relativa chegar na meta. **IGUALAR** leva a velocidade relativa a zero e **SOLTAR** devolve o controle manual.
      Tocar num inimigo o seleciona; **+ −** mudam o zoom; **DESPIST.** pisca quando há míssil vindo.

## 6. Rota do Gelo (Sistema Solar real)
- [ ] Início: Capitã Ruiz oferece a **escolta** (300 créditos). O Cruzador Aurora passa a acompanhar você.
- [ ] **MAPA**: Sol no centro, órbitas elípticas, luas em torno dos planetas. **CENTRO** alterna origem/nave/alvo/nave+alvo;
      **AJUSTAR** enquadra nave e alvo; **+ −** dão zoom de 2×. Toque num corpo para escolhê-lo como alvo.
- [ ] **PLANO DE ROTA** mostra "calculando… n%" e depois 3 opções (~9 d, ~5,7 d, ~4 d até Marte).
- [ ] Após ~1,5 h de jogo os corsários da Gralha surgem a 25 km e tentam **abordar**; com escolta, o cruzador atira neles.
      Pagar o pedágio ou resistir mudam o final.
- [ ] Atracar no Arsenal Ares: **cena de atracação** e depois a estação.

## 7. Importar e exportar
- [ ] **EXPORTAR TEMPLATE** grava o `.md` (guia, vocabulário, exemplo). **IMPORTAR PACOTE JSON** aceita um `.json`
      válido; um quebrado mostra os erros em português e **COPIAR ERROS** copia para a área de transferência.
- [ ] "Abrir com…" um `.json` de campanha também importa.
