import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * CheckersText — text-mode view of the same game.
 *
 *  - Levels 1/2/3 all share the same BoardModel rules as the GUI.
 *  - Level 2/3: AI plays Black automatically.
 *  - Level 1 stack-penalty resolution prompts the user to remove a piece.
 *  - Multi-jump chaining is announced and continues until completed.
 */
public class CheckersText {

    private final BoardModel model;
    private final boolean    aiEnabled;

    /** How a cell prints.  Stack heights append the count, e.g. "RS3". */
    private static final Map<Integer, String> SYMBOLS = new HashMap<>();
    static {
        SYMBOLS.put(CellCoordinate.EMPTY,       " . ");
        SYMBOLS.put(CellCoordinate.RED_PIECE,   " r ");
        SYMBOLS.put(CellCoordinate.BLACK_PIECE, " b ");
        SYMBOLS.put(CellCoordinate.RED_KING,    " R ");
        SYMBOLS.put(CellCoordinate.BLACK_KING,  " B ");
    }

    public CheckersText(BoardModel model) {
        this.model = model;
        this.aiEnabled = (model.getLevel() >= 2);
    }

    public void start() {
        System.out.println("=== Checkers (Text Mode) — Level "
                + model.getLevel() + " (" + levelName() + ") ===");
        if (aiEnabled) {
            System.out.println("You are RED.  The computer plays BLACK.");
        } else {
            System.out.println("Two-player mode (RED moves first).");
        }
        printRules();
        displayBoard();

        Scanner sc      = new Scanner(System.in);
        boolean running = true;

        while (running) {
            int winner = model.checkWin();
            if (winner != 0) {
                announceWinner(winner);
                break;
            }

            // AI's turn?
            if (aiEnabled && model.getCurrentPlayer() == 2) {
                doAIMove();
                displayBoard();
                continue;
            }

            // Stack-penalty (level 1).
            if (model.isPendingPenalty()) {
                running = handleStackPenalty(sc);
                if (!running) break;
                displayBoard();
                continue;
            }

            // Human input prompt.
            String player = (model.getCurrentPlayer() == 1) ? "RED" : "BLACK";
            if (model.hasPendingJump()) {
                System.out.printf("%nPlayer %s — MULTI-JUMP REQUIRED from (%d,%d)!%n",
                        player, model.getPendingJumpRow(), model.getPendingJumpCol());
            } else {
                System.out.printf("%nPlayer %s's turn.%n", player);
            }

            System.out.println("Enter move (row1 col1 row2 col2) or 'help' / 'exit':");
            String cmd = sc.nextLine().trim();
            if (cmd.isEmpty()) continue;
            if (cmd.equalsIgnoreCase("exit")) {
                System.out.println("Game terminated by player.");
                break;
            }
            if (cmd.equalsIgnoreCase("help")) { printRules(); continue; }

            try {
                String[] parts = cmd.split("\\s+");
                if (parts.length != 4) {
                    System.err.println("Need 4 numbers, e.g.  5 0 4 1");
                    continue;
                }
                int r1 = Integer.parseInt(parts[0]);
                int c1 = Integer.parseInt(parts[1]);
                int r2 = Integer.parseInt(parts[2]);
                int c2 = Integer.parseInt(parts[3]);

                if (!model.isValidMove(r1, c1, r2, c2)) {
                    System.err.println("Invalid move — try again.  ("
                            + ruleHint() + ")");
                    continue;
                }

                boolean turnSwitched = model.executeMove(r1, c1, r2, c2);
                displayBoard();

                if (model.isPendingPenalty()) {
                    // Loop will pick this up next iteration.
                } else if (!turnSwitched && model.hasPendingJump()) {
                    System.out.println("Capture made — you must continue jumping!");
                } else if (turnSwitched) {
                    System.out.println("Move accepted.");
                }

            } catch (NumberFormatException e) {
                System.err.println("Error: enter 4 integers separated by spaces, e.g.  5 0 4 1");
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
        sc.close();
    }

    /* ---------------- AI move (level 2/3) ---------------- */
    private void doAIMove() {
        System.out.println("\nComputer (BLACK) is thinking...");
        while (model.getCurrentPlayer() == 2 && model.checkWin() == 0) {
            int[] mv = model.getAIMove();
            if (mv == null) break;
            System.out.printf("Computer plays: (%d,%d) → (%d,%d)%n",
                    mv[0], mv[1], mv[2], mv[3]);
            boolean switched = model.executeMove(mv[0], mv[1], mv[2], mv[3]);
            if (!switched && model.hasPendingJump()) {
                continue; // multi-jump
            }
            if (model.isPendingPenalty()) {
                // Should not happen at level 2+, but defensive.
                for (int r = 0; r < model.getSize(); r++) {
                    for (int c = 0; c < model.getSize(); c++) {
                        if (model.removeOpponentPiece(r, c)) break;
                    }
                }
            }
            break;
        }
    }

    /* ---------------- Stack-penalty sub-loop ---------------- */
    private boolean handleStackPenalty(Scanner sc) {
        String player = (model.getCurrentPlayer() == 1) ? "RED" : "BLACK";
        String foe    = (model.getCurrentPlayer() == 1) ? "BLACK" : "RED";
        System.out.printf("%nSTACK BONUS — Player %s created a stack!%n", player);
        System.out.printf("Choose any %s piece to remove (row col):%n", foe);

        while (true) {
            System.out.println("Enter row col of opponent piece to remove (or 'exit'):");
            String cmd = sc.nextLine().trim();
            if (cmd.equalsIgnoreCase("exit")) return false;

            try {
                String[] parts = cmd.split("\\s+");
                if (parts.length != 2) {
                    System.err.println("Enter exactly two numbers, e.g.  3 2");
                    continue;
                }
                int r = Integer.parseInt(parts[0]);
                int c = Integer.parseInt(parts[1]);
                if (model.removeOpponentPiece(r, c)) {
                    System.out.println("Opponent piece removed.");
                    return true;
                } else {
                    System.err.println("Invalid selection — choose an opponent's piece.");
                }
            } catch (NumberFormatException e) {
                System.err.println("Error: enter row and column, e.g.  3 2");
            }
        }
    }

    /* ---------------- Board display ---------------- */
    private void displayBoard() {
        int size = model.getSize();
        System.out.print("    ");
        for (int i = 0; i < size; i++) System.out.printf(" %2d ", i);
        System.out.println();
        System.out.print("    ");
        for (int i = 0; i < size; i++) System.out.print("----");
        System.out.println();

        for (int r = 0; r < size; r++) {
            System.out.printf("%2d |", r);
            for (int c = 0; c < size; c++) {
                int s = model.getCellStatus(r, c);
                if (s == CellCoordinate.RED_STACK) {
                    System.out.printf("R%-2d ", model.getStackHeight(r, c));
                } else if (s == CellCoordinate.BLACK_STACK) {
                    System.out.printf("B%-2d ", model.getStackHeight(r, c));
                } else {
                    System.out.print(SYMBOLS.getOrDefault(s, " ? "));
                    System.out.print(" ");
                }
            }
            System.out.println();
        }
        System.out.printf("Pieces — Red: %d   Black: %d%n",
                model.getPieceCountFor(1), model.getPieceCountFor(2));
        System.out.println();
    }

    /* ---------------- Rules / win announcement ---------------- */
    private void printRules() {
        System.out.println("Coordinates are 0-indexed (top-left = 0,0).");
        System.out.println("Type a move as four numbers: row1 col1 row2 col2.");
        System.out.println("Type 'help' for a rule reminder, 'exit' to quit.");
        System.out.println("Rule for this level: " + ruleHint());
    }

    private String ruleHint() {
        switch (model.getLevel()) {
            case 1: return "Beginner — 1-square diagonal forward; jumps optional unless forced; promotion makes a STACK with bonus removal.";
            case 2: return "Intermediate — Mandatory jumps; kings move/capture diagonally backward.";
            case 3: return "Advanced — Men move 1 square in ANY direction; kings fly diagonally; mandatory jumps.";
            default: return "";
        }
    }

    private String levelName() {
        switch (model.getLevel()) {
            case 1: return "Beginner";
            case 2: return "Intermediate";
            case 3: return "Advanced";
            default: return "?";
        }
    }

    private void announceWinner(int winner) {
        displayBoard();
        System.out.println("===========================================");
        if (winner == 3) {
            System.out.println("  GAME OVER — DRAW (no moves, equal pieces).");
        } else {
            String name = (winner == 1) ? "RED" : "BLACK";
            System.out.println("  GAME OVER — " + name + " WINS!");
            System.out.printf("  Final piece counts — Red: %d, Black: %d%n",
                    model.getPieceCountFor(1), model.getPieceCountFor(2));
        }
        System.out.println("===========================================");
    }
}
