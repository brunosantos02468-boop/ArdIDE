# Arduino IDE para Android (protótipo)

IDE para Arduino Uno (clone CH340) no Android: editor, upload por USB OTG e gerenciador de bibliotecas.

## Estado atual
| Parte | Situação |
|---|---|
| Interface (tema claro, editor com realce de sintaxe, saída, status) | Pronta, simples |
| Upload de `.hex` pelo CH340 (STK500v1 / Optiboot, 115200) | Escrita, **não testada em placa** |
| Gerenciador de bibliotecas (busca online, instalar, importar .ZIP) | Escrito, **não testado** |
| Compilação offline (avr-gcc) | **Não implementada** (ver `Compiler.kt`) |
| Editor Monaco / layout idêntico à IDE desktop | Pendente |

## Como compilar
- **Android Studio:** abra a pasta, deixe sincronizar o Gradle e rode no aparelho.
- **GitHub Actions:** ao dar push, o workflow `.github/workflows/android.yml` gera o APK de debug (aba Actions → artefato `app-debug`).

## Subir no GitHub
```
git init
git add .
git commit -m "Primeiro commit"
git branch -M main
git remote add origin https://github.com/SEU-USUARIO/ArduinoIdeAndroid.git
git push -u origin main
```

## Testando o upload (sem compilador)
Gere um `.hex` na IDE do computador (Sketch → Exportar binário compilado), copie para o celular, conecte o Uno com cabo OTG e use **Abrir .hex**.

## Bibliotecas incluídas
Copie Servo, Wire, SPI, EEPROM, SoftwareSerial etc. de uma instalação da Arduino IDE para `app/src/main/assets/bundled_libraries/`. Elas são copiadas para o sketchbook na primeira execução.

## Próximos passos
1. Empacotar `avr-gcc` (ARM64) como biblioteca nativa (`jniLibs`) e implementar `AvrGccCompiler`.
2. Editor Monaco em WebView e layout igual ao da IDE desktop.
3. Abas e arquivos de sketch, Monitor Serial, aba "Incluídas".

## Segurança (o que já está no código)
HTTPS obrigatório nos downloads; checksum SHA-256 do índice verificado antes de instalar; extração de ZIP protegida contra *zip-slip* e *zip-bomb*; sem backup automático; tráfego em texto puro desativado; só a tela principal é exportada. Isso não substitui uma revisão de segurança.

## Licença
Defina a licença do seu projeto. Se copiar código da Arduino IDE (AGPL-3.0), o projeto precisa seguir a AGPL.
