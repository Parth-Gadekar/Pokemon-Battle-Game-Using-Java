
public class Main{
    public class Constants{
    static final int NUM_CREATURE = 9;
    static final int MOVES_TOTAL  = 8;
    static final int MOVES_BATTLE = 4;
    static final int NUM_TYPES    = 9;
    static final int PARTY_SIZE   = 3;

    static final int T_NORMAL = 0;
    static final int T_FIRE   = 1;
    static final int T_WATER  = 2;
    static final int T_GRASS  = 3;
    static final int T_ELECTRIC = 4;
    static final int T_PSYCHIC  = 5;
    static final int T_DARK = 6;
    static final int T_ROCK = 7;
    static final int T_FIGHTING = 8;

    static final String [] TYPE_NAME = {
        "Normal", "Fire", "Water", "Grass", "Electric", "Psychic", "Dark", "Rock", "Fighting"
    };

    
    static final int[][] CHART = {
     //        NOR  FIR  WAT  GRS  ELC  PSY  DRK  ROC  FIG   <- Defenders
    /*NOR*/  { 10,  10,  10,  10,  10,  10,  10,   5,  10 },
    /*FIR*/  { 10,   5,   5,  20,  10,  10,  10,   5,  10 },
    /*WAT*/  { 10,  20,   5,   5,  10,  10,  10,  20,  10 },
    /*GRS*/  { 10,   5,  20,   5,  10,  10,  10,  20,  10 },
    /*ELC*/  { 10,  10,  20,   5,   5,  10,  10,  10,  10 },
    /*PSY*/  { 20,  10,  10,  10,  10,   5,   5,  10,  20 },
    /*DRK*/  { 10,  10,  10,  10,  10,  20,   5,  10,   5 },
    /*ROC*/  { 10,  20,  10,  10,  10,  10,  10,  10,   5 },
    /*FIG*/  { 20,  10,  10,  10,  10,   5,  20,  20,  10 }
    }; 
}

    enum Phase {IDLE, BATTLE, OVER}
    enum Status { NONE, BURN, PARALYSIS, SLEEP, POISON }

    static class MoveBlueprint{
        String name;
        int power, type, accuracy;
        String category;
        String effect;

        MoveBlueprint(String name, int power, int type, int accuaracy, String category, String effect){
            this.name = name;
            this.power = power;
            this.type = type;
            this.accuracy = accuaracy;
            this.category = category;
            this.effect = effect;
        }
    }

    static class Move {
        String name;
        int power, type, accuracy;
        String category;
        String effect;
        int PP, maxPP;

        Move(String name, int power, int type, int accuracy, String category, String effect){
            this.name = name;
            this.power = power;
            this.type = type;
            this.accuracy = accuracy;
            this.category = category;
            this.effect = effect;
            this.PP = this.maxPP = 5;
        }
    }

    static class Template{
        String name;
        int type, hp, attack, spAtk, defence, spDef, speed;
        MoveBlueprint[] moves;

        Template(String name, int type, int hp, int attack, int spAtk, int defence, int spDef, int speed,
                 MoveBlueprint[] moves){

            this.name    = name;    this.type    = type;
            this.hp      = hp;      this.attack  = attack;
            this.spAtk   = spAtk;   this.defence = defence;
            this.spDef   = spDef;   this.speed   = speed;
            this.moves   = moves;

        }
    }

    static class Fighter {
        String name;
        int creatureId, type;
        int hp, maxHP;
        int attack, spAtk, defence, spDef, speed;
        Status status = Status.NONE;
        int sleepTurns = 0;
        Move[] moves = new Move[Constants.MOVES_BATTLE];
    }

    static class Party {
        Fighter[] slots = new Fighter[Constants.PARTY_SIZE];
        int active = 0;
    }

    static class Game { 
        Phase phase = Phase.IDLE;
        Party party = new Party();
        Party enemy = new Party();
        int result = 0;
        boolean forceSwitch = false;
        StringBuilder log = new StringBuilder();

        private static Game instance;
        static Game get(){
            if (instance == null) instance = new Game();
            return instance;
        }

        static void reset() { instance = new Game(); }
    }

    static class CreatureData {
        private static MoveBlueprint mv(String name, int power, int type, int accuracy, String cat, String eff){
            return new MoveBlueprint(name, power, type, accuracy, cat, eff);
        }

        static final Template[] TEMPLATES = {

            new Template("Emberfox", Constants.T_FIRE, 480, 90, 85, 60, 65, 95,
            new MoveBlueprint[]{
                mv("Ember",         40,  Constants.T_FIRE, 100, "special", "burn"),
                mv("Incenerate",    60,  Constants.T_FIRE, 90, "special", null),
                mv("Flamethrower",  90,  Constants.T_FIRE, 85, "special", "burn"),
                mv("Fire Blast",    120, Constants.T_FIRE, 70, "special", "burn"),
                mv("Scratch",       40,  Constants.T_NORMAL, 100, "physical", null),
                mv("Quick Slash",   65,  Constants.T_NORMAL, 100, "physical", null),
                mv("Bite",          60,  Constants.T_DARK, 100, "physical", null),
                mv("Inferno",       140, Constants.T_FIRE, 50, "special", "burn"),
            }),

            new Template("Tidalfin", Constants.T_WATER, 520, 75, 90, 85, 95, 85, new MoveBlueprint[]{
                mv("Water Gun", 40, Constants.T_WATER, 100, "special", null),
                mv("Aqua Jet", 50, Constants.T_WATER, 100, "physical", null),
                mv("Surf", 90, Constants.T_WATER, 80, "special",null),
                mv("Hydro Pump", 110, Constants.T_WATER, 80, "special", null),
                mv("Tackle", 35, Constants.T_NORMAL, 100, "physical", null),
                mv("Aqua Tail", 75, Constants.T_WATER, 90, "physical", null),
                mv("Muddy Water", 85, Constants.T_WATER, 85, "special", null),
            }),

            new Template("Thornback", Constants.T_GRASS, 540, 70, 80, 90, 85, 65, new MoveBlueprint[]{
                mv("Vine Whip",   35,  Constants.T_GRASS, 100, "physical", null),
                mv("Razor Leaf",  55,  Constants.T_GRASS, 95, "physical", null),
                mv("Energy Ball", 90,  Constants.T_GRASS, 80, "special", null),
                mv("Leaf Storm",  130, Constants.T_GRASS, 65, "special", null),
                mv("Tackle",      35,  Constants.T_NORMAL, 100, "physical", null),
                mv("Headbutt",    70,  Constants.T_NORMAL, 100, "physical", null),
                mv("Needle Arm",  60,  Constants.T_GRASS,   95, "physical", "poison"),
                mv("Solar Beam",  120, Constants.T_GRASS,   70, "special",  null),
            }),

            new Template("Zapwing", Constants.T_ELECTRIC, 470, 80, 95, 60, 70, 115,
            new MoveBlueprint[]{
                mv("Thunder Shock", 40,  Constants.T_ELECTRIC, 100, "special",  "paralysis"),
                mv("Spark",         65,  Constants.T_ELECTRIC, 100, "physical", "paralysis"),
                mv("Thunderbolt",   90,  Constants.T_ELECTRIC,  90, "special",  "paralysis"),
                mv("Thunder",      150,  Constants.T_ELECTRIC,  60, "special",  "paralysis"),
                mv("Quick Attack",  40,  Constants.T_NORMAL,   100, "physical", null),
                mv("Wing Slash",    60,  Constants.T_NORMAL,   100, "physical", null),
                mv("Discharge",     80,  Constants.T_ELECTRIC,  90, "special",  "paralysis"),
                mv("Volt Crash",   100,  Constants.T_ELECTRIC,  80, "special",  "paralysis"),
            }),

            new Template("Mindweave", Constants.T_PSYCHIC, 490, 65, 110, 55, 100, 105,
            new MoveBlueprint[]{
                mv("Confusion",    50,  Constants.T_PSYCHIC, 100, "special",  null),
                mv("Psybeam",      65,  Constants.T_PSYCHIC, 100, "special",  null),
                mv("Psychic",      90,  Constants.T_PSYCHIC,  90, "special",  null),
                mv("Psystrike",   100,  Constants.T_PSYCHIC,  80, "physical", null),
                mv("Tackle",       35,  Constants.T_NORMAL,  100, "physical", null),
                mv("Swift",        60,  Constants.T_NORMAL,  100, "special",  null),
                mv("Shadow Ball",  80,  Constants.T_DARK,     90, "special",  null),
                mv("Future Sight",120,  Constants.T_PSYCHIC,  70, "special",  "sleep"),
            }),

            new Template("Grimclaw", Constants.T_DARK, 510, 95, 75, 75, 70, 90,
            new MoveBlueprint[]{
                mv("Bite",         60,  Constants.T_DARK,   100, "physical", null),
                mv("Crunch",       80,  Constants.T_DARK,    95, "physical", null),
                mv("Dark Pulse",   80,  Constants.T_DARK,    95, "special",  null),
                mv("Night Daze",   90,  Constants.T_DARK,    90, "special",  null),
                mv("Scratch",      40,  Constants.T_NORMAL, 100, "physical", null),
                mv("Slash",        70,  Constants.T_NORMAL, 100, "physical", null),
                mv("Feint Attack", 60,  Constants.T_DARK,   100, "physical", null),
                mv("Payback",      50,  Constants.T_DARK,   100, "physical", null),
            }),

            new Template("Thedude", Constants.T_NORMAL, 480, 80, 70, 70, 70, 80,
            new MoveBlueprint[]{
                mv("Giga Impact",  130, Constants.T_NORMAL,  60, "physical", null),
                mv("Crunch",        80, Constants.T_DARK,    95, "physical", null),
                mv("Quick Attack",  50, Constants.T_NORMAL, 100, "physical", null),
                mv("Swift",         70, Constants.T_NORMAL, 100, "special",  null),
                mv("Scratch",       40, Constants.T_NORMAL, 100, "physical", null),
                mv("Slash",         70, Constants.T_NORMAL,  95, "physical", null),
                mv("Feint Attack",  60, Constants.T_DARK,   100, "physical", null),
                mv("Mega Punch",    90, Constants.T_NORMAL,  80, "physical", null),
            }),

            new Template("Rockruff", Constants.T_ROCK, 580, 75, 55, 95, 85, 75,
            new MoveBlueprint[]{
                mv("Bite",             60,  Constants.T_DARK,     100, "physical", null),
                mv("Rock Slide",       75,  Constants.T_ROCK,      90, "physical", null),
                mv("Rock Smash",       40,  Constants.T_FIGHTING, 100, "physical", null),
                mv("High Horsepower",  90,  Constants.T_ROCK,      90, "physical", null),
                mv("Scratch",          40,  Constants.T_NORMAL,   100, "physical", null),
                mv("Stone Edge",      100,  Constants.T_ROCK,      70, "physical", null),
                mv("Accelerock",       50,  Constants.T_ROCK,     100, "physical", null),
                mv("Rock Throw",       60,  Constants.T_ROCK,      90, "physical", null),
            }),

            new Template("Elmacho", Constants.T_FIGHTING, 500, 110, 70, 60, 60, 85,
            new MoveBlueprint[]{
                mv("Mega Punch",    60,  Constants.T_FIGHTING, 100, "physical", null),
                mv("Rock Smash",    80,  Constants.T_FIGHTING,  95, "physical", null),
                mv("Aura Sphere",   80,  Constants.T_FIGHTING,  95, "special",  null),
                mv("Close Combat", 120,  Constants.T_FIGHTING,  65, "physical", null),
                mv("Scratch",       40,  Constants.T_NORMAL,   100, "physical", null),
                mv("Slash",         70,  Constants.T_NORMAL,   100, "physical", null),
                mv("Sucker Punch",  60,  Constants.T_DARK,     100, "physical", null),
                mv("Fire Punch",    75,  Constants.T_FIRE,     100, "physical", "burn"),
            }),
        };
    }

    static class FighterFactory{

        static Fighter create(int pid, int[] movesIds){
            Template t = CreatureData.TEMPLATES[pid];
            Fighter  f = new Fighter();

            f.name          = t.name;
            f.creatureId    = pid;
            f.type          = t.type;
            f.hp = f.maxHP  = t.hp;
            f.attack        = t.attack;
            f.spAtk         = t.spAtk;
            f.defence       = t.defence;
            f.spDef         = t.spDef;
            f.speed         = t.speed;

            for (int i = 0; i < Constants.MOVES_BATTLE; i++){
                MoveBlueprint mb = t.moves[movesIds[i]];
                f.moves[i] = new Move(mb.name, mb.power, mb.type, mb.accuracy, mb.category, mb.effect);
    
            }
            return f;
        }
    }

    static class BattleEngine {

        static void appendLog(String msg) { Game.get().log.append(msg);}
        // return : -2 no pp, -1 miss, 0 immune, 1 damage dealt

        static int calcDamage(Fighter atk, Fighter def, int mi){
            Move m = atk.moves[mi];
            if (m.PP <= 0) return -2;
            m.PP--;

            if ((int)(Math.random() * 100)>= m.accuracy) return -1;

            int te = Constants.CHART[m.type][def.type];
            if (te == 0) return 0;

            int atkStat = m.category.equals("special") ? atk.spAtk : atk.attack;
            int defStat = m.category.equals("special") ? def.spDef : def.defence;

            int dmg = m.power * atkStat/ (defStat * 2);

            if (m.type == atk.type) dmg *= 15/10;

            if (atk.status == Status.BURN && m.category.equals("physical")) dmg /= 2 ;

            dmg = dmg * te/10;
            return Math.max(1, dmg);
        }

        //return true if enemy faints
        static boolean doAttack(Fighter atk, Fighter def, int mi){
            Move m = atk.moves[mi];

            if(atk.status == Status.PARALYSIS && Math.random() < 0.25){
                appendLog("[PAR] "+ atk.name + "is paralysed and can't move! \n");
                return false;
            }

            if(atk.status == Status.SLEEP){
                atk.sleepTurns--;
                if(atk.sleepTurns <= 0){
                    atk.status = Status.NONE;
                    appendLog("[WAKE] "+ atk.name + " woke up!\n");
                }
                else{
                    appendLog("[SLP] "+ atk.name + " is fast asleep!\n");
                    return false;
                }
            }

            int dmg = calcDamage(atk, def, mi);

            if (dmg == -2){
                appendLog("[NO PP] "+ atk.name + " can't use "+ m.name + "!\n");
                return false;
            }
            if ( dmg == -1){
                appendLog("[MISS] "+ atk.name + "used" + m.name + "... Missed! \n");
                return false;
            }
            if (dmg == 0){
                appendLog("[IMMUNE] "+ def.name + " is immune !\n");
                return false;
            }

            def.hp = Math.max(0, def.hp - dmg);

            int te = Constants.CHART[m.type][def.type];
            String eff = (te == 20) ? "SUPER EFFECTIVE" : (te == 5) ? " not very effective." : "";

            String stab = (m.type == atk.type) ? "[STAB] " : "";
            appendLog("[ATK] "+ atk.name + "->" + def.name + " : " + m.name + " " + dmg + " dmg!\n" + stab + eff + "\n");

            if(m.effect != null && def.status == Status.NONE){
                switch (m.effect){
                    case "burn":
                        def.status = Status.BURN;
                        appendLog("[STATUS] "+ def.name + " was burned!\n");
                        break;
                    case "paralysis":
                        def.status = Status.PARALYSIS;
                        appendLog("[STATUS] "+ def.name + " was paralysed!\n");
                        break;
                    case "poison":
                        def.status = Status.POISON;
                        appendLog("[STATUS] "+ def.name + " was poisoned!\n");
                        break;
                    case "sleep":
                        def.status = Status.SLEEP;
                        def.sleepTurns = 1 + (int)(Math.random() * 3);
                        appendLog("[STATUS] "+ def.name + " fell asleep!\n");
                        break;
                }
            }

            if (def.hp == 0){
                appendLog("[KO] " + def.name + " fainted!\n");
                return true;
            }


            return false;
        }

    }

}