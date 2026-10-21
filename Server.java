import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.awt.Desktop;
import java.util.*;

public class Server {

    static final int PORT = 8080;

    // ─── MAIN ────────────────────────────────────────────────────
    public static void main(String[] args) {
        try {
            Main.Game g = Main.Game.get();
            g.phase = Main.Phase.IDLE;

            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/api/pokemon", Server::handleGetPokemon);
            server.createContext("/api/state",   Server::handleGetState);
            server.createContext("/api/start",   Server::handlePostStart);
            server.createContext("/api/move",    Server::handlePostMove);
            server.createContext("/api/switch",  Server::handlePostSwitch);
            server.createContext("/api/reset",   Server::handlePostReset);
            server.createContext("/api/lan/info", Server::handleLanInfo);
            server.createContext("/api/lan/host", Server::handleLanHost);
            server.createContext("/api/lan/join", Server::handleLanJoin);
            server.createContext("/api/pick",     Server::handlePostPick);
            server.createContext("/",            Server::handleStatic);
            server.setExecutor(null);
            server.start();
            System.out.println("Server running on http://localhost:" + PORT);
            System.out.println("Friends on the same Wi-Fi can join at http://" + lanAddresses().get(0) + ":" + PORT + "/?join");

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
        Path root = Paths.get("web").toAbsolutePath().normalize();
        Path f    = root.resolve(uri.substring(1)).normalize();
        // refuse anything that climbs out of web/ (e.g. /../Main.java)
        if (!f.startsWith(root) || !Files.isRegularFile(f)) { respond(ex, 404, "text/plain", "Not found"); return; }
        String mime = uri.endsWith(".html") ? "text/html; charset=utf-8"
                    : uri.endsWith(".css")  ? "text/css; charset=utf-8"
                    : uri.endsWith(".js")   ? "application/javascript; charset=utf-8"
                    : "application/octet-stream";
        byte[] data = Files.readAllBytes(f);
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
    // In a LAN game the view is per seat (?t=<token>): the guest sees the
    // parties swapped so the client can always treat "player" as itself.
    static void handleGetState(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) {
            respond(ex, 405, "application/json", "{\"error\":\"Method not allowed\"}"); return;
        }
        Main.Game g = Main.Game.get();
        int seat = 0;
        boolean away = false;
        if (g.lan) {
            seat = seatFor(g, queryParam(ex, "t"));
            if (seat == 0) { respond(ex, 403, "application/json", "{\"error\":\"Not a player in this game\"}"); return; }
            long now = System.currentTimeMillis();
            if (seat == 1) g.seen1 = now; else g.seen2 = now;
            long other = (seat == 1) ? g.seen2 : g.seen1;
            away = g.phase == Main.Phase.BATTLE && other > 0 && now - other > 20000;
        }
        boolean flip = (seat == 2);
        Main.Party me  = flip ? g.enemy  : g.player;
        Main.Party opp = flip ? g.player : g.enemy;
        int result     = flip ? -g.result : g.result;
        String phase = g.phase == Main.Phase.BATTLE ? "battle"
                     : g.phase == Main.Phase.OVER   ? "over"
                     : g.phase == Main.Phase.LOBBY  ? "lobby" : "idle";
        String json = "{\"phase\":\"" + phase + "\""
            + ",\"player\":"      + partyToJson(me, true)
            + ",\"enemy\":"       + partyToJson(opp, !g.lan)   // opponent's moves stay hidden online
            + ",\"log\":\""       + jsonEscape(g.log.toString()) + "\""
            + ",\"result\":"      + result
            + ",\"forceSwitch\":" + g.forceSwitch
            + ",\"lan\":"         + g.lan
            + ",\"seat\":"        + seat
            + ",\"round\":"       + g.round
            + ",\"away\":"        + away
            + "}";
        respond(ex, 200, "application/json", json);
    }

    // ─── POST /api/start ─────────────────────────────────────────
    static void handlePostStart(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) {
            respond(ex, 405, "application/json", "{\"error\":\"Method not allowed\"}"); return;
        }
        if (Main.Game.get().lan && Main.Game.get().phase != Main.Phase.OVER) {
            respond(ex, 409, "application/json", "{\"error\":\"A LAN game is in progress\"}"); return;
        }
        String body = new String(ex.getRequestBody().readAllBytes());

        int size = jsonGetInt(body, "mode", 3);
        if (size != 1) size = 3;
        boolean pvp = jsonGetInt(body, "pvp", 0) == 1;
        if (pvp) size = 1;

        int[] pids    = new int[size];
        int[][] moves = new int[size][Main.Constants.MOVES_BATTLE];

        for (int i = 0; i < size; i++) {
            pids[i] = jsonGetInt(body, "pid" + i, i % Main.Constants.NUM_CREATURE);
            if (pids[i] < 0 || pids[i] >= Main.Constants.NUM_CREATURE) pids[i] = i % Main.Constants.NUM_CREATURE;
            int[] tmp = {0, 1, 2, 3};
            jsonGetArr4(body, "moves" + i, tmp);
            for (int j = 0; j < Main.Constants.MOVES_BATTLE; j++)
                moves[i][j] = (tmp[j] < 0 || tmp[j] >= Main.Constants.MOVES_TOTAL) ? j : tmp[j];
        }

        boolean[] used = new boolean[Main.Constants.NUM_CREATURE];
        int[] epids = new int[size];
        for (int i = 0; i < size; i++) {
            int ep;
            do { ep = (int)(Math.random() * Main.Constants.NUM_CREATURE); } while (used[ep]);
            used[ep] = true; epids[i] = ep;
        }


        int[] p2moves = {0, 1, 2, 3};
        if (pvp) {
            epids[0] = jsonGetInt(body, "p2pid0", 0);
            if (epids[0] < 0 || epids[0] >= Main.Constants.NUM_CREATURE) epids[0] = 0;
            jsonGetArr4(body, "p2moves0", p2moves);
            for (int j = 0; j < 4; j++)
                if (p2moves[j] < 0 || p2moves[j] >= Main.Constants.MOVES_TOTAL) p2moves[j] = j;
        }

        Main.Game.reset();
        Main.Game g = Main.Game.get();
        g.player = new Main.Party(size);
        g.enemy  = new Main.Party(size);
        g.pvp    = pvp;

        for (int i = 0; i < size; i++) {
            g.player.slots[i] = Main.FighterFactory.create(pids[i], moves[i]);
            int[] shuffle = {0,1,2,3,4,5,6,7};
            for (int j = Main.Constants.MOVES_TOTAL - 1; j > 0; j--) {
                int k = (int)(Math.random() * (j + 1));
                int tmp = shuffle[j]; shuffle[j] = shuffle[k]; shuffle[k] = tmp;
            }
            int[] emoves = pvp ? p2moves : new int[]{shuffle[0], shuffle[1], shuffle[2], shuffle[3]};
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
            if (g.lan || g.phase != Main.Phase.BATTLE || g.forceSwitch) {
                respond(ex, 400, "application/json", "{\"error\":\"Cannot use move now\"}"); return;
            }
            String body = new String(ex.getRequestBody().readAllBytes());
            int mi = jsonGetInt(body, "move_index", 0);
            if (mi < 0 || mi >= Main.Constants.MOVES_BATTLE) mi = 0;
            if (g.pvp) {
                Main.Fighter ef = g.enemy.slots[g.enemy.active];
                int mi2 = jsonGetInt(body, "move_index2", 0);
                if (Main.BattleEngine.allPpEmpty(ef)) mi2 = -1;   // P2 must Struggle
                else if (mi2 < 0 || mi2 >= Main.Constants.MOVES_BATTLE) mi2 = 0;
                Main.BattleEngine.processRound(mi, mi2);
            } else {
                Main.BattleEngine.processRound(mi);
            }
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
            if (slot < 0 || slot >= g.player.slots.length
                    || slot == g.player.active
                    || g.player.slots[slot].hp <= 0) {
                respond(ex, 400, "application/json", "{\"error\":\"Invalid switch\"}"); return;
            }

            boolean wasForced = g.forceSwitch;
            g.player.active = slot;
            g.forceSwitch   = false;
            g.log.append("[SWITCH] You sent out ").append(g.player.slots[slot].name).append("!\n");

            // voluntary switch costs a turn: enemy gets a free attack
            if (!wasForced) {
                Main.Fighter pf = g.player.slots[g.player.active];
                Main.Fighter ef = g.enemy.slots[g.enemy.active];
                int em = Main.BattleEngine.EnemyPickMove();
                boolean ko = (em < 0) ? Main.BattleEngine.doStruggle(ef, pf)
                                    : Main.BattleEngine.doAttack(ef, pf, em);
                if (ko) {
                    if (Main.BattleEngine.countAlive(g.player) == 0) { g.result = -1; g.phase = Main.Phase.OVER; }
                    else g.forceSwitch = true;
                } else if (ef.hp == 0) {   // enemy died from Struggle recoil
                    if (Main.BattleEngine.countAlive(g.enemy) == 0) { g.result = 1; g.phase = Main.Phase.OVER; }
                    else Main.BattleEngine.enemyAutoSwitch();
                }
            }
            respond(ex, 200, "application/json", "{\"ok\":true}");
        }

    // ─── POST /api/reset ─────────────────────────────────────────
    static void handlePostReset(HttpExchange ex) throws IOException {
        Main.Game cur = Main.Game.get();
        if (cur.lan && cur.phase != Main.Phase.OVER) {
            // only a seated player may tear down a running LAN game
            String body = new String(ex.getRequestBody().readAllBytes());
            if (seatFor(cur, jsonGetStr(body, "token")) == 0) {
                respond(ex, 403, "application/json", "{\"error\":\"Not a player in this game\"}"); return;
            }
        }
        Main.Game.reset();
        respond(ex, 200, "application/json", "{\"ok\":true}");
    }

    // ═════════════════════════════════════════════════════════
    //  LAN MULTIPLAYER  (host = seat 1, guest = seat 2)
    // ═════════════════════════════════════════════════════════

    // Candidate IPv4 addresses of this machine, best guess first
    // (home Wi-Fi 192.168.x.x before 10.x before 172.x virtual adapters).
    static List<String> lanAddresses() {
        List<String> out = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) out.add(a.getHostAddress());
            }
        } catch (Exception e) { /* fall through to localhost */ }
        out.sort(Comparator.comparingInt(ip -> ip.startsWith("192.168.") ? 0 : ip.startsWith("10.") ? 1 : 2));
        if (out.isEmpty()) out.add("localhost");
        return out;
    }

    static int seatFor(Main.Game g, String token) {
        if (token == null) return 0;
        if (token.equals(g.tok1)) return 1;
        if (token.equals(g.tok2)) return 2;
        return 0;
    }

    static String queryParam(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getRawQuery();
        if (q == null) return null;
        for (String kv : q.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0 && kv.substring(0, i).equals(key))
                return URLDecoder.decode(kv.substring(i + 1), java.nio.charset.StandardCharsets.UTF_8);
        }
        return null;
    }

    static int readPid(String body, String key) {
        int pid = jsonGetInt(body, key, 0);
        return (pid < 0 || pid >= Main.Constants.NUM_CREATURE) ? 0 : pid;
    }

    static int[] readMoves(String body, String key) {
        int[] m = {0, 1, 2, 3};
        jsonGetArr4(body, key, m);
        for (int j = 0; j < 4; j++)
            if (m[j] < 0 || m[j] >= Main.Constants.MOVES_TOTAL) m[j] = j;
        return m;
    }

    // ─── GET /api/lan/info ───────────────────────────────────────
    static void handleLanInfo(HttpExchange ex) throws IOException {
        Main.Game g = Main.Game.get();
        List<String> ips = lanAddresses();
        StringBuilder sb = new StringBuilder("{\"port\":" + PORT + ",\"ip\":\"" + ips.get(0) + "\",\"ips\":[");
        for (int i = 0; i < ips.size(); i++) sb.append(i > 0 ? "," : "").append("\"").append(ips.get(i)).append("\"");
        sb.append("],\"lobby\":").append(g.lan && g.phase == Main.Phase.LOBBY).append("}");
        respond(ex, 200, "application/json", sb.toString());
    }

    // ─── POST /api/lan/host ──────────────────────────────────────
    // Body: {pid0, moves0}. Opens a lobby and seats the caller as player 1.
    static void handleLanHost(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) {
            respond(ex, 405, "application/json", "{\"error\":\"Method not allowed\"}"); return;
        }
        Main.Game old = Main.Game.get();
        long now = System.currentTimeMillis();
        boolean live = old.lan && old.phase == Main.Phase.BATTLE
                    && now - Math.max(old.seen1, old.seen2) < 30000;   // someone is still polling
        if (live) { respond(ex, 409, "application/json", "{\"error\":\"A LAN game is already in progress\"}"); return; }

        String body = new String(ex.getRequestBody().readAllBytes());
        Main.Game.reset();
        Main.Game g = Main.Game.get();
        g.lan    = true;
        g.player = new Main.Party(1);
        g.enemy  = new Main.Party(1);
        g.player.slots[0] = Main.FighterFactory.create(readPid(body, "pid0"), readMoves(body, "moves0"));
        g.tok1  = UUID.randomUUID().toString();
        g.seen1 = now;
        g.phase = Main.Phase.LOBBY;
        respond(ex, 200, "application/json", "{\"ok\":true,\"seat\":1,\"token\":\"" + g.tok1 + "\"}");
    }

    // ─── POST /api/lan/join ──────────────────────────────────────
    // Body: {pid0, moves0}. Fills seat 2 and starts the battle.
    static void handleLanJoin(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) {
            respond(ex, 405, "application/json", "{\"error\":\"Method not allowed\"}"); return;
        }
        Main.Game g = Main.Game.get();
        if (!g.lan || g.phase != Main.Phase.LOBBY) {
            respond(ex, 409, "application/json", "{\"error\":\"No game is waiting for a player\"}"); return;
        }
        String body = new String(ex.getRequestBody().readAllBytes());
        g.enemy.slots[0] = Main.FighterFactory.create(readPid(body, "pid0"), readMoves(body, "moves0"));
        g.tok2  = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        g.seen1 = now; g.seen2 = now;
        g.phase = Main.Phase.BATTLE;
        g.log.append("[START] Battle begins: ")
             .append(g.player.slots[0].name).append(" VS ").append(g.enemy.slots[0].name).append("!\n");
        respond(ex, 200, "application/json", "{\"ok\":true,\"seat\":2,\"token\":\"" + g.tok2 + "\"}");
    }

    // ─── POST /api/pick ──────────────────────────────────────────
    // Body: {token, move_index}. The first pick is held; when the second
    // arrives the round is resolved and g.round goes up by one.
    static void handlePostPick(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) {
            respond(ex, 405, "application/json", "{\"error\":\"Method not allowed\"}"); return;
        }
        Main.Game g = Main.Game.get();
        String body = new String(ex.getRequestBody().readAllBytes());
        int seat = g.lan ? seatFor(g, jsonGetStr(body, "token")) : 0;
        if (seat == 0) { respond(ex, 403, "application/json", "{\"error\":\"Not a player in this game\"}"); return; }
        if (g.phase != Main.Phase.BATTLE) { respond(ex, 400, "application/json", "{\"error\":\"No battle in progress\"}"); return; }

        int already = (seat == 1) ? g.pick1 : g.pick2;
        if (already != Main.Game.NO_PICK) { respond(ex, 409, "application/json", "{\"error\":\"Already picked this round\"}"); return; }

        Main.Fighter mine = (seat == 1) ? g.player.slots[0] : g.enemy.slots[0];
        int mi = jsonGetInt(body, "move_index", 0);
        if (Main.BattleEngine.allPpEmpty(mine)) {
            mi = (seat == 2) ? -1 : 0;       // -1 = Struggle for the second seat; the engine handles seat 1 itself
        } else if (mi < 0 || mi >= Main.Constants.MOVES_BATTLE || mine.moves[mi].PP <= 0) {
            respond(ex, 400, "application/json", "{\"error\":\"Invalid move\"}"); return;
        }
        if (seat == 1) { g.pick1 = mi; g.seen1 = System.currentTimeMillis(); }
        else           { g.pick2 = mi; g.seen2 = System.currentTimeMillis(); }

        boolean resolved = false;
        if (g.pick1 != Main.Game.NO_PICK && g.pick2 != Main.Game.NO_PICK) {
            int a = g.pick1, b = g.pick2;
            g.pick1 = g.pick2 = Main.Game.NO_PICK;
            Main.BattleEngine.processRound(a, b);
            g.round++;
            resolved = true;
        }
        respond(ex, 200, "application/json", "{\"ok\":true,\"resolved\":" + resolved + "}");
    }

    // ─── JSON SERIALISERS ────────────────────────────────────────

    static String fighterToJson(Main.Fighter f) { return fighterToJson(f, true); }

    static String fighterToJson(Main.Fighter f, boolean showMoves) {
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
        for (int i = 0; showMoves && i < Main.Constants.MOVES_BATTLE; i++) {
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

    static String partyToJson(Main.Party party) { return partyToJson(party, true); }

    static String partyToJson(Main.Party party, boolean showMoves) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"active\":").append(party.active).append(",\"slots\":[");
        for (int i = 0; i < party.slots.length; i++) {
            if (i > 0) sb.append(",");
            if (party.slots[i] == null) { sb.append("null"); continue; }
            sb.append(fighterToJson(party.slots[i], showMoves));
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

    // Pulls a simple "key":"value" string (tokens only, no escapes)
    static String jsonGetStr(String body, String key) {
        String search = "\"" + key + "\":";
        int idx = body.indexOf(search);
        if (idx < 0) return null;
        int a = body.indexOf('"', idx + search.length());
        if (a < 0) return null;
        int b = body.indexOf('"', a + 1);
        return b < 0 ? null : body.substring(a + 1, b);
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