package com.esotar.api.integrations.mikrotik.impl;

import com.esotar.api.exeptions.IsonAppException;
import com.esotar.api.integrations.mikrotik.ApiMk;
import org.springframework.http.HttpStatus;

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


public class ApiMkImpl implements ApiMk {
    public int PORT = 8728;
    public int TIMEOUT = 30000;
    private Socket sock = null;
    private boolean connected = false;

    @Override
    public void login(String ip, String login, String password, int port) {
        try {
            if (port < 1) {
                port = this.PORT;
            }
            this.sock = SocketFactory.getDefault().createSocket();
            this.sock.connect(new InetSocketAddress(InetAddress.getByName(ip), port), this.TIMEOUT);
            sock.setSoTimeout(this.TIMEOUT);

            Command cmd = new Command("/login");
            cmd.addParameter("name", login);
            cmd.addParameter("password", password);

            //login
            execute(cmd, this.sock);
            this.connected = true;
        } catch (UnknownHostException ex) {
            throw new RuntimeException(String.format("Endereço de Ip Invalido '%s'", ip), ex);
        } catch (IOException ex) {
            throw new RuntimeException(String.format("Não fui possivél conectar em %s:%d : %s", ip, port, ex.getMessage()), ex);
        } catch (Exception ex) {
            if (ex.getMessage().equals("invalid user name or password (6)")) {
                throw new RuntimeException("nome de usuário ou senha inválidos!");
            }
            throw new RuntimeException(ex.getMessage());
        }
    }

    @Override
    public List<Map<String, String>> execute(Command cmd) {
        if (this.connected) {
            return execute(cmd, this.sock);
        }
        throw new RuntimeException("mikrotik não autenticado");
    }

    @Override
    public void closed() {
        try {
            this.connected = false;
            this.sock.close();
            this.sock.getInputStream().close();
            this.sock.getOutputStream().close();
        } catch (Exception ignored) {

        }
    }

    @Override
    public void setTimeOut(int timeOut) {
        if (timeOut < 100) {
            this.TIMEOUT = 100;
            return;
        }
        this.TIMEOUT = timeOut;
    }

    private List<Map<String, String>> execute(Command cmd, Socket sock) {
        List<Map<String, String>> list = new ArrayList<>();
        Map<String, String> map = new HashMap<>();

        try {
            write(cmd, sock.getOutputStream());
            boolean error = false;
            while (true){
               String s = decode(sock.getInputStream());

                try {
                    map.put(s.split("=", 3)[1], s.split("=", 3)[2]);
                } catch (Exception ignored) {
                }
                if (s.equals(".tag=")) {
                    try {
                        map.put(s.split("=")[0], s.split("=")[1]);
                    } catch (Exception ignored) {
                    }
                }
                if (s.equals("!trap")) {
                    error = true;
                }
                if (s.isEmpty()) {
                    if (error) {
                        throw new RuntimeException(map.get("message"));
                    }
                    if (!map.isEmpty()) {
                        list.add(map);
                    }
                    map = new HashMap<>();
                }

                //finality
                if (s.equals("!done") || s.equals("!trap") ) {
                    break;
                }
            }

        } catch (Exception e) {
            throw new IsonAppException(HttpStatus.FORBIDDEN, e.getMessage());
        }
        return list;
    }

    private String decode(InputStream in) {
        try {
            int len = readLen(in);
            byte[] buf = new byte[len];
            for (int i = 0; i < len; ++i) {
                int c = in.read();
                buf[i] = (byte) (c & 0xFF);
            }
            return new String(buf, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }

    private int readLen(InputStream in) throws Exception {
        int c = in.read();
        if (c > 0) {
            if ((c & 0x80) == 0) {
            } else if ((c & 0xC0) == 0x80) {
                c = c & ~0xC0;
                c = (c << 8) | in.read();
            } else if ((c & 0xE0) == 0xC0) {
                c = c & ~0xE0;
                c = (c << 8) | in.read();
                c = (c << 8) | in.read();
            } else if ((c & 0xF0) == 0xE0) {
                c = c & ~0xF0;
                c = (c << 8) | in.read();
                c = (c << 8) | in.read();
                c = (c << 8) | in.read();
            } else if ((c & 0xF8) == 0xF0) {
                c = in.read();
                c = (c << 8) | in.read();
                c = (c << 8) | in.read();
                c = (c << 8) | in.read();
                c = (c << 8) | in.read();
            }
        }
        return c;
    }

    private void write(Command cmd, OutputStream out) throws Exception {
        encode(cmd.getCommand(), out);
        for (Parameter param : cmd.getParameters()) {
            encode(String.format("=%s=%s", param.getName(), param.hasValue() ? param.getValue() : ""), out);
        }
        String tag = cmd.getTag();
        if ((tag != null) && !tag.isEmpty()) {
            encode(String.format(".tag=%s", tag), out);
        }
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
        for (String query : cmd.getQueries()) {
            encode(query, out);
        }
        out.write(0);
    }

    private void encode(String word, OutputStream out) throws Exception {
        byte[] bytes = word.getBytes(StandardCharsets.UTF_8);
        int len = bytes.length;
        if (len < 0x80) {
            out.write(len);
        } else if (len < 0x4000) {
            len = len | 0x8000;
            out.write(len >> 8);
            out.write(len);
        } else if (len < 0x20000) {
            len = len | 0xC00000;
            out.write(len >> 16);
            out.write(len >> 8);
            out.write(len);
        } else if (len < 0x10000000) {
            len = len | 0xE0000000;
            out.write(len >> 24);
            out.write(len >> 16);
            out.write(len >> 8);
            out.write(len);
        } else {
            out.write(0xF0);
            out.write(len >> 24);
            out.write(len >> 16);
            out.write(len >> 8);
            out.write(len);
        }
        out.write(bytes);
    }

}