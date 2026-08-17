public class Main{

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

    
}