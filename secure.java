///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS io.javalin:javalin:4.1.1 org.slf4j:slf4j-simple:1.7.31
//SOURCES Opener.java

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Map;

import io.javalin.http.Context;

class secure extends Opener {
    private Map<String, String> usrPwds;

    @Override
    public Integer call() throws Exception {
        String users = System.getenv("DOP_USERS");
        if (users == null) {
            System.out.println("Missing DOP_USERS environment variable");
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

        String ts = ctx.req.getParameter("ts");
        String cd = ctx.req.getParameter("cd");
        String usr = ctx.req.getParameter("usr");
        String hash = ctx.req.getParameter("hash");
        if (ts == null || cd == null || usr == null || hash == null) {
            System.out.println("Missing parameters for authentication, need: ts, cd, usr, hash");
            logAuth(ip, false);
            return null;
        }

        if (!usrPwds.containsKey(usr)) {
            System.out.println("Unknown user: " + usr);
            logAuth(ip, false);
            return null;
        }

        //TODO check timestamp is more-or-less recent

        //TODO check the random code is not being re-used
        
        String hashcheck = hash(ts, cd, usrPwds.get(usr));
        if (!hash.equalsIgnoreCase(hashcheck)) {
            System.out.println("Hashes don't match, given: " + hash + ", expected: " + hashcheck);
            logAuth(ip, false);
            logAuth(usr, false);
            return null;
        }

        logAuth(ip, true);
        logAuth(usr, true);
        System.out.println("User authorized: " + usr);

        return usr;
    }

    public static void main(String... args) throws Exception {
        secure app = new secure();
        int res = app.call();
        if (res != 0) {
            System.exit(res);
        }
    }
}

