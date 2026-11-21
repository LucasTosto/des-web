# Sistema de Tutoria Assíncrono com IA

Trabalho de Desenvolvimento Web da UFF. O sistema final receberá perguntas pelo React, encaminhará cada uma pelo Gateway Spring Boot ao RabbitMQ e usará um Worker independente para consultar a IA com Spring AI. A resposta voltará ao navegador por STOMP sobre WebSocket. O Gateway não chamará a IA diretamente.

Formação dos grupos: até quatro alunos por equipe, com no máximo 16 grupos.

## Etapas

| Etapa | Entrega | Situação |
| --- | --- | --- |
| 1 | Tela de pergunta e Gateway HTTP: validar o texto e retornar um `correlation_id` | Implementada |
| 2 | Publicação imediata no RabbitMQ: configurar a exchange, `perguntas.queue` durável, mensagens persistentes e payload JSON com Jackson | Implementada |
| 3 | Worker Spring Boot sem Starter Web: consumir a fila, extrair a pergunta e seu identificador e montar o prompt de tutor | Pendente |
| 4 | Worker: consultar a IA com Spring AI, tratar falhas com intervalo e retry e publicar a resposta JSON com o identificador original em `respostas.exchange`, roteada à fila durável `respostas.queue` | Pendente |
| 5 | Retorno via Web STOMP do RabbitMQ: inscrição do React com `@stomp/stompjs` no destino da sessão correta, correlação e exibição da resposta | Pendente |

Na etapa 5, o destino de cada sessão precisa isolar as respostas no broker. Colocar todos os navegadores como consumidores concorrentes de uma única fila não garante que cada resposta chegue ao navegador que fez a pergunta. O `correlation_id` identifica uma pergunta e será preservado no processamento; o roteamento da sessão será definido nessa etapa.

## Organização da etapa 1

```text
des-web/
├── backend/
│   ├── mvnw, mvnw.cmd e .mvn/  # Maven Wrapper do Trabalho
│   ├── pom.xml
│   ├── comum/    # módulo Maven reservado para o que for compartilhado
│   ├── gateway/  # controller, service, dto, resources e testes
│   └── worker/   # apenas o POM já existente; sem execução nesta etapa
└── frontend/     # components, pages, routes, interfaces e util em src/
```

O Gateway segue a estrutura do exercício 02: o controller recebe a requisição, o DTO define os dados de entrada, o Spring valida e o service gera o identificador. O frontend segue os exercícios 04 e 06: layout, página e formulário separados, `fetch` com `async/await` e proxy do Vite. As dependências de AMQP, Spring AI e STOMP que já estavam declaradas na estrutura foram preservadas; na etapa 1 elas ainda não eram utilizadas.

`POST /perguntas` recebe `{"texto":"O que é escopo?"}`. Na etapa 1, o Gateway devolvia HTTP 200 com `{"correlation_id":"<UUID>"}` ou HTTP 400 para texto ausente, vazio ou em branco, sem publicar nada. **A partir da etapa 2, o sucesso passou a ser HTTP 202 e o RabbitMQ precisa estar rodando** (veja a próxima seção). MySQL e Ollama continuam desnecessários.

## Organização da etapa 2

```text
backend/
├── comum/    # contrato compartilhado por Gateway e Worker
│   ├── config/RabbitMQConfig  # exchange, fila, binding e conversor JSON
│   └── dto/PerguntaMensagem   # payload {"correlation_id", "texto"}
└── gateway/
    ├── service/PerguntaService          # publica e espera a confirmação do RabbitMQ
    └── exception/GlobalExceptionHandler # RabbitMQ indisponível → HTTP 503
compose.yaml  # RabbitMQ local, igual no Windows e no macOS
```

A cada `POST /perguntas` válido, o Gateway gera o `correlation_id`, monta um `PerguntaMensagem` e o publica na exchange `perguntas.exchange` (tipo direct) com a routing key `perguntas`, que a entrega à fila `perguntas.queue`. O Gateway não chama a IA.

- **Durabilidade:** exchange e fila são duráveis e a mensagem é publicada como persistente (`delivery_mode` 2), então as perguntas ainda não consumidas sobrevivem a um reinício do RabbitMQ.
- **Confirmação:** o Gateway usa *publisher confirms* e só devolve o `correlation_id` depois que o RabbitMQ confirma o recebimento. Se o broker estiver desligado ou não confirmar em 5 segundos, a resposta é HTTP 503, e o React avisa que a fila está indisponível.
- **Serialização:** `Jackson2JsonMessageConverter` com o `ObjectMapper` do Spring Boot. A mensagem trafega como `application/json`, por exemplo `{"correlation_id":"<UUID>","texto":"O que é escopo?"}`. O `correlation_id` também vai na propriedade AMQP `correlation_id`.
- **Módulo comum:** a declaração da fila e o formato da mensagem ficam em `comum`, porque o Worker (etapa 3) precisa declará-los de forma idêntica. O RabbitMQ recusa a mesma fila declarada com argumentos diferentes. O Gateway importa a configuração com `@Import(RabbitMQConfig.class)`.
- **Status HTTP:** a resposta de sucesso passou de 200 para **202 Accepted**. A pergunta foi aceita para processamento, mas ainda não foi respondida; a resposta da IA chegará de forma assíncrona.

Host, porta, usuário e senha do RabbitMQ vêm de `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME` e `RABBITMQ_PASSWORD`, com padrão `localhost`, `5672`, `guest` e `guest`. Com o `compose.yaml`, não é preciso definir nenhuma delas. Os testes do Gateway simulam o RabbitMQ e não precisam dele ligado. As requisições de exemplo estão em [testes.http](backend/gateway/src/main/resources/testes.http), que pode ser executado no VS Code com a extensão REST Client.

Para conferir a publicação, abra o painel em [localhost:15672](http://localhost:15672) (usuário e senha `guest`), entre em **Queues and Streams → perguntas.queue** e use **Get messages**. Como ainda não há Worker, as perguntas se acumulam na fila; elas serão consumidas a partir da etapa 3.

## Organização da etapa 3

ainda a fazer

## Organização da etapa 4

ainda a fazer

## Organização da etapa 5

ainda a fazer

## Executar no Windows

Pré-requisitos: JDK 25, npm e Node.js 20.19 ou superior na linha 20.x, ou Node.js 22.12 ou superior, e Docker Desktop para o RabbitMQ. A ordem de execução é: **RabbitMQ, Gateway e React**. Sem o RabbitMQ, o Gateway inicia normalmente, mas cada envio de pergunta responde HTTP 503. Abra os terminais **na raiz do clone de `des-web`**, onde estão `README.md`, `compose.yaml`, `backend/` e `frontend/`. Não é necessário clonar os exercícios nem instalar o Maven separadamente: o Maven Wrapper do projeto baixa o Maven no primeiro uso se ele ainda não estiver em cache.

**RabbitMQ.** Se o Docker Desktop ainda não estiver instalado, em um PowerShell como administrador rode `winget install --id Docker.DockerDesktop --exact`, ou use o [instalador oficial](https://docs.docker.com/desktop/setup/install/windows-install/). O instalador pode pedir para habilitar o WSL 2 e reiniciar o Windows. Abra o Docker Desktop, espere ele indicar que está em execução e, na raiz de `des-web`:

```powershell
docker compose up -d
```

O primeiro uso baixa a imagem do RabbitMQ. O comando volta ao prompt e o RabbitMQ continua rodando em segundo plano; o painel fica em [localhost:15672](http://localhost:15672) (usuário e senha `guest`). Para parar, use `docker compose stop`; para iniciar de novo, `docker compose start` (ou `docker compose up -d`). As filas e as mensagens ficam em um volume do Docker e não se perdem ao parar ou recriar o container. Se preferir instalar o RabbitMQ diretamente no Windows (Erlang + instalador), o projeto funciona igual, desde que ele responda em `localhost:5672` com o usuário `guest` e o plugin `rabbitmq_web_stomp` esteja habilitado para a etapa 5.

No terminal do Gateway, rode `java -version`. Se aparecer Java 8 ou outra versão que não seja 25, ou se `JAVA_HOME` apontar para outro JDK, ajuste as variáveis abaixo. O caminho é um exemplo da instalação do Gabriel: em outro computador, use o caminho do seu JDK 25 (a pasta que contém `bin\javac.exe`). As variáveis são alteradas apenas neste terminal:

```powershell
$env:JAVA_HOME = 'C:\Users\Gabriel Panza\AppData\Local\Programs\Java\Temurin-25\PFiles64\Eclipse Adoptium\jdk-25.0.4.7-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

Confirme que `java -version` mostra **25** antes de continuar.


**Terminal 1 — Gateway** (porta 8081, separada das APIs dos exercícios):

```powershell
cd .\backend
.\mvnw.cmd -pl gateway -am package
java -jar .\gateway\target\gateway-1.0.0-SNAPSHOT.jar
```

`-pl gateway -am` seleciona o Gateway e seu módulo comum, sem compilar o Worker. Execute a linha `java -jar` somente após o Maven terminar com sucesso.

**Terminal 2 — React:**

```powershell
cd .\frontend
npm.cmd ci
npm.cmd run dev
```

Abra o endereço que o Vite imprimir, normalmente [localhost:5173](http://localhost:5173). Escreva uma pergunta e clique em **Enviar pergunta**. A confirmação deve mostrar o identificador, e a pergunta deve aparecer em `perguntas.queue` no painel do RabbitMQ. O Vite encaminha `/api/perguntas` para `http://localhost:8081/perguntas`, como no proxy usado em sala. Use `Ctrl+C` para parar cada servidor. O RabbitMQ continua rodando até `docker compose stop`.

### Problemas comuns

| Sintoma | O que conferir |
| --- | --- |
| `docker` não é reconhecido | Instale o Docker Desktop e reabra o PowerShell |
| `error during connect` ou `Cannot connect to the Docker daemon` | Abra o Docker Desktop e espere ele indicar que está em execução |
| Erro do `docker compose` citando `configs` ou `content` | Docker Desktop desatualizado: atualize-o, pois o `compose.yaml` usa `configs` com conteúdo inline |
| `port is already allocated` na porta 5672 ou 15672 | Outro RabbitMQ já está rodando (instalação nativa ou outro container); pare-o ou use apenas ele |
| A tela mostra "A fila de perguntas está indisponível" (HTTP 503) | O RabbitMQ não está rodando: `docker compose up -d` e envie de novo; não é preciso reiniciar o Gateway |
| A tela mostra "Verifique se o Gateway está rodando" | O Terminal 1 foi fechado ou o Maven falhou; confira se apareceu `Started GatewayApplication` |
| Erro de compilação `release 25` ou `JAVA_HOME` inválido | Confira `java -version` e o ajuste de `JAVA_HOME` acima |
| `npm.ps1 cannot be loaded` | Use `npm.cmd`, como nos exemplos |

## Executar no macOS

Pré-requisitos iguais aos do Windows: JDK 25, Node.js e npm. Com o [Homebrew](https://brew.sh), instale e coloque no PATH (as fórmulas abaixo não substituem outro Java ou Node que já esteja configurado):

```bash
brew install openjdk@25 node@24
echo 'export JAVA_HOME="/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home"' >> ~/.zprofile
echo 'export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"' >> ~/.zprofile
```

Feche e reabra o terminal e confirme que `java -version` mostra **25**. Os comandos partem da raiz de `des-web`.

**RabbitMQ.** Com Docker Desktop instalado, ou com o Colima (`brew install colima docker docker-compose` e depois `colima start`), rode na raiz de `des-web`:

```bash
docker compose up -d
```

O painel, os comandos para parar e iniciar e os problemas comuns são os mesmos da seção do Windows. Se o Colima foi usado, rode `colima start` de novo depois de reiniciar o Mac.

**Terminal 1 — Gateway** (porta 8081):

```bash
cd backend
./mvnw -pl gateway -am package
java -jar gateway/target/gateway-1.0.0-SNAPSHOT.jar
```

**Terminal 2 — React:**

```bash
cd frontend
npm ci
npm run dev
```

O `mvnw` é versionado com permissão de execução e fim de linha LF (veja `.gitattributes`). Se aparecer `permission denied`, rode `chmod +x backend/mvnw`.
