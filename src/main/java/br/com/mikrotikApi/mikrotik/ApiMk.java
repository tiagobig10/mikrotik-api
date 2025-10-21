package com.esotar.api.integrations.mikrotik;

import com.esotar.api.integrations.mikrotik.impl.Command;

import java.util.List;
import java.util.Map;

public interface ApiMk {

    void login(String ip, String login, String password, int port);

    List<Map<String,String>> execute(Command cmd);

    void setTimeOut(int timeOut);

    void closed();

}
