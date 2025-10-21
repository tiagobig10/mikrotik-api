# Cliente API MikroTik (Protocolo Binário)

Este projeto é uma implementação de um cliente Java para interagir com a API binária nativa do MikroTik RouterOS (não a API REST/HTTP). Ele utiliza comunicação baseada em sockets para enviar comandos e receber respostas codificadas no formato de comprimento de palavra variável do MikroTik.

## Estrutura do Projeto

O componente central desta integração é a classe `ApiMkImpl`.

### Classe Principal: `ApiMkImpl`

Esta classe gerencia o ciclo de vida da conexão, incluindo autenticação, execução de comandos e o fechamento da conexão.

| Método | Descrição |
| :--- | :--- |
| `login(ip, login, password, port)` | Conecta-se ao MikroTik via socket e executa o comando `/login` para autenticação. |
| `execute(Command cmd)` | Envia um comando formatado para o RouterOS e processa a resposta. |
| `closed()` | Fecha o socket e libera os recursos de I/O. |
| `setTimeOut(timeOut)` | Define o tempo limite de leitura e conexão do socket em milissegundos. |

### Protocolo de Comunicação (Mecanismos Privados)

Os métodos privados lidam com a complexidade do protocolo de comunicação binária:

| Método | Função |
| :--- | :--- |
| `write(Command cmd, OutputStream out)` | Serializa o objeto `Command` no formato de lista de "palavras" binárias, terminadas por um byte nulo. |
| `encode(String word, OutputStream out)` | Codifica uma `String` anexando seu cabeçalho de **comprimento variável** (1 a 5 bytes) antes de enviar os bytes UTF-8 da palavra. |
| `decode(InputStream in)` | Decodifica os bytes lidos do socket em uma `String` UTF-8. |
| `readLen(InputStream in)` | Lê e interpreta o cabeçalho de **comprimento variável** para determinar quantos bytes formam a próxima palavra. |

## Detalhes da Conexão

| Parâmetro | Valor Padrão | Descrição |
| :--- | :--- | :--- |
| `PORT` | `8728` | A porta padrão para a API não criptografada do MikroTik. |
| `TIMEOUT` | `30000` ms (30s) | O tempo limite para tentativas de conexão e operações de leitura de socket. |

## Tratamento de Respostas

A resposta do MikroTik é processada no método `execute` e retorna uma lista de mapas:

```java
List<Map<String, String>> results = api.execute(cmd);
