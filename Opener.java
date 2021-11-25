
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
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
    private final String nukiBridgeIp = System.getenv("DOP_IP");
    private final String nukiBridgeToken = System.getenv("DOP_TOKEN");
    private Map<String, String> doorIds;
 
    private static class FailedAuth {
        Instant last;
        int count;
    }

    private final Map<String, FailedAuth> failedAuths = new HashMap<>();

    @Override
    public Integer call() throws Exception {
        String doors = System.getenv("DOP_DOORS");
        if (doors == null || nukiBridgeIp == null || nukiBridgeToken == null) {
            System.out.println("Missing DOP_USERS, DOP_DOORS, DOP_IP and/or DOP_TOKEN environment variables");
            System.exit(1);
        }
        doorIds = toMap(doors);
        
        System.out.println("Doors: " + doorIds.keySet());

        Javalin app = Javalin.create().start(8080);
        System.out.println("Takida Door Opener Proxy started on port 8080");
        app.before(ctx -> {
            if (!authorized(ctx)) {
                System.out.println("Received unauthorized request : " + ctx.req + " from " + ctx.req.getRemoteAddr());
                throw new UnauthorizedResponse();
            }
        });
        app.get("/open", ctx -> {
            System.out.println("Received request for opening a door : " + ctx.req);
            String door = ctx.req.getParameter("door");
            if (door == null || !doorIds.containsKey(door)) {
                System.out.println("Unknown door : " + door);
                throw new BadRequestResponse();
            }
            if (!openDoor(door)) {
                System.out.println("Unable to open door : " + door);
                throw new InternalServerErrorResponse("Fail");
            }
            ctx.result("OK");
        });
        
        return 0;
    }

    public abstract boolean authorized(Context ctx);

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
        String uri = String.format(nukiBridgeUrl, nukiBridgeIp, nukiBridgeToken, doorId, devType, action);
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
