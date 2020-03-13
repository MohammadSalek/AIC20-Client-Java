package Client;

import Client.Model.*;

import java.util.*;
import java.util.Map;

/**
 * You must put your code in this class {@link AI}.
 * This class has {@link #pick}, to choose units before the start of the game;
 * {@link #turn}, to do orders while game is running;
 * {@link #end}, to process after the end of the game;
 */

public class AI {
    private Path mainPath;
    private Path otherPath;
    private King myKing;
    private Player myself;
    private int myId;
    private int allyId;
    private Player whoAmIFightingWith;
    private boolean isFightingFirstEnemy;
    private List<Integer> exceptionUnitIds;

    private static int startTurnToSendUnitsToOther = 10;
    private static int turnNumToSendUnit = 32;
    private static int minEnemyNumForPoison = 5;
    private static int minEnemyNumForDamage = 5;

    boolean isFirstEnemyClosest(World world) {
        Path pathToFirstEnemy = world.getShortestPathToCell(myself, world.getFirstEnemy().getKing().getCenter());
        Path pathToSecondEnemy = world.getShortestPathToCell(myself, world.getSecondEnemy().getKing().getCenter());
        return pathToFirstEnemy.getCells().size() < pathToSecondEnemy.getCells().size();
    }

    Unit getOurUnitWithMostUnitsAround(World world) {
        Unit res = null;
        int maxUnitsNum = 0;
        List<Unit> ourUnits = new ArrayList<>();
        ourUnits.addAll(world.getMe().getUnits());
        ourUnits.addAll(world.getFriend().getUnits());
        for (Unit unit : ourUnits) {
            int tmpNum = myUnitsAroundAndInCell(world, unit.getCell());
            if (tmpNum > maxUnitsNum) {
                res = unit;
                maxUnitsNum = tmpNum;
            }
        }
        return res;
    }

    int myUnitsAroundAndInCell(World world, Cell cell) {
        int res = 0;
        for (int i = -1; i <= +1; ++i) {
            for (int j = -1; j <= +1; ++j) {
                int row = cell.getRow() + i;
                int col = cell.getCol() + j;
                Cell tmpCell = world.getMap().getCell(row, col);
                if (tmpCell != null) {
                    for (Unit unit : myself.getUnits()) {
                        if (tmpCell.getUnits().contains(unit)) {
                            ++res;
                        }
                    }
                }
            }
        }
        return res;
    }

    int distanceFromKing(Unit unit, King king) {
        int r = Math.abs(king.getCenter().getRow() - unit.getCell().getRow());
        int c = Math.abs(king.getCenter().getCol() - unit.getCell().getCol());
        return r + c;
    }

    List<Unit> getLowestHpUnits(List<Unit> units) {
        List<Unit> res = new ArrayList<>(units);
        res.sort(Comparator.comparingInt(o -> (o.getBaseUnit().getMaxHp() - o.getHp())));
        return res;
    }

    Unit closestUnitFromKing(List<Unit> units, King king) {
        Unit closestUnit = null;
        int minDist = Integer.MAX_VALUE;
        for (Unit u : units) {
            int tmpDist = distanceFromKing(u, king);
            if (tmpDist < minDist) {
                closestUnit = u;
                minDist = tmpDist;
            }
        }
        return closestUnit;
    }

    Unit furthestUnitFromKing(List<Unit> units, King king) {
        Unit furthestUnit = null;
        int maxDist = -1;
        for (Unit u : units) {
            int tmpDist = distanceFromKing(u, king);
            if (tmpDist > maxDist) {
                furthestUnit = u;
                maxDist = tmpDist;
            }
        }
        return furthestUnit;
    }

    Cell getCellWithMostEnemiesInArea(World world, int radius) {
        Cell res = null;
        int maxEnemies = 0;
        List<Unit> enemies = new ArrayList<>();
        enemies.addAll(world.getFirstEnemy().getUnits());
        enemies.addAll(world.getSecondEnemy().getUnits());
        for (Unit enemyUnit : enemies) {
            Cell enemyCell = enemyUnit.getCell();
            int enemiesFound = 0;
            for (int i = -radius; i <= radius; ++i) {
                for (int j = -radius; j <= radius; ++j) {
                    Cell tmpCell = world.getMap().getCell(enemyCell.getRow() + i, enemyCell.getCol() + j);
                    for (Unit u : tmpCell.getUnits()) {
                        if (u.getPlayerId() != myId && u.getPlayerId() != allyId) {
                            ++enemiesFound;
                        }
                    }
                }
            }
            if (enemiesFound > maxEnemies) {
                maxEnemies = enemiesFound;
                res = enemyCell;
            }
        }
        return res;
    }

    Cell getCellWithLowestUnitHp(World world, Player myself, int radius) {
        Cell res = null;
        int maxAreaLostHpNum = 0;
        List<Unit> ourUnits = new ArrayList<>();
        ourUnits.addAll(myself.getUnits());
        ourUnits.addAll(world.getFriend().getUnits());
        for (Unit u : ourUnits) {
            int tmpAreaLostHp = 0;
            for (int i = -radius; i <= radius; ++i) {
                for (int j = -radius; j <= radius; ++j) {
                    int row = u.getCell().getRow() + i;
                    int col = u.getCell().getCol() + j;
                    List<Unit> unitsInCell = world.getMap().getCell(row, col).getUnits();
                    for (Unit cellUnit : unitsInCell) {
                        if (cellUnit.getPlayerId() == myId || cellUnit.getPlayerId() == allyId) {
                            tmpAreaLostHp += (cellUnit.getBaseUnit().getMaxHp() - cellUnit.getHp());
                        }
                    }
                }
            }
            if (tmpAreaLostHp > maxAreaLostHpNum) {
                res = u.getCell();
                maxAreaLostHpNum = tmpAreaLostHp;
            }
        }
        return res;
    }

    public void pick(World world) {
        System.out.println("pick started");
        myself = world.getMe();
        myKing = myself.getKing();
        myId = myself.getPlayerId();
        allyId = world.getFriend().getPlayerId();

        // Stupid Units:
        exceptionUnitIds = new ArrayList<>();
        exceptionUnitIds.add(3);
        exceptionUnitIds.add(4);

        // First Deck:
        List<BaseUnit> units = new ArrayList<>(world.getAllBaseUnits());
        units.sort(Comparator.comparingInt(BaseUnit::getBaseRange));
        Collections.reverse(units);
        world.chooseHand(units);

        // Find Closest enemy:
        isFightingFirstEnemy = isFirstEnemyClosest(world);
        if (isFightingFirstEnemy) {
            whoAmIFightingWith = world.getFirstEnemy();
        } else {
            whoAmIFightingWith = world.getSecondEnemy();
        }
    }

    public void turn(World world) {
        System.out.println("turn started: " + world.getCurrentTurn());

        /*
         * PLAYER
         */
        myself = world.getMe(); // for some unknown stupid reasons I had to write these lines here to run every turn:
        myKing = myself.getKing();
        myId = myself.getPlayerId();
        allyId = world.getFriend().getPlayerId();

        // whoamifightingwith and paths:
        if (whoAmIFightingWith.getPlayerId() == world.getFirstEnemy().getPlayerId() && !world.getFirstEnemy().isAlive()) {
            isFightingFirstEnemy = false;
        }
        else if (whoAmIFightingWith.getPlayerId() == world.getSecondEnemy().getPlayerId() && !world.getSecondEnemy().isAlive()) {
            isFightingFirstEnemy = true;
        }
        if (isFightingFirstEnemy) {
            whoAmIFightingWith = world.getFirstEnemy();
            if (world.getSecondEnemy().isAlive()) {
                otherPath = world.getShortestPathToCell(myself, world.getSecondEnemy().getKing().getCenter());
            } else {
                otherPath = null;
            }
        } else {
            whoAmIFightingWith = world.getSecondEnemy();
            if (world.getFirstEnemy().isAlive()) {
                otherPath = world.getShortestPathToCell(myself, world.getFirstEnemy().getKing().getCenter());
            } else {
                otherPath = null;
            }
        }
        mainPath = world.getShortestPathToCell(myself, whoAmIFightingWith.getKing().getCenter());

        /*
         * UNITS:
         */
        List<BaseUnit> units = new ArrayList<>(world.getMe().getHand());
        units.sort(Comparator.comparingInt(BaseUnit::getAp));

        // deploy units to defend my king:
        Unit kingTarget = myKing.getTarget();
        if (kingTarget != null) {
            System.out.println("enemyId: " + (kingTarget.getPlayerId()) + ", whoamifightingwithId:" + whoAmIFightingWith.getPlayerId());
            int enemyId = kingTarget.getPlayerId();
            Path defendPath;
            if (whoAmIFightingWith.getPlayerId() == enemyId) {
                defendPath = otherPath;
            } else {
                defendPath = mainPath;
            }
            if (defendPath != null) {
                for (BaseUnit u : units) {
                    if (!exceptionUnitIds.contains(u.getTypeId())) {
                        world.putUnit(u, defendPath);
                    }
                }
            }
        }

        // deploy units to 2nd enemy:
//        if (otherPath != null && myKing.getTarget() == null) {
//            if (world.getCurrentTurn() > startTurnToSendUnitsToOther) {
//                if (world.getCurrentTurn() % turnNumToSendUnit == 0) {
//                    for (BaseUnit u : units) {
//                        if (!exceptionUnitIds.contains(u.getTypeId())) {
//                            world.putUnit(u, otherPath);
//                            break;
//                        }
//                    }
//                }
//            }
//        }

        // deploy main units:
        for (BaseUnit u : units) {
            if (!exceptionUnitIds.contains(u.getTypeId())) {
                if (u.getAp() <= myself.getAp()){
                    world.putUnit(u, mainPath);
                }
            }
        }

        /*
         * SPELL:
         */
        for (Spell spell : myself.getSpells()) {
            if (spell != null) {
                if (spell.isAreaSpell()) {
                    switch (spell.getTarget()) {
                        case ALLIED: {
                            switch (spell.getType()) {
                                case HP: {
                                    List<Unit> ourUnits = new ArrayList<>();
                                    ourUnits.addAll(myself.getUnits());
                                    ourUnits.addAll(world.getFriend().getUnits());
                                    List<Unit> lowestHp = getLowestHpUnits(ourUnits);
                                    for (Unit unit : lowestHp) {
                                        Cell target = unit.getCell();
                                        if (target != null) {
                                            world.castAreaSpell(target, spell);
                                            break;
                                        }
                                    }
                                    break;
                                }
                                case HASTE: {
                                    Cell target = null;
                                    List<Unit> myUnits = myself.getUnits();
                                    if (!myUnits.isEmpty()) {
                                        Unit unit = getOurUnitWithMostUnitsAround(world);
                                        target = unit.getCell();
                                    }
                                    if (target != null) {
                                        world.castAreaSpell(target, spell);
                                    }
                                    break;
                                }
                                case DUPLICATE: {
                                    Cell target = null;
                                    List<Unit> ourUnits = new ArrayList<>();
                                    ourUnits.addAll(myself.getUnits());
                                    ourUnits.addAll(world.getFriend().getUnits());
                                    if (!ourUnits.isEmpty()) {
                                        for (Unit u : ourUnits) {
                                            if (u != null && (u.getTarget() != null || u.getTargetCell() != null)) {
                                                target = u.getCell();
                                                break;
                                            }
                                        }
                                    }
                                    if (target != null) {
                                        world.castAreaSpell(target, spell);
                                    }
                                    break;
                                }
                            }
                            break;
                        }
                        case ENEMY: {
                            switch (spell.getTypeId()) {
                                case 1: { // Damage:
                                    Cell target = getCellWithMostEnemiesInArea(world, 1);
                                    if (target != null) {
                                        if (world.getAreaSpellTargets(target, spell).size() >= minEnemyNumForDamage) {
                                            world.castAreaSpell(target, spell);
                                        }
                                    }
                                    break;
                                }
                                case 5: { // Poison:
                                    Cell target = getCellWithMostEnemiesInArea(world, 1);
                                    if (target != null) {
                                        if (world.getAreaSpellTargets(target, spell).size() >= minEnemyNumForPoison) {
                                            world.castAreaSpell(target, spell);
                                        }
                                    }
                                    break;
                                }
                            }
                            break;
                        }
                    }
                }
                else if (spell.isUnitSpell()) {
                    if (spell.getType().equals(SpellType.TELE)) {
                        Unit target = null;
                        List<Unit> myUnits = myself.getUnits();
                        List<Unit> lowestHp = getLowestHpUnits(myUnits);
                        if (!lowestHp.isEmpty()) {
                            for (Unit unit : lowestHp) {
                                if (unit.getHp() <= 1) {
                                    continue;
                                }
                                target = unit;
                                break;
                            }
                        }
                        if (target != null) {
                            world.castUnitSpell(target, target.getPath(), target.getCell(), spell);
                        }
                        break;
                    }
                }
            }
        }

        /*
         * UPGRADE:
         */
        if (!myself.getUnits().isEmpty()) {
            List<Unit> myUnits = myself.getUnits();
            myUnits.sort(Comparator.comparingInt(Unit::getRange));
            Collections.reverse(myUnits);
            for (Unit u : myUnits) {
                world.upgradeUnitRange(u);
            }
            myUnits.sort(Comparator.comparingInt(Unit::getAttack));
            Collections.reverse(myUnits);
            for (Unit u : myUnits) {
                world.upgradeUnitDamage(u);
            }
        }
    }

    public void end(World world, Map<Integer, Integer> scores) {
        System.out.println("end started");
        System.out.println("My score: " + scores.get(world.getMe().getPlayerId()));
    }
}