# KeyFast
**Digite menos. Resolva mais.**

Teclado Android com atalhos pessoais para preencher dados recorrentes de destinatários.

## Recursos
- Teclado QWERTY para digitação cotidiana.
- Cadastro, edição e exclusão de atalhos.
- Sugestão explícita: só expande após toque do usuário.
- Insere o nome e solicita avanço para o próximo campo para inserir o documento.
- Funciona offline; dados criptografados com AES-GCM e chave protegida pelo Android Keystore.
- Não armazena nem reutiliza assinaturas. Não confirma nem salva entregas.

## Ativação
1. Instale o APK.
2. Abra KeyFast e toque em **Ativar teclado**.
3. Habilite KeyFast nas configurações de teclado do Android.
4. Toque em **Testar / selecionar teclado** e escolha KeyFast.
5. Cadastre os atalhos.

## Compatibilidade
O preenchimento do segundo campo depende do aplicativo de destino aceitar a ação IME Next ou navegação por Tab. O comportamento deve ser validado no app de entrega antes de uso operacional. KeyFast não salva a entrega nem confirma assinatura.

## Compilar
Execute `gradle assembleDebug`. O APK será gerado em `app/build/outputs/apk/debug/app-debug.apk`.
