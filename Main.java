public class Main {
    public static class Constants {

        public static final int NUM_CREATURE = 9;
        public static final int MOVES_TOTAL = 8;
        public static final int MOVES_BATTLE = 4;
        public static final int NUM_TYPE = 9;
        public static final int PARTY_SIZE = 3;

        public static final int T_NORMAL = 0;   
        public static final int T_FIRE = 1;
        public static final int T_WATER = 2;
        public static final int T_GRASS = 3;
        public static final int T_ELECTRIC = 4;
        public static final int T_PSYCHIC = 5;
        public static final int T_DARK = 6;
        public static final int T_ROCK = 7;
        public static final int T_FIGHTING = 8;
    }

    public static class TypeChart {

        public static final String[] TYPE_NAME = {
            "Normal", "Fire", "Water", "Grass", "Electric", "Psychic", "Dark", "Rock", "Fighting"
        };

        public static final int[][] CHART = {
                //  NOR  FIR  WAT  GRS  ELC  PSY  DRK  ROC  FIG   
            /*NOR*/ {10,  10,  10,  10,  10,  10,  10,   5,  10},
            /*FIR*/ {10,   5,   5,  20,  10,  10,  10,   5,  10},
            /*WAT*/ {10,  20,   5,   5,  10,  10,  10,  20,  10},
            /*GRS*/ {10,   5,  20,   5,  10,  10,  10,  20,  10},
            /*ELC*/ {10,  10,  20,   5,   5,  10,  10,  10,  10},
            /*PSY*/ {20,  10,  10,  10,  10,   5,   5,  10,  20},
            /*DRK*/ {10,  10,  10,  10,  10,  20,   5,  10,   5},
            /*ROC*/ {10,  20,  10,  10,  10,  10,  10,  10,   5},
            /*FIG*/ {20,  10,  10,  10,  10,   5,  20,  20,  10},
        };
    }

    public static class Move {
        String name;
        int power;
        int type;
        int accuracy;
        int PP, maxPP;

        public Move(String name, int power, int type, int accuracy){
            this.name = name;
            this.power = power;
            this.type = type;
            this.accuracy = accuracy;
            this.PP = this.maxPP = 5;
        }
    }

    public static class Fighter {
        String name;
        int creatureId, type;
        int HP, maxHP;
        int attack, defence;
        Move[] moves = new Move[Constants.MOVES_BATTLE];
    }

    public static class Template {
        String name;
        int type, HP, attack, defence;

        static class MoveBlueprint {
            String name;
            int power, type, accuracy;
            MoveBlueprint(String name,int power,int type,int accuracy){
                this.name = name;
                this.power = power;
                this.type = type;
                this.accuracy = accuracy;
            }
        }
        MoveBlueprint[] moves = new MoveBlueprint[Constants.MOVES_TOTAL];

        Template(String name, int type, int HP, int attack, int defence, MoveBlueprint[] moves){
            this.name = name;
            this.type = type;
            this.HP = HP;
            this.attack = attack;
            this.defence = defence;
            this.moves = moves;
        }
    }

    public static class Party {
        Fighter[] slots = new Fighter[Constants.PARTY_SIZE];
        int active = 0;
    }

    public enum Phase {IDLE, BATTLE, OVER}

    public static class Game {
        Phase phase = Phase.IDLE;
        Party player = new Party();
        Party enemy = new Party();
        int   result = 0;
        boolean forceSwitch = false;
        StringBuilder log = new StringBuilder();

        private static Game instance;
        public static Game get(){
            if (instance == null) instance = new Game();
            return instance;
        }
    }

    public static class PokemonData {

        public static final Template[] TEMPLATES = {

            new Template("Emberfox", Constants.T_FIRE, 480, 90, 60, new Template.MoveBlueprint[]{
                new Template.MoveBlueprint("Ember",40, Constants.T_FIRE, 100),
                new Template.MoveBlueprint("Flame Charge",50, Constants.T_FIRE, 100),
                new Template.MoveBlueprint("Fire Blast",120, Constants.T_FIRE, 85),
                new Template.MoveBlueprint("Quick Attack",40, Constants.T_NORMAL, 100),
                new Template.MoveBlueprint("Flame Wheel",60, Constants.T_FIRE, 100),
                new Template.MoveBlueprint("Inferno",100, Constants.T_FIRE, 50),
                new Template.MoveBlueprint("Tackle",30, Constants.T_NORMAL, 100),

                
                    
            } ),
        };
    }
    public static class Attack {
        
    }
}




