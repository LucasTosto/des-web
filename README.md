# Sistema de Tutoria Assíncrono com IA

Trabalho de Desenvolvimento Web da UFF. O sistema final receberá perguntas pelo React, encaminhará cada uma pelo Gateway Spring Boot ao RabbitMQ e usará um Worker independente para consultar a IA com Spring AI. Pelo plano atual, a resposta voltará ao navegador por STOMP sobre WebSocket; o SSE é uma alternativa aceita pelo professor (veja [Decisões pendentes](#decisões-pendentes-antes-da-etapa-5)). O Gateway não chamará a IA diretamente.

Formação dos grupos: até quatro alunos por equipe, com no máximo 16 grupos.

## Etapas

| Etapa | Entrega | Situação |
| --- | --- | --- |
| 1 | Tela de pergunta e Gateway HTTP: validar o texto e retornar um `correlation_id` | Implementada |
| 2 | Publicação imediata no RabbitMQ: configurar a exchange, `perguntas.queue` durável, mensagens persistentes e payload JSON com Jackson | Implementada |
| 3 | Worker sem Starter Web: consumir a fila e preparar o prompt com tema, perfil e temperatura | Implementada sem chamada à IA |
| 4 | Worker: consultar a IA com Spring AI, tratar falhas com intervalo e retry e publicar a resposta JSON com o identificador original em `respostas.exchange`, roteada à fila durável `respostas.queue` | Implementada |
| 5 | Retorno via Web STOMP do RabbitMQ: inscrição do React com `@stomp/stompjs` no destino da sessão correta, correlação e exibição da resposta | Pendente |

Na etapa 5, o destino de cada sessão precisa isolar as respostas no broker. Colocar todos os navegadores como consumidores concorrentes de uma única fila não garante que cada resposta chegue ao navegador que fez a pergunta. O `correlation_id` identifica uma pergunta e será preservado no processamento; o roteamento da sessão será definido nessa etapa.

### Decisões pendentes antes da etapa 5

As etapas 1 a 4 não dependem destes dois pontos, mas ambos precisam ser decididos **antes de começar a etapa 5**, porque mudam como a resposta chega ao usuário certo.

**1. Autenticação com JWT: recomendada, ainda não implementada.** A especificação não lista o JWT entre os requisitos obrigatórios, mas o professor o recomenda fortemente: a chamada à IA tem custo, e cada resposta pertence a um único usuário. Sem autenticação, qualquer pessoa pode enviar perguntas ou tentar ler a resposta de outra. Quando for implementado:

- o Gateway passa a exigir `Authorization: Bearer <token>` em `POST /perguntas`. `SecurityConfig`, `JwtService`, `JwtAuthenticationFilter` e o login do exercício 20 do professor (`backend/Exercicio20-apirestful-com-spring-security`) podem ser reaproveitados;
- `PerguntaMensagem`, no módulo `comum`, ganha o identificador do usuário extraído do token, e o Worker o devolve junto com a resposta, para que ela seja roteada a esse usuário;
- o canal de retorno da etapa 5 só entrega respostas ao usuário dono do token.

**2. Caminho de retorno: STOMP (plano atual) ou SSE (alternativa aceita).** A especificação permite os dois:

| | STOMP sobre WebSocket (itens 2.1 a 2.4 da especificação) | SSE pelo Gateway (itens 3.1 a 3.4 da especificação) |
| --- | --- | --- |
| Quem entrega a resposta ao React | O RabbitMQ, pelo plugin Web STOMP (porta 15674) | O Gateway: um `@RabbitListener` em `respostas.queue` envia pelo `SseEmitter` do usuário |
| Biblioteca no React | `@stomp/stompjs` (já declarada) | `@microsoft/fetch-event-source` (permite enviar o JWT no header) |
| Isolamento por usuário | Feito no broker: uma fila ou routing key exclusiva por usuário | Feito no Gateway: mapa `userId → SseEmitter` |
| JWT | Validado pelo RabbitMQ (plugin OAuth 2) ou por um intermediário; mais trabalhoso | Validado pelo Spring Security, como no exercício 20 |

O professor descreve o SSE como o caminho que "parece ser mais simples", e ele combina diretamente com o JWT. Se o grupo escolher o SSE, é preciso atualizar a linha da etapa 5 na tabela acima e esta seção. Nesse caso, o React passa a usar `fetch-event-source` no lugar de `@stomp/stompjs`, e o plugin `rabbitmq_web_stomp` deixa de ser necessário.

## Organização da etapa 1

```text
des-web/
├── backend/
│   ├── mvnw, mvnw.cmd e .mvn/  # Maven Wrapper do Trabalho
│   ├── pom.xml
│   ├── comum/    # módulo Maven reservado para o que for compartilhado
│   ├── gateway/  # controller, service, dto e resources
│   ├── worker/   # apenas o POM já existente; sem execução nesta etapa
│   └── tests/    # testes centralizados, separados por módulo
└── frontend/     # components, pages, routes, interfaces e util em src/
```

O Gateway segue a estrutura do exercício 02: o controller recebe a requisição, o DTO define os dados de entrada, o Spring valida e o service gera o identificador. O frontend segue os exercícios 04 e 06: layout, página e formulário separados, `fetch` com `async/await` e proxy do Vite. As dependências de AMQP, Spring AI e STOMP que já estavam declaradas na estrutura foram preservadas; na etapa 1 elas ainda não eram utilizadas.

`POST /perguntas` recebe `{"texto":"O que é escopo?"}`. Na etapa 1, o Gateway devolvia HTTP 200 com `{"correlation_id":"<UUID>"}` ou HTTP 400 para texto ausente, vazio ou em branco, sem publicar nada. **A partir da etapa 2, o sucesso passou a ser HTTP 202 e o RabbitMQ precisa estar rodando** (veja a próxima seção). MySQL não é necessário. Para testar apenas a etapa 2, também não é preciso ligar o Ollama.

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
- **Confirmação:** o Gateway exige confirmação positiva e verifica se o RabbitMQ devolveu a mensagem por falta de rota. Retorna HTTP 503 em caso de devolução, rejeição, indisponibilidade ou se não receber a confirmação em até 5 segundos após publicar. Um timeout é inconclusivo: a mensagem pode ter entrado na fila. Reenviar não é uma operação sem duplicatas.
- **Serialização:** `Jackson2JsonMessageConverter` com o `ObjectMapper` do Spring Boot. A mensagem trafega como `application/json`, por exemplo `{"correlation_id":"<UUID>","texto":"O que é escopo?"}`. O `correlation_id` também vai na propriedade AMQP `correlation_id`.
- **Módulo comum:** a declaração da fila e o formato da mensagem ficam em `comum`, porque o Worker (etapa 3) precisa declará-los de forma idêntica. O RabbitMQ recusa a mesma fila declarada com argumentos diferentes. O Gateway importa a configuração com `@Import(RabbitMQConfig.class)`.
- **Status HTTP:** a resposta de sucesso passou de 200 para **202 Accepted**. A pergunta foi aceita para processamento, mas ainda não foi respondida; a resposta da IA chegará de forma assíncrona.

Host, porta, usuário e senha do RabbitMQ vêm de `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME` e `RABBITMQ_PASSWORD`, com padrão `localhost`, `5672`, `guest` e `guest`. Com o `compose.yaml`, não é preciso definir nenhuma delas. Os testes do Gateway simulam o RabbitMQ e não precisam dele ligado. As requisições de exemplo estão em [testes.http](backend/tests/http/testes.http), que pode ser executado no VS Code com a extensão REST Client.

Para conferir a publicação, abra o painel em [localhost:15672](http://localhost:15672) (usuário e senha `guest`), entre em **Queues and Streams → perguntas.queue** e use **Get messages**. Sem ligar o Worker, as perguntas se acumulam na fila. Use a leitura com reenfileiramento se quiser inspecionar uma mensagem sem removê-la.

## Organização da etapa 3

O Worker é outro processo Spring Boot, sem servidor HTTP. Ele importa a mesma configuração Jackson/RabbitMQ do módulo `comum` e usa `PerguntaListener` para consumir `perguntas.queue`. Em seguida, `EnriquecedorDePrompt` analisa termos e expressões (sem diferenciar acentos ou maiúsculas) e escolhe o tema e o perfil separadamente.

| Perfil | Exemplo de pedido | Temperatura preparada |
| --- | --- | --- |
| Analítico | Compare dois algoritmos | 0,0 |
| Criativo | Me dê ideias para um projeto de física | 0,8 |
| Prático | Como configurar um banco de dados? | 0,2 |
| Didático | O que é um algoritmo? | 0,4 |
| Padrão | Escopo? | 0,2 |

O perfil é escolhido por expressões, sinônimos e variações de escrita: «qual a diferença» equivale a um pedido de comparação e «me explica» sinaliza um pedido didático. Expressões específicas têm mais peso que verbos isolados; sem sinais suficientes ou em caso de empate, aplica-se o perfil padrão. Se o tema não ficar claro, o template deixa o assunto aberto. Isso é uma heurística, não uma interpretação semântica do pedido. A instrução do tutor e a pergunta original ficam separadas; o `correlation_id` é preservado. A temperatura preparada é aplicada na chamada ao Ollama pelo Spring AI na etapa 4.

Inicie o Worker seguindo os comandos de [Windows](#4-ligar-o-gateway-o-react-e-o-worker) ou [macOS](#executar-no-macos).

Envie uma pergunta com o RabbitMQ e o Gateway ligados. O Worker registra o ID, tema, perfil e temperatura no terminal, sem registrar a pergunta. Na versão atual, ele também executa a etapa 4. Deixe o Worker desligado se quiser inspecionar `perguntas.queue` na etapa 2.

## Organização da etapa 4

O Worker envia a instrução do tutor como mensagem de sistema e a pergunta original como mensagem de usuário ao Ollama via Spring AI, com a temperatura do perfil. A chamada pode bloquear o Worker, mas não o Gateway. Falhas transitórias (incluindo HTTP 429, falha de conexão e respostas vazias) têm até três tentativas, com pausas de 2 e 4 segundos. Se a IA falhar de forma definitiva, o Worker publica uma resposta com status `ERRO`, em vez de deixar a pergunta sem retorno no broker.

A resposta é JSON persistente com `correlation_id`, `texto` e `status` (`OK` ou `ERRO`), publicada em `respostas.exchange` com chave `respostas`, para a fila durável `respostas.queue`. O Worker espera a confirmação e verifica a devolução por falta de rota antes de confirmar o consumo da pergunta. Se a publicação não for confirmada, ele espera brevemente e manda reenfileirar a pergunta. Corrija a falha no broker ou pare o Worker se o problema persistir. A entrega não é exatamente uma vez: se o broker gravar a resposta e a confirmação se perder, pode haver duas respostas com o mesmo ID.

Para rodar e conferir a etapa 4, siga o [guia de execução](#executar-no-windows) e os [testes manuais](#testes-manuais-das-etapas-1-a-4).

## Organização da etapa 5

ainda a fazer

## Executar os testes

Todos os arquivos de teste ficam em [backend/tests/](backend/tests/):

- `comum/`: configuração RabbitMQ e serialização JSON.
- `gateway/`: validação HTTP e publicação com o RabbitMQ simulado.
- `worker/`: classificação, preparação do prompt, chamada simulada à IA e confirmação da publicação.
- `http/`: requisições manuais; execute [testes.http](backend/tests/http/testes.http) com o Gateway ligado. Para conferir o HTTP 202, ligue também o RabbitMQ.

Com o JDK 25, dentro de `backend/`, rode `.\mvnw.cmd test` no Windows ou `./mvnw test` no macOS/Linux. Os testes automatizados não precisam do RabbitMQ nem do Ollama. Para rodar apenas os testes do Worker e de suas dependências, acrescente `-pl worker -am`.

### Testes manuais das etapas 1 a 4

Use o [testes.http](backend/tests/http/testes.http) com a extensão REST Client ou envie as perguntas pela tela do React. O React é opcional para testar por HTTP, mas é necessário para conferir a tela da etapa 1.

**Etapa 1 — formulário e Gateway.** Na versão atual, ligue RabbitMQ, Gateway e React, mas deixe o Worker parado. Abra a URL do Vite, digite “O que é um algoritmo?” e clique em **Enviar pergunta**. A tela deve mostrar a confirmação e um `correlation_id`. Pelo `testes.http`, o envio válido deve retornar `202 Accepted`; o envio em branco deve retornar `400` sem ID. Na implementação original da etapa 1, ainda não havia RabbitMQ e o sucesso era `200`; no código atual, a etapa 2 mudou esse contrato.

**Etapa 2 — envio e persistência.** Ligue RabbitMQ e Gateway, mas deixe o Worker parado. Envie uma pergunta válida e confira o `202 Accepted` e o `correlation_id` retornado. No painel do RabbitMQ, abra **Queues and Streams → perguntas.queue**. Confira que **Ready** aumentou e, em **Get messages**, escolha o modo de leitura que reenfileira a mensagem (`requeue=true`); o JSON deve conter o mesmo ID e o texto enviado. A requisição em branco deve retornar `400` sem acrescentar mensagem.

Se quiser comprovar a persistência, ainda com o Worker parado, rode `docker compose restart rabbitmq` na raiz, aguarde o ping e confira se a pergunta continua na fila. Se quiser conferir a falha de conexão, pare temporariamente o RabbitMQ com `docker compose stop rabbitmq`, envie uma pergunta e espere `503`. Depois, suba-o novamente com `docker compose up -d`.

**Etapa 3 — consumo e escolha do perfil.** Mantenha RabbitMQ e Gateway ligados. No código atual, inicie também o Ollama com um modelo instalado antes de rodar o Worker, para que a execução da etapa 4 não atrase os testes seguintes. Inicie o Worker em outro terminal e envie os exemplos abaixo. Ele pode consumir as perguntas que ficaram na fila da etapa 2 primeiro; use o `correlation_id` da requisição para achar a linha certa no terminal do Worker.

| Sugestões | Perfil e temperatura no log |
| --- | --- |
| Qual a diferença entre Java e Python? | `ANALITICO`, `0.0` |
| Me dê ideias para um projeto de física | `CRIATIVO`, `0.8` |
| Como eu consigo configurar o React? | `PRATICO`, `0.2` |
| O que é um algoritmo? | `DIDATICO`, `0.4` |
| Tenho uma dúvida sobre Python | `PADRAO`, `0.2` |

Com o Worker ativo, a mensagem pode sair da fila antes de aparecer no painel. O que confirma o teste é a linha `Pergunta preparada` com o mesmo ID, tema, perfil e temperatura. Esse teste verifica a saída da etapa 3. Na versão atual, o Worker também executa a etapa 4: a resposta da IA ou de erro fica em `respostas.queue`, não na interface.

**Etapa 4 — resposta da IA.** Com Ollama, Worker, Gateway e RabbitMQ ativos, envie “O que é um algoritmo?” pela interface ou pelo `testes.http`. Anote o ID devolvido pelo Gateway e espere `Resposta publicada` no terminal do Worker. Em [localhost:15672](http://localhost:15672), entre com `guest` / `guest` e vá em **Queues and Streams → respostas.queue → Get messages**. Escolha **Nack message requeue true** e procure o JSON com o mesmo `correlation_id`. O status esperado é `OK` e o campo `texto` deve conter a resposta. Se houver itens antigos, peça mais de uma mensagem e compare os IDs.

Se o Ollama estiver indisponível ou o modelo selecionado não existir, o envio ainda pode retornar `202`, mas o Worker deve publicar `ERRO` na fila. Já um `503` no envio indica que o Gateway não confirmou a publicação da pergunta. A primeira geração pode demorar para carregar o modelo. A resposta só vai aparecer no React na etapa 5.

## Executar no Windows

Rode os comandos no **PowerShell do Windows**, a partir da raiz do clone de `des-web` (a pasta que contém `compose.yaml`). A ordem é **RabbitMQ → Gateway → React → Ollama → Worker**. Use os três primeiros para testar as etapas 1 e 2. Só ligue o Worker depois de conferir `perguntas.queue`; na versão atual, ele consome as perguntas e já chama a IA. O React é opcional quando você só quer testar por HTTP. Não é preciso instalar MySQL nem Maven separadamente; o projeto já tem Maven Wrapper.

### 1. Instalar o que estiver faltando

- **Java:** instale o [Temurin JDK 25](https://adoptium.net/installation/). Use o **JDK**, não só o JRE. Confira com `java -version` e `javac -version`.
- **React:** instale [Node.js e npm](https://nodejs.org/en/download) se for usar a interface: Node 20.19+ na linha 20.x ou 22.12+. Confira com `node -v` e `npm.cmd -v`.
- **RabbitMQ:** instale o [Docker Desktop para Windows](https://docs.docker.com/desktop/setup/install/windows-install/) se ainda não tiver. Pelo WinGet, a alternativa é `winget install --id Docker.DockerDesktop --exact`. Use o backend WSL 2 e siga as instruções do instalador se ele pedir para habilitar o recurso ou reiniciar. Ter o Ubuntu no WSL não significa que o Docker esteja instalado. Não é preciso abrir o Ubuntu nem instalar Docker dentro dele para usar este projeto pelo PowerShell.

### 2. Ligar o RabbitMQ

Abra o **Docker Desktop** pelo menu Iniciar e espere o motor iniciar. Abra um PowerShell novo; se usar o terminal integrado e ele não reconhecer os comandos após a instalação, feche e reabra o VS Code inteiro. Na raiz de `des-web`:

```powershell
docker compose version
docker info --format '{{.ServerVersion}}'
docker compose up -d
docker compose ps
docker compose exec rabbitmq rabbitmq-diagnostics -q ping
```

Rode `up -d` depois que `docker info` mostrar a versão do servidor. No primeiro uso o Compose baixa a imagem. A saída de `ps` deve mostrar `tutoria-rabbitmq` como `Up`; se o ping ainda falhar, espere alguns segundos e repita. O painel fica em [localhost:15672](http://localhost:15672), com usuário e senha `guest`, e a aplicação se conecta ao RabbitMQ na porta `5672`.

Se `docker` ainda não for reconhecido, mas o Docker Desktop já estiver instalado em `C:\Program Files\Docker\Docker`, rode no **mesmo terminal**:

```powershell
$env:Path = "C:\Program Files\Docker\Docker\resources\bin;$env:Path"
docker compose version
```

Esse ajuste vale apenas para a sessão atual. Se a instalação estiver em outro lugar, aponte para a pasta que contém `docker.exe`. Se `compose version` funcionar, mas `docker info` falhar com `npipe`, `docker_engine` ou `daemon`, o caminho está correto: confira se o motor do Docker Desktop terminou de iniciar e tente `docker info` de novo.

### 3. Instalar o Ollama e escolher um modelo

Para rodar o Worker da etapa 4, instale o [Ollama para Windows](https://docs.ollama.com/windows), abra-o pelo menu Iniciar e abra um novo PowerShell. Baixe **apenas um** dos modelos:

| Opção | Comando para baixar | Download | Configuração sugerida para o projeto completo |
| --- | --- | --- | --- |
| Leve — para notebook de entrada e testes do fluxo | `ollama pull llama3.2:1b` | 1,3 GB | CPU de 4 núcleos, 8 GB de RAM com outros apps fechados; 16 GB dá mais folga. Reserve ~2–3 GB de RAM livre para o Ollama. |
| Intermediária — padrão do projeto | `ollama pull llama3.2:3b` | 2,0 GB | CPU de 4 núcleos ou mais, 16 GB de RAM. Reserve ~4–6 GB de RAM livre. |
| Forte — para perguntas mais complexas | `ollama pull llama3.1:8b` | 4,9 GB | CPU com 6 núcleos e 24–32 GB de RAM como ponto de partida se rodar sem GPU; reserve ~8–12 GB de RAM livre. Uma GPU compatível com 8 GB de VRAM ou mais pode acelerar contextos curtos; mantenha folga na RAM do sistema para o restante da aplicação. |

Os valores de RAM/VRAM são **estimativas de partida**, não requisitos oficiais nem garantia de velocidade. Considere o que sobrou **depois de ligar Windows, Docker, Gateway e Worker**. O tamanho do download não inclui o instalador nem o uso de memória em execução. Contextos maiores e outros aplicativos aumentam o consumo. Consulte as fichas de [Llama 3.2](https://ollama.com/library/llama3.2/tags), [Llama 3.1 8B](https://ollama.com/library/llama3.1:8b) e o [guia de tamanho de contexto](https://docs.ollama.com/context-length).

Depois de baixar:

```powershell
ollama list
Invoke-RestMethod 'http://localhost:11434/api/tags'
```

Confira se o modelo aparece na lista. Se a API não responder, abra o aplicativo. Use `ollama serve` em outro terminal apenas se já não houver um servidor na porta `11434`. Se o CLI não for reconhecido depois da instalação, reabra também o VS Code, caso esteja usando o terminal integrado. Para mover o armazenamento dos modelos para outro disco, veja `OLLAMA_MODELS` no [guia do Ollama para Windows](https://docs.ollama.com/windows#changing-model-location).

### 4. Ligar o Gateway, o React e o Worker

Nos terminais do Gateway e do Worker, rode `java -version`. Se aparecer Java 8 ou outra versão que não seja 25, ou se `JAVA_HOME` apontar para outro JDK, ajuste as variáveis abaixo. O caminho é um exemplo da instalação do Gabriel: em outro computador, use o caminho do seu JDK 25 (a pasta que contém `bin\javac.exe`). As variáveis são alteradas apenas neste terminal:

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

Abra o endereço que o Vite imprimir, normalmente [localhost:5173](http://localhost:5173). Escreva uma pergunta e clique em **Enviar pergunta**. A confirmação deve mostrar o identificador. Para ver a mensagem na fila, faça o teste manual da etapa 2 com o Worker desligado. O Vite encaminha `/api/perguntas` para `http://localhost:8081/perguntas`, como no proxy usado em sala. Use `Ctrl+C` para parar cada servidor. O RabbitMQ continua rodando em segundo plano depois que você fecha o terminal. Para pará-lo sem excluir o volume de dados, rode `docker compose stop` na raiz do clone. Evite `docker compose down -v` se quiser preservar as mensagens. Para voltar, abra o Docker Desktop e rode `docker compose up -d` novamente.

**Terminal 3 — Worker:** na raiz do clone, ajuste o JDK 25 neste terminal também. Ligue o Ollama antes e escolha **o mesmo nome que você baixou**:

```powershell
cd .\backend
$env:OLLAMA_MODEL = 'llama3.2:latest' # 'llama3.2:1b' ou 'llama3.2:3b' ou 'llama3.1:8b'
.\mvnw.cmd -pl worker -am package
java -jar .\worker\target\worker-1.0.0-SNAPSHOT.jar
```

Rode o JAR após `BUILD SUCCESS` e espere `Started WorkerApplication`. Sem `OLLAMA_MODEL`, o Worker usa `llama3.2` (3B). Para trocar de modelo, pare o Worker, ajuste a variável e rode o JAR novamente; não precisa recompilar só por isso. Para um serviço em outra máquina, configure `OLLAMA_BASE_URL`; para ajustar o tempo de leitura, use `OLLAMA_READ_TIMEOUT` (padrão `90s`). Depois, siga os [testes manuais](#testes-manuais-das-etapas-1-a-4).

### Problemas comuns

| Sintoma | O que conferir |
| --- | --- |
| `docker` não é reconhecido | Confira a instalação e reabra o terminal, ou ajuste o `PATH` da sessão como descrito acima |
| `npipe`, `docker_engine` ou `Cannot connect to the Docker daemon` | Abra o Docker Desktop e espere `docker info` mostrar a versão do servidor |
| Erro do `docker compose` citando `configs` ou `content` | Docker Desktop desatualizado: atualize-o, pois o `compose.yaml` usa `configs` com conteúdo inline |
| `port is already allocated` na porta 5672 ou 15672 | Outro RabbitMQ já está rodando (instalação nativa ou outro container); pare-o ou use apenas ele |
| A tela mostra "Não foi possível confirmar o envio" (HTTP 503) | Confira se o RabbitMQ está rodando e se a fila está vinculada à exchange. Em caso de timeout, confira a fila antes de reenviar |
| A tela mostra "Verifique se o Gateway está rodando" | O Terminal 1 foi fechado ou o Maven falhou; confira se apareceu `Started GatewayApplication` |
| `release 25`, `JAVA_HOME` inválido ou `UnsupportedClassVersionError` | Confira `java -version` no mesmo terminal antes de rodar o Maven ou o JAR |
| `npm.ps1 cannot be loaded` | Use `npm.cmd`, como nos exemplos |
| `ERRO` em `respostas.queue` ou `model not found` | Confira `ollama list` e `OLLAMA_MODEL` no terminal do Worker |
| Erro de memória ou geração muito lenta | Feche aplicativos pesados, use o modelo leve e reinicie o Worker com a variável correta |

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

**Terminal 3 — Worker:** instale o [Ollama para macOS](https://ollama.com/download/mac), abra o aplicativo e faça `ollama pull` com uma das [três opções](#3-instalar-o-ollama-e-escolher-um-modelo). Em outro terminal, na raiz do clone:

```bash
cd backend
export OLLAMA_MODEL=llama3.2:1b # ou llama3.2:3b ou llama3.1:8b
./mvnw -pl worker -am package
java -jar worker/target/worker-1.0.0-SNAPSHOT.jar
```

Quando o Worker estiver ativo, siga os [mesmos testes manuais](#testes-manuais-das-etapas-1-a-4).

O `mvnw` é versionado com permissão de execução e fim de linha LF (veja `.gitattributes`). Se aparecer `permission denied`, rode `chmod +x backend/mvnw`.
