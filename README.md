# Tutoria assíncrona com IA

Este é um trabalho da disciplina de Desenvolvimento Web da UFF. A ideia é simples: você escreve uma dúvida no navegador e pode continuar usando a página enquanto a IA prepara a resposta. Quando ela fica pronta, aparece abaixo da pergunta, na mesma aba.

O **Gateway**, nossa API em Spring Boot, recebe a pergunta e a coloca numa fila do RabbitMQ. O **Worker**, que roda separado da API, pega a pergunta, monta as instruções do tutor e consulta o Ollama. A resposta volta por outra fila; o Gateway a entrega ao React pelo WebSocket.

[Como rodar](#como-rodar) · [O que fizemos em cada etapa](#o-que-fizemos-em-cada-etapa) · [Como testar](#como-testar)

## Etapas do projeto

| Etapa | O que entrou no projeto | Situação |
| --- | --- | --- |
| **01** | Formulário para enviar uma pergunta e receber o ID do envio | Concluída |
| **02** | Fila no RabbitMQ para guardar as perguntas até o processamento | Concluída |
| **03** | Worker escolhe o perfil do tutor e prepara as instruções | Concluída |
| **04** | Ollama gera a resposta, que volta por uma segunda fila | Concluída |
| **05** | A resposta aparece na aba que enviou a pergunta | Concluída para uso local |

## O que fizemos em cada etapa

1. **Receber a pergunta.** Criamos o formulário em React e a rota na API. O Gateway rejeita perguntas em branco e devolve um identificador (`correlation_id`) para acompanhar cada envio.
2. **Colocar a pergunta na fila.** Em vez de chamar a IA na hora, o Gateway envia a pergunta para `perguntas.queue`. Ele só confirma o envio ao navegador depois que o RabbitMQ aceita a publicação e confirma que a mensagem teve destino. A fila e as mensagens foram configuradas para sobreviver a um reinício normal do broker.
3. **Preparar o pedido para a IA.** O Worker lê a pergunta e usa expressões do texto para identificar o assunto e o tipo de ajuda que o aluno quer. “Qual a diferença entre Java e Python?” pede uma comparação; “Me dê ideias para um projeto de física” pede sugestões. O Worker monta as instruções do tutor sem reescrever a pergunta original. Essas regras ajudam a escolher um perfil, mas não entendem toda frase: quando não há uma escolha clara, usamos o perfil padrão.
4. **Gerar a resposta.** O Worker chama o Ollama pelo Spring AI e usa a temperatura escolhida no passo anterior. Se houver uma falha temporária, ele tenta de novo, com até três tentativas no total. Depois publica em `respostas.queue` a resposta ou um aviso de erro, junto com o ID original.
5. **Mostrar a resposta na tela.** O Gateway lê a fila de respostas e manda cada resultado para a aba que fez a pergunta, usando STOMP sobre WebSocket. Se a conexão cair, a mesma aba consegue buscar a resposta pela API quando voltar. Dá para fazer mais de um envio sem perder de qual pergunta veio cada resposta.

A temperatura é um ajuste passado ao modelo: usamos valores menores para pedidos que pedem mais precisão e maiores quando faz sentido explorar ideias.

| Perfil | Exemplo | Temperatura |
| --- | --- | --- |
| Analítico | Qual a diferença entre Java e Python? | `0.0` |
| Criativo | Me dê ideias para um projeto de física | `0.8` |
| Prático | Como configurar um banco de dados? | `0.2` |
| Didático | O que é um algoritmo? | `0.4` |
| Padrão | Tenho uma dúvida sobre Python | `0.2` |

A etapa 5 usa o Gateway como ponte. O navegador não se conecta diretamente ao plugin Web STOMP do RabbitMQ, como no fluxo original do enunciado. A sessão é anônima, sem login, e fica em memória por até uma hora. Recarregar a página ou reiniciar o Gateway não restaura o histórico. Para a demonstração local, use uma única instância do Gateway.

## Como rodar

Você precisa de **JDK 25**, **Node.js** com npm (20.19+ na linha 20 ou 22.12+), **Docker** para o RabbitMQ e **Ollama** para gerar respostas. Não precisa instalar MySQL nem Maven separadamente: o repositório já traz o Maven Wrapper.

### 1. Ligar o RabbitMQ

No Windows, abra o [Docker Desktop](https://docs.docker.com/desktop/setup/install/windows-install/) e espere ele terminar de iniciar. Não é preciso entrar no Ubuntu pelo WSL para rodar os comandos. No macOS, abra o Docker Desktop ou inicie o Colima, se já o usa.

Na raiz de `des-web`, onde fica `compose.yaml`:

```shell
docker compose up -d
docker compose ps
docker compose exec rabbitmq rabbitmq-diagnostics -q ping
```

O container deve aparecer como `Up` e o último comando deve indicar `Ping succeeded`. O painel do RabbitMQ fica em [localhost:15672](http://localhost:15672), com usuário e senha `guest`. Se só quiser testar o formulário e a fila de perguntas, deixe o Worker desligado por enquanto.

### 2. Escolher o modelo da IA

Instale o [Ollama](https://ollama.com/download) e abra o aplicativo. Em um novo terminal, baixe **só um** dos modelos:

| Opção | Comando | Download | Memória sugerida |
| --- | --- | --- | --- |
| Leve | `ollama pull llama3.2:1b` | 1,3 GB | 8 GB de RAM com outros programas fechados; 16 GB dá mais folga |
| Intermediária | `ollama pull llama3.2:3b` | 2,0 GB | 16 GB de RAM, com cerca de 4–6 GB livres para o Ollama |
| Forte | `ollama pull llama3.1:8b` | 4,9 GB | 24–32 GB de RAM como ponto de partida sem GPU; uma GPU compatível com 8 GB de VRAM pode ajudar |

Esses valores de memória são estimativas para o projeto rodando junto com o Docker. O tamanho do download não é a quantidade de RAM que o modelo vai usar. Se não souber qual escolher, comece pelo **1B**. Confira se o download terminou com `ollama list`. Se o comando não conectar, abra o aplicativo; só use `ollama serve` se não houver um servidor rodando.

Tamanhos dos modelos: [Llama 3.2](https://ollama.com/library/llama3.2/tags) e [Llama 3.1 8B](https://ollama.com/library/llama3.1:8b).

### 3. Abrir os terminais da aplicação

Use um terminal para cada processo e mantenha os três abertos. Se já houver um Gateway ou Worker rodando, pare a versão antiga com `Ctrl+C` antes de gerar o novo JAR.

#### Windows (PowerShell)

Nos dois terminais Java, selecione o JDK 25. O caminho abaixo é um exemplo local: se o seu JDK estiver em outra pasta, coloque em `JAVA_HOME` o caminho da pasta que contém `bin\javac.exe`. Caso ainda não tenha o JDK, instale o [Temurin 25](https://adoptium.net/installation/).

```powershell
$env:JAVA_HOME = 'C:\Users\Gabriel Panza\AppData\Local\Programs\Java\Temurin-25\PFiles64\Eclipse Adoptium\jdk-25.0.4.7-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

**Terminal 1 — Gateway.** Cole a linha inteira em um prompt vazio. Se o clone estiver em outro lugar, ajuste o caminho em `Set-Location`.

```powershell
Set-Location -LiteralPath 'D:\Documents\GitHub\des-web\backend' -ErrorAction Stop; & './mvnw.cmd' -pl gateway -am package; if ($LASTEXITCODE -eq 0) { & "$env:JAVA_HOME/bin/java.exe" -jar './gateway/target/gateway-1.0.0-SNAPSHOT.jar' }
```

Espere aparecer `Started GatewayApplication`. A API fica na porta `8081`.

**Terminal 2 — React.** Abra o terminal na raiz do clone. O `npm.cmd ci` é necessário na primeira vez e quando as dependências mudarem.

```powershell
cd .\frontend
npm.cmd ci
npm.cmd run dev
```

**Terminal 3 — Worker.** Selecione o JDK 25 neste terminal também. Use o nome do modelo que aparece em `ollama list`; o exemplo usa o 1B.

```powershell
Set-Location -LiteralPath 'D:\Documents\GitHub\des-web\backend' -ErrorAction Stop; $env:OLLAMA_MODEL = 'llama3.2:1b'; & './mvnw.cmd' -pl worker -am package; if ($LASTEXITCODE -eq 0) { & "$env:JAVA_HOME/bin/java.exe" -jar './worker/target/worker-1.0.0-SNAPSHOT.jar' }
```

Espere aparecer `Started WorkerApplication`. Os comandos do Gateway e do Worker só abrem o JAR se o build passar.

#### macOS

Se precisar instalar Java e Node, use o [Homebrew](https://brew.sh/) com `brew install openjdk@25 node@24`. Nos terminais Java, ajuste o ambiente e confira a versão:

```bash
export JAVA_HOME="$(brew --prefix openjdk@25)/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$(brew --prefix node@24)/bin:$PATH"
java -version
```

Abra cada terminal na raiz do clone:

**Gateway:**

```bash
cd backend
./mvnw -pl gateway -am package && java -jar gateway/target/gateway-1.0.0-SNAPSHOT.jar
```

**React:**

```bash
cd frontend
npm ci
npm run dev
```

**Worker:**

```bash
cd backend
export OLLAMA_MODEL=llama3.2:1b
./mvnw -pl worker -am package && java -jar worker/target/worker-1.0.0-SNAPSHOT.jar
```

Troque o valor de `OLLAMA_MODEL` se baixou outro modelo. Se o Maven Wrapper não tiver permissão para executar, rode `chmod +x backend/mvnw` na raiz do clone.

### 4. Abrir a aplicação

Em [localhost:5173](http://localhost:5173), espere aparecer **Conectado para receber respostas**. Digite “O que é um algoritmo?” e envie. Primeiro aparece o ID do envio; a resposta vem depois, no cartão da própria pergunta. A primeira geração pode demorar porque o Ollama ainda precisa carregar o modelo.

Para parar os processos, use `Ctrl+C` nos respectivos terminais. Na raiz do clone, `docker compose stop` para o RabbitMQ sem excluir seu volume.

### Continuar uma conversa

Depois que chegar a resposta da IA, clique em **Continuar daqui** no cartão que você quer usar como referência. Por exemplo, pergunte “O que é um algoritmo?” e, depois da resposta, envie “Pode dar outro exemplo?”. Para fazer uma pergunta independente, não selecione um cartão ou clique em **Enviar sem contexto**.

O React envia só o ID da resposta escolhida. O Gateway confere se ela pertence à mesma sessão e recupera até três trocas daquela sequência. Trechos longos do histórico são encurtados para caber no contexto do modelo. O Worker passa as perguntas anteriores como falas do aluno e as respostas anteriores como falas do tutor, antes da nova pergunta. Nem uma aba diferente nem uma sessão nova acessam essa conversa.

## Como testar

- **Etapa 1 — formulário:** ligue RabbitMQ, Gateway e React, mas deixe o Worker desligado. Um envio válido mostra o ID; no HTTP, retorna `202`. Uma pergunta em branco retorna `400`. A versão original da etapa 1 não usava RabbitMQ e retornava `200`; esses testes são para a versão atual.
- **Etapa 2 — fila:** com o Worker ainda desligado, abra o painel e vá em **Queues and Streams → perguntas.queue → Get messages**. Selecione **Nack message requeue true** para ler sem retirar a mensagem. Confira se o texto e o ID são os mesmos do envio.
- **Etapa 3 — perfil:** ligue Ollama e Worker. Envie “O que é um algoritmo?” e procure `Pergunta preparada` no terminal do Worker. A linha deve mostrar o mesmo ID, perfil `DIDATICO` e temperatura `0.4`.
- **Etapa 4 — geração:** procure `Resposta publicada` no terminal do Worker, com o mesmo ID e status `OK`. Se quiser ler o JSON cru em `respostas.queue`, reinicie **só o Gateway** com `--spring.rabbitmq.listener.simple.auto-startup=false` depois do nome do JAR e envie uma nova pergunta. Assim, o Gateway não consome a fila de respostas antes de você abrir o painel. Retire o argumento para voltar à etapa 5.
- **Etapa 5 — tela:** abra duas abas e envie uma pergunta diferente em cada uma. Cada aba deve mostrar apenas as próprias respostas. Para testar a recuperação, desconecte uma aba após enviar e reconecte sem recarregar a página.

Se preferir testar sem a interface, abra [testes.http](backend/tests/http/testes.http) no VS Code com a extensão REST Client. Rode na ordem: **criar sessão → enviar pergunta → consultar resposta**. A consulta retorna `202` enquanto a resposta não está pronta e `200` quando ela chega.

Para rodar os testes automatizados, execute `.\mvnw.cmd test` no Windows ou `./mvnw test` no macOS, dentro de `backend/`. Esses testes não precisam do RabbitMQ nem do Ollama.

## Se algo der errado

| Sintoma | O que conferir |
| --- | --- |
| O PowerShell não encontra `docker` | Reabra o terminal ou ajuste o PATH com `$env:Path = "C:\Program Files\Docker\Docker\resources\bin;$env:Path"`, se o Docker estiver instalado nesse caminho. |
| O Docker menciona `docker_engine` ou `daemon` | O comando foi encontrado, mas não conseguiu falar com o motor. Abra o Docker Desktop e confira `docker info`. |
| `mvnw` não é reconhecido ou o Java procura `.jarcd` | Cole a linha inteira em um prompt vazio. O arquivo é `mvnw.cmd`, sem barra antes de `.cmd`. |
| Erro `release 25` ou `UnsupportedClassVersionError` | Confira se o terminal do serviço está usando o JDK 25. |
| A tela não conecta ou a porta `5173` está ocupada | Confira `Started GatewayApplication` e se já existe um Vite rodando. Use `localhost:5173` ou `127.0.0.1:5173`. |
| O envio retorna `503` | Confira se o RabbitMQ está ligado. Se a confirmação demorou demais, a pergunta pode já estar na fila; confira antes de reenviar. |
| Aparece **Erro no processamento** ou `model not found` | Confira se o Ollama está aberto, se o nome em `OLLAMA_MODEL` bate com o de `ollama list` e se há memória livre. |

## Onde está o código

- [`frontend/`](frontend/): formulário, conexão com a API e exibição das respostas.
- [`backend/gateway/`](backend/gateway/): recebe pedidos, publica perguntas e entrega respostas.
- [`backend/worker/`](backend/worker/): escolhe o perfil, chama a IA e publica a resposta.
- [`backend/comum/`](backend/comum/): formato das mensagens e configuração das filas.
- [`backend/tests/`](backend/tests/): testes automatizados e requisições manuais.

Para entender por que o mesmo ID passa pelo projeto inteiro, veja [Enterprise Integration Patterns — Correlation Identifier](https://www.enterpriseintegrationpatterns.com/patterns/messaging/CorrelationIdentifier.html), de Gregor Hohpe e Bobby Woolf.
