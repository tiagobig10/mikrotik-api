package br.com.mikrotikApi;

import br.com.mikrotikApi.mikrotik.ApiMk;
import br.com.mikrotikApi.mikrotik.impl.ApiMkImpl;
import br.com.mikrotikApi.mikrotik.impl.Command;

import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        ApiMk apiMk = new ApiMkImpl();

        try {
                              //ip             // login    // password       // port
            apiMk.login("192.168.88.1", "admin", "123456", 8728);
            Command cmd = new Command("/system/resource/print");
            //cmd.addProperty("uptime", "board-name");

            List<Map<String, String>> rs = apiMk.execute(cmd);

            // response
            System.out.println(rs);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            apiMk.closed();
        }

    }

}