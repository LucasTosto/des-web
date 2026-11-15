# Sistema de Tutoria Assíncrono com IA

Trabalho de Desenvolvimento Web da UFF. O sistema final receberá perguntas pelo React, encaminhará cada uma pelo Gateway Spring Boot ao RabbitMQ e usará um Worker independente para consultar a IA com Spring AI. A resposta voltará ao navegador por STOMP sobre WebSocket. O Gateway não chamará a IA diretamente.

Formação dos grupos: até quatro alunos por equipe, com no máximo 16 grupos.

## Etapas

| Etapa | Entrega | Situação |
| --- | --- | --- |
| 1 | Tela de pergunta e Gateway HTTP: validar o texto e retornar um `correlation_id` | Implementada |
| 2 | Publicação imediata no RabbitMQ: configurar a exchange, `perguntas.queue` durável, mensagens persistentes e payload JSON com Jackson | Pendente |
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

O Gateway segue a estrutura do exercício 02: o controller recebe a requisição, o DTO define os dados de entrada, o Spring valida e o service gera o identificador. O frontend segue os exercícios 04 e 06: layout, página e formulário separados, `fetch` com `async/await` e proxy do Vite. As dependências de AMQP, Spring AI e STOMP que já estavam declaradas na estrutura foram preservadas, mas ainda não são utilizadas no fluxo implementado.

`POST /perguntas` recebe `{"texto":"O que é escopo?"}`. O Gateway devolve HTTP 200 com `{"correlation_id":"<UUID>"}` ou HTTP 400 para texto ausente, vazio ou em branco. Nesta etapa, HTTP 200 confirma apenas o recebimento e a validação: **não há armazenamento, publicação na fila, Worker em execução ou resposta da IA**. Não é necessário iniciar MySQL, RabbitMQ ou Ollama.

## Organização da etapa 2

ainda a fazer

## Organização da etapa 3

ainda a fazer

## Organização da etapa 4

ainda a fazer

## Organização da etapa 5

ainda a fazer

## Executar no Windows

Pré-requisitos: JDK 25, npm e Node.js 20.19 ou superior na linha 20.x, ou Node.js 22.12 ou superior. Abra dois terminais **na raiz do clone de `des-web`**, onde estão `README.md`, `backend/` e `frontend/`. Não é necessário clonar os exercícios nem instalar o Maven separadamente: o Maven Wrapper do projeto baixa o Maven no primeiro uso se ele ainda não estiver em cache.

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

Abra o endereço que o Vite imprimir, normalmente [localhost:5173](http://localhost:5173). Escreva uma pergunta e clique em **Enviar pergunta**. A confirmação deve mostrar o identificador. O Vite encaminha `/api/perguntas` para `http://localhost:8081/perguntas`, como no proxy usado em sala. Use `Ctrl+C` para parar cada servidor.