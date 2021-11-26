
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.UnauthorizedResponse;

public abstract class Opener implements Callable<Integer> {
    private static final String nukiBridgeUrl = "http://%s:8080/lockAction?token=%s&nukiId=%s&deviceType=%d&action=%d";
    private final String nukiBridgeHost = System.getenv("DOP_HOST");
    private final String nukiBridgeToken = System.getenv("DOP_TOKEN");
    private Map<String, String> doorIds;
    private Map<String, String> doorRights;
 
    private static class FailedAuth {
        Instant last;
        int count;
    }

    private final Map<String, FailedAuth> failedAuths = new HashMap<>();

    @Override
    public Integer call() throws Exception {
        String doors = System.getenv("DOP_DOORS");
        String rights = System.getenv("DOP_RIGHTS");
        if (doors == null || rights == null || nukiBridgeHost == null || nukiBridgeToken == null) {
            System.out.println("Missing DOP_DOORS, DOP_RIGHTS, DOP_HOST and/or DOP_TOKEN environment variables");
            System.exit(1);
        }
        doorIds = toMap(doors);
        doorRights = toMap(rights);
        
        System.out.println("Doors: " + doorIds.keySet());
        System.out.println("Rights: " + doorRights);

        Javalin app = Javalin.create().start(8080);
        System.out.println("Takida Door Opener Proxy started on port 8080");
        app.before(ctx -> {
            String usr = authorized(ctx);
            if (usr == null) {
                System.out.println("Received unauthorized request : " + ctx.req + " from " + ctx.req.getRemoteAddr());
                throw new UnauthorizedResponse();
            }
            ctx.attribute("authUser", usr);
        });
        app.get("/open", ctx -> {
            String usr = ctx.attribute("authUser");
            String door = ctx.req.getParameter("door");
            System.out.println(String.format("User %s requesting to open door: %s", usr, door));
            if (door == null || !doorIds.containsKey(door)) {
                System.out.println("Unknown door : " + door);
                throw new BadRequestResponse();
            }
            if (door != null && !allowed(usr, door)) {
                System.out.println(String.format("User %s not allowed to use door: %s", usr, door));
                throw new UnauthorizedResponse();
            }
            if (!openDoor(door)) {
                System.out.println("Unable to open door : " + door);
                throw new InternalServerErrorResponse("Fail");
            }
            System.out.println("Opened door successfully: " + door);
            ctx.result("OK");
        });
        
        return 0;
    }

    /**
     * Should check if the request is authorized and must return
     * an authenticated user id
     * @param ctx Request context
     * @return A user id
     */
    public abstract String authorized(Context ctx);

    protected String hash(String... args) {
        return hash(String.join(",", args));
    }

    protected String hash(String msg) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] messageDigest = md.digest(msg.getBytes());
            BigInteger no = new BigInteger(1, messageDigest);
            return no.toString(16);
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    protected boolean allowed(String id, String door) {
        String rights = doorRights.get(id);
        return rights != null &&  Arrays.asList(rights.split(":")).contains(door);
    }

    protected synchronized void logAuth(String id, boolean success) {
        if (success) {
            failedAuths.remove(id);
        } else {
            FailedAuth fa = failedAuths.get(id);
            if (fa == null || blockedEnough(fa)) {
                fa = new FailedAuth();
                failedAuths.put(id, fa);
            }
            fa.last = Instant.now();
            fa.count++;
        }
    }

    protected synchronized boolean isBlocked(String id) {
        FailedAuth fa = failedAuths.get(id);
        return fa != null && fa.count >= getMaxAuthIntents() && !blockedEnough(fa);
    }

    private boolean blockedEnough(FailedAuth fa) {
        return Duration.between(fa.last, Instant.now()).compareTo(getBlockDuration()) > 0;
    }

    protected int getMaxAuthIntents() {
        return 3;
    }

    protected Duration getBlockDuration() {
        return Duration.ofMinutes(5);
    }

    private boolean openDoor(String door) {
        String info = doorIds.get(door);
        String[] id_type = info.split(":");
        String doorId = id_type[0];
        int devType = Integer.valueOf(id_type[1]);
        int action = 3;
        String uri = String.format(nukiBridgeUrl, nukiBridgeHost, nukiBridgeToken, doorId, devType, action);
        return sendOpenRequest(uri);
    }

    private static boolean sendOpenRequest(String uri) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .build();
    
        try {
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.out.println(String.format("Error %d trying to open door", response.statusCode()));
                return false;
            }
            return true;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    protected static Map<String, String> toMap(String items) {
        Map<String, String> res = new HashMap<>();
        String[] pairs = items.split(",");
        for (String pair : pairs) {
            String[] key_val = pair.split("=", 2);
            res.put(key_val[0], key_val[1]);
        }
        return res;
    }
}
