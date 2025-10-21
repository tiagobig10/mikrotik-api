package br.com.mikrotikApi.mikrotik.impl;

import br.com.mikrotikApi.mikrotik.ApiMk;
import javax.net.SocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;

// A classe implementa a interface ApiMk e contém a lógica para se conectar e comunicar com o MikroTik.
public class ApiMkImpl implements ApiMk {
    // Porta padrão da API do MikroTik (API não segura/sem criptografia)
    public int PORT = 8728;
    // Tempo limite padrão para operações de socket (30 segundos)
    public int TIMEOUT = 30000;
    // O socket usado para a conexão com o MikroTik
    private Socket sock = null;
    // Flag que indica se o cliente está conectado e autenticado
    private boolean connected = false;

    /**
     * Tenta estabelecer uma conexão de socket com o endereço IP e porta especificados
     * e, em seguida, realiza o processo de login na API do MikroTik.
     *
     * @param ip Endereço IP do MikroTik.
     * @param login Nome de usuário.
     * @param password Senha.
     * @param port Porta da API (se for menor que 1, usa a porta padrão 8728).
     */
    @Override
    public void login(String ip, String login, String password, int port) {
        try {
            // Usa a porta padrão se a porta fornecida for inválida
            if (port < 1) {
                port = this.PORT;
            }
            // 1. Cria e conecta o socket
            this.sock = SocketFactory.getDefault().createSocket();
            // Tenta a conexão com um timeout
            this.sock.connect(new InetSocketAddress(InetAddress.getByName(ip), port), this.TIMEOUT);
            // Define o tempo limite de leitura no socket
            sock.setSoTimeout(this.TIMEOUT);

            // 2. Cria o comando de login
            // A API do MikroTik usa "/login" para a primeira etapa de autenticação
            Command cmd = new Command("/login");
            cmd.addParameter("name", login);
            cmd.addParameter("password", password); // Nota: Em versões mais antigas do RouterOS, o login é em duas etapas (desafio/resposta). Este código parece assumir uma autenticação simples ou usa o protocolo de API moderno.

            // 3. Executa o comando de login
            execute(cmd, this.sock);
            this.connected = true;
        } catch (UnknownHostException ex) {
            // Exceção se o nome do host/IP for inválido
            throw new RuntimeException(String.format("Endereço de Ip Invalido '%s'", ip), ex);
        } catch (IOException ex) {
            // Exceção se não for possível conectar (ex: firewall, serviço desativado)
            throw new RuntimeException(String.format("Não fui possivél conectar em %s:%d : %s", ip, port, ex.getMessage()), ex);
        } catch (Exception ex) {
            // Tratamento específico para erro de login/senha da API do MikroTik
            if (ex.getMessage().equals("invalid user name or password (6)")) {
                throw new RuntimeException("nome de usuário ou senha inválidos!");
            }
            // Repassa outros erros
            throw new RuntimeException(ex.getMessage());
        }
    }

    /**
     * Executa um comando no MikroTik após a autenticação.
     *
     * @param cmd O objeto Command contendo o comando e parâmetros a serem enviados.
     * @return Uma lista de Maps, onde cada Map representa um "registro" de resposta do MikroTik.
     * @throws RuntimeException se o cliente não estiver autenticado.
     */
    @Override
    public List<Map<String, String>> execute(Command cmd) throws Exception {
        if (this.connected) {
            return execute(cmd, this.sock); // Chama o método de execução real
        }
        // Lança exceção se tentar executar um comando sem login
        throw new RuntimeException("mikrotik não autenticado");
    }

    /**
     * Fecha a conexão com o MikroTik e limpa os recursos de I/O.
     */
    @Override
    public void closed() {
        try {
            this.connected = false;
            // Fecha o socket e streams de I/O (a ordem nem sempre é estritamente necessária, mas é uma boa prática)
            this.sock.close();
            // Fechamento de streams é ignorado se lançar exceção, pois o close() do socket normalmente já lida com isso.
            this.sock.getInputStream().close();
            this.sock.getOutputStream().close();
        } catch (Exception ignored) {
            // Erros ao fechar são ignorados, garantindo que o estado 'connected' seja falso.
        }
    }

    /**
     * Define o tempo limite (timeout) para operações de socket.
     * Garante um valor mínimo de 100ms.
     *
     * @param timeOut O novo valor de timeout em milissegundos.
     */
    @Override
    public void setTimeOut(int timeOut) {
        if (timeOut < 100) {
            this.TIMEOUT = 100;
            return;
        }
        this.TIMEOUT = timeOut;
    }

    /**
     * Lógica principal de comunicação: envia o comando e processa a resposta.
     *
     * @param cmd O objeto Command a ser enviado.
     * @param sock O socket ativo da conexão.
     * @return Uma lista de resultados (registros).
     */
    private List<Map<String, String>> execute(Command cmd, Socket sock) throws Exception {
        List<Map<String, String>> list = new ArrayList<>(); // Lista final de resultados
        Map<String, String> map = new HashMap<>(); // Mapa para armazenar os atributos de um único registro

        try {
            // 1. Envia o comando para o MikroTik
            write(cmd, sock.getOutputStream());
            boolean error = false; // Flag para indicar se a resposta contém um erro (!trap)

            // 2. Loop de leitura e decodificação da resposta
            while (true){
                String s = decode(sock.getInputStream()); // Lê e decodifica a próxima 'palavra' da resposta

                // Tenta parsear a 'palavra' como um par chave/valor (ex: "=name=value")
                try {
                    // Divide por "=" (máximo de 3 partes): [ "", "name", "value" ] -> Ignora a primeira, usa [1] e [2]
                    map.put(s.split("=", 3)[1], s.split("=", 3)[2]);
                } catch (Exception ignored) {
                }

                // Trata a tag (ex: ".tag=123") - O formato da tag é diferente de um campo de resultado
                if (s.equals(".tag=")) {
                    try {
                        // Divide por "=": [ ".tag", "123" ] -> Usa [0] e [1]
                        map.put(s.split("=")[0], s.split("=")[1]);
                    } catch (Exception ignored) {
                    }
                }

                // Verifica se a resposta é um erro
                if (s.equals("!trap")) {
                    error = true;
                }

                // Um String vazio ("") indica o fim de um bloco de resposta (registro)
                if (s.isEmpty()) {
                    if (error) {
                        // Se houve !trap e um registro vazio, lança exceção com a mensagem de erro
                        throw new RuntimeException(map.get("message"));
                    }
                    if (!map.isEmpty()) {
                        // Adiciona o registro atual (mapa) à lista de resultados
                        list.add(map);
                    }
                    // Reinicia o mapa para o próximo registro
                    map = new HashMap<>();
                }

                // Condição de finalização do loop
                // "!done" indica sucesso e fim da resposta.
                // "!trap" indica erro e fim da resposta.
                if (s.equals("!done") || s.equals("!trap") ) {
                    break;
                }
            }

        } catch (Exception e) {
            // Encapsula o erro em uma exceção de aplicação com status 403 Forbidden
            throw new Exception(e.getMessage());
        }
        return list;
    }

    /**
     * Decodifica uma 'palavra' da API do MikroTik lendo seu comprimento e, em seguida, os bytes.
     *
     * @param in InputStream do socket.
     * @return A 'palavra' decodificada como String.
     */
    private String decode(InputStream in) {
        try {
            // 1. Lê o comprimento da palavra
            int len = readLen(in);
            byte[] buf = new byte[len];

            // 2. Lê os bytes da palavra
            for (int i = 0; i < len; ++i) {
                int c = in.read(); // Lê um byte
                buf[i] = (byte) (c & 0xFF); // Armazena como byte (garante que seja tratado como valor de 8 bits)
            }
            // 3. Converte os bytes para String usando UTF-8
            return new String(buf, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }

    /**
     * Lê e decodifica o comprimento de uma 'palavra' da API.
     * O comprimento é codificado usando um esquema de codificação de comprimento variável (similar ao VarInt, mas específico do MikroTik).
     *
     * @param in InputStream do socket.
     * @return O comprimento da palavra em bytes.
     * @throws Exception se a leitura falhar.
     */
    private int readLen(InputStream in) throws Exception {
        int c = in.read(); // Lê o primeiro byte (que contém o comprimento OU informações de quantos bytes de comprimento seguirão)
        if (c > 0) {
            // 1. Comprimento de 1 byte (bit mais significativo 0): 0xxxxxxx (até 127)
            if ((c & 0x80) == 0) {
                // 'c' já é o comprimento
            }
            // 2. Comprimento de 2 bytes (bits mais significativos 10): 10xxxxxx xxxxxxxx (até 16383)
            else if ((c & 0xC0) == 0x80) {
                c = c & ~0xC0; // Limpa os bits de identificação (10)
                c = (c << 8) | in.read(); // Combina com o próximo byte
            }
            // 3. Comprimento de 3 bytes (bits mais significativos 110): 110xxxxx xxxxxxxx xxxxxxxx (até 2097151)
            else if ((c & 0xE0) == 0xC0) {
                c = c & ~0xE0; // Limpa os bits de identificação (110)
                c = (c << 8) | in.read();
                c = (c << 8) | in.read();
            }
            // 4. Comprimento de 4 bytes (bits mais significativos 1110): 1110xxxx xxxxxxxx xxxxxxxx xxxxxxxx (até 268435455)
            else if ((c & 0xF0) == 0xE0) {
                c = c & ~0xF0; // Limpa os bits de identificação (1110)
                c = (c << 8) | in.read();
                c = (c << 8) | in.read();
                c = (c << 8) | in.read();
            }
            // 5. Comprimento de 5 bytes (byte inicial 11110xxx, seguido por 4 bytes de comprimento):
            else if ((c & 0xF8) == 0xF0) {
                // O primeiro byte (c) é descartado (ou contém apenas flags), e 4 bytes seguem para o comprimento.
                c = in.read();
                c = (c << 8) | in.read();
                c = (c << 8) | in.read();
                c = (c << 8) | in.read();
                c = (c << 8) | in.read(); // Erro? Parece que 5 bytes de dados são lidos após o byte de controle. O padrão MikroTik API usa 5 bytes totais de comprimento, onde o primeiro é 0xF0 e os 4 seguintes são o comprimento. Este código lê 5 bytes após 0xF0.
            }
        }
        return c;
    }

    /**
     * Escreve um objeto Command no OutputStream do socket, formatando-o no protocolo da API do MikroTik.
     *
     * @param cmd O objeto Command a ser enviado.
     * @param out OutputStream do socket.
     * @throws Exception se a escrita falhar.
     */
    private void write(Command cmd, OutputStream out) throws Exception {
        // 1. Envia o comando principal (ex: "/ip/address/print")
        encode(cmd.getCommand(), out);

        // 2. Envia os parâmetros (ex: "=name=value")
        for (Parameter param : cmd.getParameters()) {
            encode(String.format("=%s=%s", param.getName(), param.hasValue() ? param.getValue() : ""), out);
        }

        // 3. Envia a tag se existir (ex: ".tag=123")
        String tag = cmd.getTag();
        if ((tag != null) && !tag.isEmpty()) {
            encode(String.format(".tag=%s", tag), out);
        }

        // 4. Envia a lista de propriedades solicitadas (ex: "=.proplist=.id,address,interface")
        List<String> props = cmd.getProperties();
        if (!props.isEmpty()) {
            StringBuilder buf = new StringBuilder("=.proplist=");
            for (int i = 0; i < props.size(); ++i) {
                if (i > 0) {
                    buf.append(",");
                }
                buf.append(props.get(i));
            }
            encode(buf.toString(), out);
        }

        // 5. Envia as cláusulas de consulta/filtros (ex: "?address=192.168.88.1")
        for (String query : cmd.getQueries()) {
            encode(query, out);
        }

        // 6. Termina o comando com um byte nulo (terminador)
        out.write(0);
    }

    /**
     * Codifica uma String (uma 'palavra' do protocolo) com seu cabeçalho de comprimento variável
     * e a escreve no OutputStream. É o inverso de readLen/decode.
     *
     * @param word A String a ser codificada e enviada.
     * @param out OutputStream do socket.
     * @throws Exception se a escrita falhar.
     */
    private void encode(String word, OutputStream out) throws Exception {
        byte[] bytes = word.getBytes(StandardCharsets.UTF_8);
        int len = bytes.length; // Comprimento real da palavra

        // Seleciona o esquema de codificação de comprimento com base no tamanho

        // 1. Comprimento de 1 byte (até 0x7F / 127)
        if (len < 0x80) {
            out.write(len);
        }
        // 2. Comprimento de 2 bytes (até 0x3FFF / 16383)
        else if (len < 0x4000) {
            len = len | 0x8000; // Define o bit 15 (1) e o bit 14 (0) -> 10xxxx xxxxxxxx
            out.write(len >> 8);
            out.write(len);
        }
        // 3. Comprimento de 3 bytes (até 0x1FFFFF / 2097151)
        else if (len < 0x20000) {
            len = len | 0xC00000; // Define os bits 23 e 22 (11) e o bit 21 (0) -> 110x xxxxxxxx xxxxxxxx
            out.write(len >> 16);
            out.write(len >> 8);
            out.write(len);
        }
        // 4. Comprimento de 4 bytes (até 0xFFFFFFF / 268435455)
        else if (len < 0x10000000) {
            len = len | 0xE0000000; // Define os bits 31, 30 e 29 (111) e o bit 28 (0) -> 1110 xxxxxxxx xxxxxxxx xxxxxxxx
            out.write(len >> 24);
            out.write(len >> 16);
            out.write(len >> 8);
            out.write(len);
        }
        // 5. Comprimento de 5 bytes (mais de 268435455)
        else {
            out.write(0xF0); // Byte de controle (11110000)
            out.write(len >> 24);
            out.write(len >> 16);
            out.write(len >> 8);
            out.write(len);
        }

        // 6. Escreve os bytes da palavra
        out.write(bytes);
    }
}