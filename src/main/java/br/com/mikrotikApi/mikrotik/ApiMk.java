package br.com.mikrotikApi.mikrotik;


import br.com.mikrotikApi.mikrotik.impl.Command;
import java.util.List;
import java.util.Map;

public interface ApiMk {

    void login(String ip, String login, String password, int port);

    List<Map<String,String>> execute(Command cmd) throws Exception;

    void setTimeOut(int timeOut);

    void closed();

}
