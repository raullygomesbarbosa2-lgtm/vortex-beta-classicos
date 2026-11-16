# Vortex Beta Clássicos

Projeto Android criado como base nova, separado dos apps anteriores. Seleciona arquivos ROM locais pelo Storage Access Framework e carrega uma ROM no sistema escolhido usando LibretroDroid.

## Dez sistemas e seus núcleos

Atari 2600 (Stella), NES (FCEUmm), SNES (Snes9x), Game Boy (Gambatte), Game Boy Advance (Beetle GBA), Sega Genesis/Mega Drive (PicoDrive), Sega Master System (Genesis Plus GX), PC Engine/TurboGrafx-16 (Beetle PCE Fast), Atari Lynx (Beetle Lynx) e WonderSwan (Beetle WonderSwan).

É um núcleo compatível por sistema, não dez variantes de núcleo para cada console. Os núcleos são compilados do código oficial Libretro para ARM64 no GitHub Actions. ROM hacks devem ser arquivos patchados pelo usuário; o APK não distribui ROMs nem BIOS.

## Requisitos e situação

- Android 6.0+ (minSdk 23), ARM64 nesta Beta inicial.
- A assinatura é de CI e temporária; em futuras builds Android pode exigir desinstalar a anterior.
- A primeira build precisa concluir e ser testada em aparelho físico. Compatibilidade dos hacks depende do sistema, formato e patch utilizado.
- Confira as licenças dos núcleos e não distribua conteúdo de jogos sem autorização.
