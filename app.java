///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS io.javalin:javalin:4.1.1 org.slf4j:slf4j-simple:1.7.31
//SOURCES Opener.java

import java.util.Map;
import java.util.Map.Entry;

import io.javalin.http.Context;

class app extends Opener {
    private Map<String, String> usrPwds;

    @Override
    public Integer call() throws Exception {
        String users = System.getenv("DOP_USERS");
        if (users == null) {
            System.out.println("Missing DOP_USERS and/or DOP_RIGHTS environment variables");
            return 1;
        }
        usrPwds = toMap(users);
        
        System.out.println("Users: " + usrPwds.keySet());

        return super.call();
    }

    @Override
    public String authorized(Context ctx) {
        String ip = ctx.req.getRemoteAddr();
        if (isBlocked(ip)) {
            System.out.println("IP is blocked: " + ip);
            return null;
        }
        String token = ctx.req.getParameter("token");
        if (token == null) {
            System.out.println("Missing parameter for authentication, need: token");
            logAuth(ip, false);
            return null;
        }

        String usr = null;
        for (Entry<String, String> e : usrPwds.entrySet()) {
            if (e.getValue().equals(token)) {
                usr  = e.getKey();
            }
        }
        if (usr == null) {
            System.out.println("No user for provided token: " + token);
            logAuth(ip, false);
            return null;
        }

        logAuth(ip, true);
        System.out.println("User authorized: " + usr);

        return usr;
    }

    public static void main(String... args) throws Exception {
        app app = new app();
        int res = app.call();
        if (res != 0) {
            System.exit(res);
        }
    }
}

