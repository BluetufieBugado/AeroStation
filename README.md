# RetroAero Launcher

Protótipo inicial do app de organização de ROMs com estética Frutiger Aero + menu do Nintendo Switch.

## Como abrir

1. Abra o Android Studio (versão recente, Koala ou mais nova).
2. **File → Open** e selecione a pasta `RetroAeroLauncher`.
3. O Android Studio vai gerar automaticamente o `gradlew`/wrapper que faltam.
4. Rode num emulador ou celular físico (API 26+).

## O que já está pronto

- **AeroBackground**: fundo animado (gradiente céu/água + bolhas de vidro subindo), desenhado em Canvas — leve, sem GIF.
- **GameCard**: ícone quadrado estilo Switch, com borda de vidro (glass) e leve efeito de "pressionar".
- **HomeScreen**: grid 3 colunas com os cards sobre o fundo animado.
- Paleta de cores em `ui/theme/Color.kt` — fácil de ajustar os tons do Frutiger Aero.

## Próximos passos sugeridos

1. **Scanner de ROMs real**: ler uma pasta escolhida pelo usuário (Storage Access Framework) e popular a lista de jogos automaticamente, em vez da lista de exemplo em `HomeScreen.kt`.
2. **Capas dos jogos**: hoje é um placeholder gradiente; dá pra buscar capas via nome do arquivo (ex: base de dados como libretro-thumbnails) ou deixar o usuário escolher manualmente.
3. **Integração com LibretroDroid**: para efetivamente rodar as ROMs usando cores libretro (snes9x, mgba, fceumm etc.) — isso entra como uma nova tela de emulação com `GLSurfaceView`.
4. **Fundo customizável**: permitir que o usuário escolha um GIF/vídeo próprio, mantendo o `AeroBackground` atual como padrão/fallback.
5. **Controles on-screen**: D-pad e botões mapeáveis para quando o jogo estiver rodando.
6. **Save states e configurações por core** (opcional, mais pra frente).

## Sons e música (personalizar)

Os sons saem da caixa sintetizados em código (placeholder). Pra usar os
seus, crie a pasta `app/src/main/res/raw/`, coloque os arquivos com
exatamente estes nomes (só minúsculas, números e `_`) e compile:

| Arquivo        | Toca quando              |
| -------------- | ------------------------ |
| `bgm_loop.mp3` | música de fundo em looping |
| `sfx_move.wav` | cursor muda de botão     |
| `sfx_confirm.wav` | confirmar (A)         |
| `sfx_open.wav` | dialog abre              |
| `sfx_close.wav` | dialog fecha            |
| `sfx_launch.wav` | jogo/app inicia        |

Vale mp3, wav ou ogg (ogg é o mais leve pros efeitos). Sem os arquivos,
os sintetizados continuam. Os volumes de Ajustes > Áudio valem pros dois.

Qualquer um desses passos, é só pedir que a gente constrói em cima do que já está aqui.
