# AeroStation - FrontEnd

Um aplicativo que organiza suas ROMs com estética inspirada em Frutiger Aero e XMB com um toque de menu do Nintendo Switch.

## Estado atual do projeto

Atualmente o projeto conta com funções avançadas como contador de horas com um marcador de ultima conquista desbloqueada, página dedicada de conquistas em jogos, página com carrocel de apps, possibilidade de navegar entre os jogos com controle fisico ou digital, adicionar jogos android e recompilações a tela inicial, galeria de capturas de tela e gravação de video, dentre outros.

Os emuladores suportados e testados incluem:

| Plataforma       | Emulador            |
| -------------- | ------------------------ |
| GBA | MyBoy! e LinkBoy! (ainda falta o Pizzaboy para teste) |
| GB | LinkBoy! e Retroarch|
| GBC | LinkBoy! e Retroarch|
| NES | Retroarch|
| SNES | Retroarch|
| Sega Genesis| Retroarch |
| Sega CD| Não testado (em breve) |
| MasterSystem | Não testado (em breve) |
| N64 | Retroarch|
| NDS | Drastic, Watermelon e SeedlessDS|
| 3DS | Citra MMJ (opção lowend) e Azahar |
| Switch | Eden e Skyline Edge |
| WiiU | Cemu Emulator (Ainda não testado/implementado) |
| PSP | PPSSPP (O GOAT) |
| PS1 | Duckstation |
| PS2 | AetherSX2/NetherSX2 |
| PS3 | Não testado |
| PSVita | Vita3K Plus |
| DreamCast| Redream e Flycast |
| Java ME | J2ME Loader |
| Xbox Classico | Não testado |
| Xbox 360 e One | Não testado |

## Notas e adições futuras

Atualmente jogos de PS3 e Xbox One e 360 são os mais complicados para testar e implementar o boot corretamente, pois não tenho um dispositivo forte o suficiente para rodar jogos dessas duas plataformas de maneira satisfatória para teste. 

Você deve estar se perguntando também do porque a escolha do NetherSX2 ao invés de ArmSX2, e a resposta é simples, atualmente o ArmSX2 não tem suporte a boot de jogos através de FrontEnds, por essa razão não consegui implementar esse suporte, caso no futuro ele venha a ser adicionado ficarei feliz em adicionar suporte a esse emulador no aplicativo.

O mesmo deve ser valido para o ArmSX3 que atualmente pode ser considerado o único emulador realmente promissor de PS3 que entrega um resultado interessante. (Sou leigo no assunto, então desculpem se falei besteira)

O Cemu Android também será adicionado futuramente, junto do suporte a SegaCD e Sega Saturn.


## Qual o intuito desse projeto a final?

Honestamente, eu o fiz por diversão e aprendizado, mas também queria criar um app que pudesse trazer um sentimento de console mesmo sem a necessidade de um controle fisico. Apps como o IISU podem até entregar uma interface bonita mas a navegação com sensação de console depende muito de um controle fisico. No geral esse problema seria resolvido com um controle digital como adicionei no meu proprio app, mas acredito que cada aplicativo ou projeto tem sua propria filosofia e é isso que importa.

## Customizações

Tenho muitas ideias de customizações no futuro e com certeza quero adicionar suporte a pacotes de temas que as proprias pessoas podem criar. Quero permitir que os usuarios mudem o fundo, os sons, o estilo dos controles, e talvez até o layout. Porém, isso vai demandar tempo e pensamento, preciso planejar como vou adicionar tantas customizações de uma vez, e de forma simples de se fazer, ninguém merece ficar horas na frente de um monte de linha de código quebrando a cabeça só para mudar a cor de um botão lol.

## Esse projeto é AI Sloop?

Definitivamente não, toda a ideia por trás do APP foi minha, porém, eu usei o OpenCode com o modelo Muze Spark 1.3 Zeen para acelerar e concertar erros irritantes de programação. Em outras palavras a IA foi uma ferramenta e não uma muleta. 

Isso significa que eu não disse "faça um app assim" e ela mágicamente criou ele com tudo pronto. No total levou cerca de 2 semanas para chegar em um estado ideal com o projeto, de forma que ele funcionasse sem erros grotescos.

## Encontrei um erro, o que fazer?

Se um emulador seu não abrir/executar um jogo você pode abrir um issue aqui e me dizer qual emulador você está tentando usar para executar um jogo, e a plataforma também que você quer jogar, com isso posso adicionar o nome de pacote do app na programação para tentar bootar os jogos nele. Existem emuladores que não conseguem abrir jogos assim, como o Armsx2 por exemplo, e se esse for o caso informarei como resposta essa limitação e o unico jeito é esperar que o desenvolvedor do app adicione suporte.

Se o aplicativo crashar uma janela deve aparecer na sua tela falando que houve um crash e pedindo para você copiar ou compartilhar o log, nesse caso copie o log, abra um issue e diga o que aconteceu deixando o log para que eu possa analisar e concertar o problema. (Ou pelo menos tentar resolver)

## E jogos de PC? (Winlator/Gamehub/Gamenative/etc)

Não faço ideia de como adicionar suporte a esses apps aqui, mas pretendo sim tentar em uma versão futura.

## Creditos e agradecimentos

A música de fundo do app foi feita por um compositor chamado A-duration Music, e a música em questão se chama "Glass". Eu não sou um bom compositor então decidi usar a música dele nesse projeto por ele mesmo incentivar e permitir o uso livre das músicas dele. Então, nesse caso agradeço muito, mas se houver problema futuramente com copyright, a música e as versões com ela associada serão removidas.