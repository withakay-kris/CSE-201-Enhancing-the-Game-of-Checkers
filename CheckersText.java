import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * Grade C fixes  : shows whose turn it is; enters a "remove opponent piece"
 *                  sub-loop when a stack penalty is pending.
 * Grade B/A adds : win-condition check after every move;
 *                  multi-jump notification; clean exit on game over.
 */

public class CheckersText {

    private final BoardModel model;

    private static final Map<Integer, String> SYMBOLS = new HashMap<>();
    static {
        SYMBOLS.put(CellCoordinate.EMPTY,       " . ");
        SYMBOLS.put(CellCoordinate.RED_PIECE,   " r ");
        SYMBOLS.put(CellCoordinate.BLACK_PIECE, " b ");
        SYMBOLS.put(CellCoordinate.RED_KING,    " R ");
        SYMBOLS.put(CellCoordinate.BLACK_KING,  " B ");
        SYMBOLS.put(CellCoordinate.RED_STACK,   "RS ");
        SYMBOLS.put(CellCoordinate.BLACK_STACK, "BS ");
    }

    public CheckersText(BoardModel model) {
        this.model = model;
    }


    public void start() {
        System.out.println("=== Checkers (Text Mode) — Level " + model.getLevel() + " ===");
        displayBoard();

        Scanner sc      = new Scanner(System.in);
        boolean running = true;

        while (running) {
            // Win check before each turn
            int winner = model.checkWin();
            if (winner != 0) {
                announceWinner(winner);
                break;
            }

            // Stack-penalty sub-loop (Grade C)
            if (model.isPendingPenalty()) {
                running = handleStackPenalty(sc);
                if (!running) break;
                displayBoard();
                continue;
            }

            // Announce whose turn it is
            String player = (model.getCurrentPlayer() == 1) ? "RED" : "BLACK";

            if (model.hasPendingJump()) {
                System.out.printf("%nPlayer %s — MULTI-JUMP REQUIRED from (%d,%d)!%n",
                        player, model.getPendingJumpRow(), model.getPendingJumpCol());
            } else {
                System.out.printf("%nPlayer %s's turn.%n", player);
            }

            System.out.println("Enter move (row1 col1 row2 col2) or 'exit':");
            String cmd = sc.nextLine().trim();

            if (cmd.equalsIgnoreCase("exit")) {
                running = false;
                System.out.println("Game terminated by player.");
                break;
            }

            // Parse and validate
            try {
                String[] parts = cmd.split("\\s+");
                int r1 = Integer.parseInt(parts[0]);
                int c1 = Integer.parseInt(parts[1]);
                int r2 = Integer.parseInt(parts[2]);
                int c2 = Integer.parseInt(parts[3]);

                if (!model.isValidMove(r1, c1, r2, c2)) {
                    System.err.println("Invalid move — try again.");
                    continue;
                }

                boolean turnSwitched = model.executeMove(r1, c1, r2, c2);

                displayBoard();

                if (!turnSwitched && !model.isPendingPenalty() && model.hasPendingJump()) {
                    System.out.println("Capture made — you must continue jumping!");
                } else if (turnSwitched) {
                    System.out.println("Move accepted.");
                }

            } catch (Exception e) {
                System.err.println("Error: enter 4 numbers separated by spaces, e.g.  5 0 4 1");
            }
        }

        sc.close();
    }

    /*Stack-penalty sub-loop*/

    /**
     * Prompts the current player to pick one of the opponent's pieces to remove.
     * Loops until a valid opponent cell is chosen.
     *
     * @return false if the player typed "exit", true otherwise.
     */
    private boolean handleStackPenalty(Scanner sc) {
        String player = (model.getCurrentPlayer() == 1) ? "RED" : "BLACK";
        String foe    = (model.getCurrentPlayer() == 1) ? "BLACK" : "RED";
        System.out.printf("%nSTACK BONUS — Player %s created a stack!%n", player);
        System.out.printf("Choose any %s piece to remove (row col):%n", foe);
        displayBoard();

        while (true) {
            System.out.println("Enter row col of opponent piece to remove (or 'exit'):");
            String cmd = sc.nextLine().trim();
            if (cmd.equalsIgnoreCase("exit")) return false;

            try {
                String[] parts = cmd.split("\\s+");
                int r = Integer.parseInt(parts[0]);
                int c = Integer.parseInt(parts[1]);

                if (model.removeOpponentPiece(r, c)) {
                    System.out.println("Opponent piece removed.");
                    return true;
                } else {
                    System.err.println("Invalid selection — choose an opponent's piece.");
                }
            } catch (Exception e) {
                System.err.println("Error: enter row and column, e.g.  3 2");
            }
        }
    }

    /*Board display*/

    private void displayBoard() {
        int size = model.getSize();
        System.out.print("   ");
        for (int i = 0; i < size; i++) System.out.printf(" %2d", i);
        System.out.println();
        System.out.print("   ");
        for (int i = 0; i < size; i++) System.out.print("---");
        System.out.println();

        for (int r = 0; r < size; r++) {
            System.out.printf("%2d|", r);
            for (int c = 0; c < size; c++) {
                System.out.print(SYMBOLS.getOrDefault(model.getCellStatus(r, c), " ? "));
            }
            System.out.println();
        }
        System.out.println();
    }

    /*Win announcement*/

    private void announceWinner(int winner) {
        displayBoard();
        System.out.println("===========================================");
        System.out.println("  GAME OVER — " + (winner == 1 ? "RED" : "BLACK") + " WINS!");
        System.out.println("===========================================");
    }
}