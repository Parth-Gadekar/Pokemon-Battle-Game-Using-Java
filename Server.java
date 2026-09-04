import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.awt.Desktop;

public class Server {

    // ─── MAIN ────────────────────────────────────────────────────
    public static void main(String[] args) {
        try {
            Main.Game g = Main.Game.get();
            g.phase = Main.Phase.IDLE;

            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
            server.createContext("/api/pokemon", Server::handleGetPokemon);
            server.createContext("/api/state",   Server::handleGetState);
            server.createContext("/api/start",   Server::handlePostStart);
            server.createContext("/api/move",    Server::handlePostMove);
            server.createContext("/api/switch",  Server::handlePostSwitch);
            server.createContext("/api/reset",   Server::handlePostReset);
            server.createContext("/",            Server::handleStatic);
            server.setExecutor(null);
            server.start();
            System.out.println("Server running on http://localhost:8080");

            openBrowser("http://localhost:8080");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ─── OPEN DEFAULT BROWSER ──────────────────────────────────────
    static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Fallback for environments without Desktop support (some Linux setups)
        System.out.println("Could not auto-open a browser — open this URL manually: " + url);
    }

    // ─── STATIC FILE SERVER ──────────────────────────────────────
    static void handleStatic(HttpExchange ex) throws IOException {
        String uri = ex.getRequestURI().getPath();
        if (uri.equals("/")) uri = "/index.html";
        File f = new File("web" + uri);
        if (!f.exists()) { respond(ex, 404, "text/plain", "Not found"); return; }
        String mime = uri.endsWith(".html") ? "text/html"
                    : uri.endsWith(".css")  ? "text/css"
                    : uri.endsWith(".js")   ? "application/javascript"
                    : "application/octet-stream";
        byte[] data = Files.readAllBytes(f.toPath());
        ex.getResponseHeaders().set("Content-Type", mime);
        ex.sendResponseHeaders(200, data.length);
        ex.getResponseBody().write(data);
        ex.getResponseBody().close();
    }

    // ─── GET /api/pokemon ────────────────────────────────────────
    static void handleGetPokemon(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) {
            respond(ex, 405, "application/json", "{\"error\":\"Method not allowed\"}"); return;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int p = 0; p < Main.Constants.NUM_CREATURE; p++) {
            Main.Template t = Main.CreatureData.TEMPLATES[p];
            if (p > 0) sb.append(",");
            sb.append("{\"id\":").append(p)
              .append(",\"name\":\"").append(t.name).append("\"")
              .append(",\"type\":\"").append(Main.Constants.TYPE_NAME[t.type]).append("\"")
              .append(",\"hp\":").append(t.hp)
              .append(",\"attack\":").append(t.attack)
              .append(",\"spAtk\":").append(t.spAtk)
              .append(",\"defence\":").append(t.defence)
              .append(",\"spDef\":").append(t.spDef)
              .append(",\"speed\":").append(t.speed)
              .append(",\"moves\":[");
            for (int m = 0; m < Main.Constants.MOVES_TOTAL; m++) {
                Main.MoveBlueprint mb = t.moves[m];
                if (m > 0) sb.append(",");
                sb.append("{\"id\":").append(m)
                  .append(",\"name\":\"").append(mb.name).append("\"")
                  .append(",\"power\":").append(mb.power)
                  .append(",\"type\":\"").append(Main.Constants.TYPE_NAME[mb.type]).append("\"")
                  .append(",\"accuracy\":").append(mb.accuracy)
                  .append(",\"category\":\"").append(mb.category).append("\"")
                  .append(",\"effect\":\"").append(mb.effect != null ? mb.effect : "none").append("\"")
                  .append("}");
            }
            sb.append("]}");
        }
        sb.append("]");
        respond(ex, 200, "application/json", sb.toString());
    }

    // ─── GET /api/state ──────────────────────────────────────────
    static void handleGetState(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) {
            respond(ex, 405, "application/json", "{\"error\":\"Method not allowed\"}"); return;
        }
        Main.Game g = Main.Game.get();
        String phase = g.phase == Main.Phase.BATTLE ? "battle"
                     : g.phase == Main.Phase.OVER   ? "over" : "idle";
        String json = "{\"phase\":\"" + phase + "\""
            + ",\"player\":"      + partyToJson(g.player)
            + ",\"enemy\":"       + partyToJson(g.enemy)
            + ",\"log\":\""       + jsonEscape(g.log.toString()) + "\""
            + ",\"result\":"      + g.result
            + ",\"forceSwitch\":" + g.forceSwitch
            + "}";
        respond(ex, 200, "application/json", json);
    }

    // ─── POST /api/start ─────────────────────────────────────────
    static void handlePostStart(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) {
            respond(ex, 405, "application/json", "{\"error\":\"Method not allowed\"}"); return;
        }
        String body = new String(ex.getRequestBody().readAllBytes());

        int[] pids    = new int[Main.Constants.PARTY_SIZE];
        int[][] moves = new int[Main.Constants.PARTY_SIZE][Main.Constants.MOVES_BATTLE];

        for (int i = 0; i < Main.Constants.PARTY_SIZE; i++) {
            pids[i] = jsonGetInt(body, "pid" + i, i % Main.Constants.NUM_CREATURE);
            if (pids[i] < 0 || pids[i] >= Main.Constants.NUM_CREATURE) pids[i] = i % Main.Constants.NUM_CREATURE;
            int[] tmp = {0, 1, 2, 3};
            jsonGetArr4(body, "moves" + i, tmp);
            for (int j = 0; j < Main.Constants.MOVES_BATTLE; j++)
                moves[i][j] = (tmp[j] < 0 || tmp[j] >= Main.Constants.MOVES_TOTAL) ? j : tmp[j];
        }

        // random enemy team, no duplicates
        boolean[] used = new boolean[Main.Constants.NUM_CREATURE];
        int[] epids = new int[Main.Constants.PARTY_SIZE];
        for (int i = 0; i < Main.Constants.PARTY_SIZE; i++) {
            int ep;
            do { ep = (int)(Math.random() * Main.Constants.NUM_CREATURE); } while (used[ep]);
            used[ep] = true; epids[i] = ep;
        }

        Main.Game.reset();
        Main.Game g = Main.Game.get();

        for (int i = 0; i < Main.Constants.PARTY_SIZE; i++) {
            g.player.slots[i] = Main.FighterFactory.create(pids[i], moves[i]);
            int[] shuffle = {0,1,2,3,4,5,6,7};
            for (int j = Main.Constants.MOVES_TOTAL - 1; j > 0; j--) {
                int k = (int)(Math.random() * (j + 1));
                int tmp = shuffle[j]; shuffle[j] = shuffle[k]; shuffle[k] = tmp;
            }
            int[] emoves = {shuffle[0], shuffle[1], shuffle[2], shuffle[3]};
            g.enemy.slots[i] = Main.FighterFactory.create(epids[i], emoves);
        }

        g.player.active = 0;
        g.enemy.active  = 0;
        g.phase = Main.Phase.BATTLE;
        g.log.append("[START] Battle begins: ")
             .append(g.player.slots[0].name)
             .append(" VS ")
             .append(g.enemy.slots[0].name)
             .append("!\n");

        respond(ex, 200, "application/json", "{\"ok\":true}");
    }

    // ─── POST /api/move ──────────────────────────────────────────
    static void handlePostMove(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) {
            respond(ex, 405, "application/json", "{\"error\":\"Method not allowed\"}"); return;
        }
        Main.Game g = Main.Game.get();
        if (g.phase != Main.Phase.BATTLE || g.forceSwitch) {
            respond(ex, 400, "application/json", "{\"error\":\"Cannot use move now\"}"); return;
        }
        String body = new String(ex.getRequestBody().readAllBytes());
        int mi = jsonGetInt(body, "move_index", 0);
        if (mi < 0 || mi >= Main.Constants.MOVES_BATTLE) mi = 0;
        Main.BattleEngine.processRound(mi);
        respond(ex, 200, "application/json", "{\"ok\":true}");
    }

    // ─── POST /api/switch ────────────────────────────────────────
    static void handlePostSwitch(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) {
            respond(ex, 405, "application/json", "{\"error\":\"Method not allowed\"}"); return;
        }
        Main.Game g = Main.Game.get();
        if (g.phase != Main.Phase.BATTLE) {
            respond(ex, 400, "application/json", "{\"error\":\"Not in battle\"}"); return;
        }
        String body = new String(ex.getRequestBody().readAllBytes());
        int slot = jsonGetInt(body, "slot", -1);
        if (slot < 0 || slot >= Main.Constants.PARTY_SIZE
                || slot == g.player.active
                || g.player.slots[slot].hp <= 0) {
            respond(ex, 400, "application/json", "{\"error\":\"Invalid switch\"}"); return;
        }

        boolean wasForced = g.forceSwitch;
        g.player.active = slot;
        g.forceSwitch   = false;
        g.log.append("[SWITCH] You sent out ").append(g.player.slots[slot].name).append("!\n");

        if (!wasForced) {
            Main.Fighter pf = g.player.slots[g.player.active];
            Main.Fighter ef = g.enemy.slots[g.enemy.active];
            int em = Main.BattleEngine.EnemyPickMove();
            if (em < 0) {
                g.log.append("[SKIP] Enemy has no PP!\n");
            } else {
                boolean ko = Main.BattleEngine.doAttack(ef, pf, em);
                if (ko) {
                    if (Main.BattleEngine.countAlive(g.player) == 0)
                        { g.result = -1; g.phase = Main.Phase.OVER; }
                    else g.forceSwitch = true;
                }
            }
        }
        respond(ex, 200, "application/json", "{\"ok\":true}");
    }

    // ─── POST /api/reset ─────────────────────────────────────────
    static void handlePostReset(HttpExchange ex) throws IOException {
        Main.Game.reset();
        respond(ex, 200, "application/json", "{\"ok\":true}");
    }

    // ─── JSON SERIALISERS ────────────────────────────────────────

    static String fighterToJson(Main.Fighter f) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"name\":\"").append(f.name).append("\"")
          .append(",\"creatureId\":").append(f.creatureId)
          .append(",\"type\":\"").append(Main.Constants.TYPE_NAME[f.type]).append("\"")
          .append(",\"hp\":").append(f.hp)
          .append(",\"maxHp\":").append(f.maxHP)
          .append(",\"attack\":").append(f.attack)
          .append(",\"spAtk\":").append(f.spAtk)
          .append(",\"defence\":").append(f.defence)
          .append(",\"spDef\":").append(f.spDef)
          .append(",\"speed\":").append(f.speed)
          .append(",\"status\":\"").append(f.status).append("\"")
          .append(",\"moves\":[");
        for (int i = 0; i < Main.Constants.MOVES_BATTLE; i++) {
            Main.Move m = f.moves[i];
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(m.name).append("\"")
              .append(",\"power\":").append(m.power)
              .append(",\"type\":\"").append(Main.Constants.TYPE_NAME[m.type]).append("\"")
              .append(",\"accuracy\":").append(m.accuracy)
              .append(",\"category\":\"").append(m.category).append("\"")
              .append(",\"pp\":").append(m.PP)
              .append(",\"maxPp\":").append(m.maxPP)
              .append(",\"effect\":\"").append(m.effect != null ? m.effect : "none").append("\"")
              .append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    static String partyToJson(Main.Party party) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"active\":").append(party.active).append(",\"slots\":[");
        for (int i = 0; i < Main.Constants.PARTY_SIZE; i++) {
            if (i > 0) sb.append(",");
            if (party.slots[i] == null) { sb.append("null"); continue; }
            sb.append(fighterToJson(party.slots[i]));
        }
        sb.append("]}");
        return sb.toString();
    }

    // ─── JSON PARSERS ────────────────────────────────────────────

    static String jsonEscape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    static int jsonGetInt(String body, String key, int def) {
        String search = "\"" + key + "\":";
        int idx = body.indexOf(search);
        if (idx < 0) return def;
        String rest = body.substring(idx + search.length()).trim();
        try { return Integer.parseInt(rest.split("[^0-9\\-]")[0]); }
        catch (Exception e) { return def; }
    }

    static void jsonGetArr4(String body, String key, int[] out) {
        String search = "\"" + key + "\":[";
        int idx = body.indexOf(search);
        if (idx < 0) return;
        String rest = body.substring(idx + search.length());
        int end = rest.indexOf(']');
        if (end < 0) return;
        String[] parts = rest.substring(0, end).split(",");
        for (int i = 0; i < 4 && i < parts.length; i++) {
            try { out[i] = Integer.parseInt(parts[i].trim()); }
            catch (Exception e) { out[i] = i; }
        }
    }

    // ─── RESPOND HELPER ──────────────────────────────────────────
    static void respond(HttpExchange ex, int code, String mime, String body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", mime);
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] bytes = body.getBytes();
        ex.sendResponseHeaders(code, bytes.length);
        OutputStream os = ex.getResponseBody();
        os.write(bytes);
        os.close();
    }
}