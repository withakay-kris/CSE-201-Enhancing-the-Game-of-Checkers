import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Board — Swing GUI for the game (View / Controller).
 *
 * In the original code every game-logic decision was duplicated here in
 * tangled "is the king at the corner" branches that were inconsistent
 * with BoardModel.  Among the bugs that produced were:
 *
 *   - "(7,0) → (6,3) is legal" because of a stray '||' instead of '&&'
 *   - Red placed at the top in the GUI but at the bottom in BoardModel
 *   - Black moved first in the GUI but Red moved first in the model
 *   - Mandatory captures (level 2/3) never enforced in GUI
 *   - Flying-king + horizontal/vertical moves (level 3) never enforced
 *   - AI never invoked
 *   - Stack penalty (level 1) never handled
 *
 * This rewrite makes Board a thin view/controller: every rules question
 * is answered by BoardModel.isValidMove / executeMove / getAllMoves /
 * checkWin / removeOpponentPiece.  The board only handles selection
 * state, painting, AI scheduling, and the Help/Resign menu.
 */
public class Board extends JPanel {

    /* ----- Components ----- */
    private final BoardModel model;
    private final Tiles[][]  theBoard;
    private final int        ROW;
    private final int        COL;
    private final JLabel     statusLabel;

    /* ----- Selection state (view-only, not part of game rules) ----- */
    private int selectedRow = -1;
    private int selectedCol = -1;
    private List<int[]> legalDestinations = new ArrayList<>();

    /* ----- AI scheduling ----- */
    /** When true, the human is Red and AI plays Black at level 2/3.
     *  Level 1 has no AI requirement, so it stays human-vs-human. */
    private final boolean aiEnabled;

    /* ----- Menu ----- */
    private JMenuBar menuBar;

    public Board(BoardModel model) {
        this.model = model;
        this.ROW   = model.getSize();
        this.COL   = model.getSize();
        this.theBoard = new Tiles[ROW][COL];
        this.aiEnabled = (model.getLevel() >= 2);

        setLayout(new BorderLayout());

        // Status banner up top tells whose turn it is and the current rule mode.
        this.statusLabel = new JLabel(" ", JLabel.CENTER);
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(new Color(50, 50, 50));
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        add(statusLabel, BorderLayout.NORTH);

        // Game grid
        JPanel grid = new JPanel(new GridLayout(ROW, COL));
        int cell = (ROW >= 10) ? 56 : 70;
        grid.setPreferredSize(new Dimension(cell * COL, cell * ROW));
        Mouse mouseListener = new Mouse();
        for (int r = 0; r < ROW; r++) {
            for (int c = 0; c < COL; c++) {
                theBoard[r][c] = new Tiles(r, c, this, model);
                theBoard[r][c].addMouseListener(mouseListener);
                grid.add(theBoard[r][c]);
            }
        }
        add(grid, BorderLayout.CENTER);

        createMenu();
        refreshStatus();
        repaintAllTiles();
    }

    public JMenuBar getBoardMenuBar() { return menuBar; }

    /* ====================================================================
     *                        Click handling
     * ==================================================================== */

    /**
     * Two-click move protocol:
     *  1) First click on an own piece selects it (and shows legal destinations).
     *  2) Second click on a legal destination executes the move.
     *  Click on another own piece while one is selected → re-selects.
     *  Click on selected piece again → deselects.
     *
     * If a stack penalty is pending (level 1), clicks instead resolve the
     * penalty by removing one opponent piece.
     */
    public void handleClick(int row, int col) {
        if (model.checkWin() != 0) return;       // game already over

        // Stack-penalty mode: any click on an opponent piece resolves it.
        if (model.isPendingPenalty()) {
            if (!model.removeOpponentPiece(row, col)) {
                JOptionPane.showMessageDialog(this,
                        "Stack bonus: click one of the OPPONENT'S pieces to remove.",
                        "Choose an opponent piece", JOptionPane.WARNING_MESSAGE);
                return;
            }
            clearSelection();
            refreshStatus();
            repaintAllTiles();
            checkGameOverOrAI();
            return;
        }

        // Block input while it isn't the human's turn (during AI mode).
        if (aiEnabled && model.getCurrentPlayer() == 2 && !model.isPendingPenalty()) {
            return;
        }

        // Already selected → try to move there.
        if (selectedRow >= 0) {
            // Click selected source again → deselect.
            if (row == selectedRow && col == selectedCol) {
                clearSelection();
                repaintAllTiles();
                return;
            }
            // Click another own piece → re-select (only allowed when no chain in progress).
            if (isOwnedByCurrent(row, col) && !model.hasPendingJump()) {
                setSelection(row, col);
                repaintAllTiles();
                return;
            }
            // Otherwise treat as a move attempt.
            if (model.isValidMove(selectedRow, selectedCol, row, col)) {
                boolean turnSwitched = model.executeMove(selectedRow, selectedCol, row, col);
                if (!turnSwitched && model.hasPendingJump()) {
                    // Multi-jump — keep the same piece selected so the user
                    // can see and click the next destination.
                    setSelection(model.getPendingJumpRow(), model.getPendingJumpCol());
                } else {
                    clearSelection();
                }
                refreshStatus();
                repaintAllTiles();
                checkGameOverOrAI();
            } else {
                JOptionPane.showMessageDialog(this,
                        "Illegal move.  " + ruleHint(),
                        "Invalid move", JOptionPane.WARNING_MESSAGE);
            }
            return;
        }

        // No selection yet → must click an own piece.
        if (isOwnedByCurrent(row, col)) {
            // If a multi-jump is pending we may only select the chained piece.
            if (model.hasPendingJump() &&
               (row != model.getPendingJumpRow() || col != model.getPendingJumpCol())) {
                JOptionPane.showMessageDialog(this,
                        "You must continue jumping with the piece at ("
                        + model.getPendingJumpRow() + ","
                        + model.getPendingJumpCol() + ").",
                        "Multi-jump in progress", JOptionPane.WARNING_MESSAGE);
                return;
            }
            setSelection(row, col);
            repaintAllTiles();
        }
    }

    private boolean isOwnedByCurrent(int r, int c) {
        int s = model.getCellStatus(r, c);
        if (s == CellCoordinate.EMPTY) return false;
        boolean redCell = (s == CellCoordinate.RED_PIECE
                        || s == CellCoordinate.RED_KING
                        || s == CellCoordinate.RED_STACK);
        return (model.getCurrentPlayer() == 1) == redCell;
    }

    private void setSelection(int r, int c) {
        selectedRow = r;
        selectedCol = c;
        // Compute legal destinations for highlight: try every cell.
        legalDestinations.clear();
        for (int rr = 0; rr < ROW; rr++)
            for (int cc = 0; cc < COL; cc++)
                if (model.isValidMove(r, c, rr, cc))
                    legalDestinations.add(new int[]{rr, cc});
    }

    private void clearSelection() {
        selectedRow = -1;
        selectedCol = -1;
        legalDestinations.clear();
    }

    public boolean isSelected(int r, int c) {
        return r == selectedRow && c == selectedCol;
    }

    public boolean isLegalDestination(int r, int c) {
        for (int[] d : legalDestinations) if (d[0] == r && d[1] == c) return true;
        return false;
    }

    /* AI / game-over flow */

    private void checkGameOverOrAI() {
        int winner = model.checkWin();
        if (winner != 0) { announceWinner(winner); return; }

        // If AI's turn, schedule its move.  Use a Swing Timer so we don't
        // freeze the EDT during minimax on bigger boards.
        if (aiEnabled && model.getCurrentPlayer() == 2) {
            Timer t = new Timer(250, (ActionEvent e) -> doAIMove());
            t.setRepeats(false);
            t.start();
        }
    }

    private void doAIMove() {
        if (model.checkWin() != 0) return;
        int[] aiMove = model.getAIMove();
        if (aiMove == null) {
            // AI has no legal moves — checkWin will detect this and end the game.
            announceWinner(model.checkWin());
            return;
        }

        // Highlight what the AI is doing.
        setSelection(aiMove[0], aiMove[1]);
        repaintAllTiles();

        boolean turnSwitched = model.executeMove(aiMove[0], aiMove[1], aiMove[2], aiMove[3]);

        // Resolve stack-penalty automatically (level 1 only — AI doesn't run there).
        // For levels 2+ this branch is unreachable since promotion creates a king.
        while (model.isPendingPenalty()) {
            // Pick first opponent piece deterministically (defensive fallback).
            outer:
            for (int r = 0; r < ROW; r++)
                for (int c = 0; c < COL; c++)
                    if (model.removeOpponentPiece(r, c)) break outer;
        }

        clearSelection();
        refreshStatus();
        repaintAllTiles();

        if (!turnSwitched && model.hasPendingJump()) {
            // AI continues its multi-jump.
            Timer t = new Timer(350, (ActionEvent e) -> doAIMove());
            t.setRepeats(false);
            t.start();
            return;
        }

        checkGameOverOrAI();
    }

    private void announceWinner(int winner) {
        String msg;
        if (winner == 3) {
            msg = "Game over — DRAW (equal piece counts and no legal moves).";
        } else {
            int redCnt   = model.getPieceCountFor(1);
            int blackCnt = model.getPieceCountFor(2);
            msg = (winner == 1 ? "RED" : "BLACK") + " wins!\n"
                + "Final counts — Red: " + redCnt + ", Black: " + blackCnt;
        }
        statusLabel.setText("GAME OVER — " + msg.split("\n")[0]);
        Window w = SwingUtilities.getWindowAncestor(this);
        JOptionPane.showMessageDialog(w, msg, "Game Over", JOptionPane.INFORMATION_MESSAGE);
        if (w != null) w.dispose();
    }

    /* ====================================================================
     *                    UI status / repaint helpers
     * ==================================================================== */

    private void refreshStatus() {
        StringBuilder sb = new StringBuilder();
        String who = (model.getCurrentPlayer() == 1) ? "RED" : "BLACK";
        sb.append("Turn: ").append(who);
        if (aiEnabled && model.getCurrentPlayer() == 2) sb.append(" (AI thinking...)");

        if (model.isPendingPenalty()) {
            sb.append("  —  STACK BONUS!  Click an opponent piece to remove.");
        } else if (model.hasPendingJump()) {
            sb.append("  —  Continue jumping with (")
              .append(model.getPendingJumpRow()).append(",")
              .append(model.getPendingJumpCol()).append(")");
        }
        sb.append("   |   Level ").append(model.getLevel())
          .append(" (").append(levelName(model.getLevel())).append(")")
          .append("   |   R: ").append(model.getPieceCountFor(1))
          .append("   B: ").append(model.getPieceCountFor(2));
        statusLabel.setText(sb.toString());
    }

    private String levelName(int level) {
        switch (level) {
            case 1: return "Beginner";
            case 2: return "Intermediate";
            case 3: return "Advanced";
            default: return "?";
        }
    }

    private String ruleHint() {
        switch (model.getLevel()) {
            case 1: return "Beginner: 1-square diagonal forward; jumps optional.";
            case 2: return "Intermediate: jumps are MANDATORY; kings move backward.";
            case 3: return "Advanced: men move 1 square in ANY direction; kings fly any distance; jumps mandatory.";
            default: return "";
        }
    }

    private void repaintAllTiles() {
        for (int r = 0; r < ROW; r++)
            for (int c = 0; c < COL; c++)
                theBoard[r][c].repaint();
    }

    /* ====================================================================
     *                              Menu
     * ==================================================================== */

    private void createMenu() {
        menuBar = new JMenuBar();
        JMenu menu = new JMenu("Game");
        JMenuItem help   = new JMenuItem("Help / Rules");
        JMenuItem resign = new JMenuItem("Resign");

        help.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                String body =
                    "CHECKERS\n\n"
                  + "GENERAL\n"
                  + "  • Pieces only occupy the dark squares.\n"
                  + "  • Red moves first.\n"
                  + "  • Click your piece, then click a legal destination.\n\n"
                  + "BEGINNER (Level 1)\n"
                  + "  • 8x8 board, 12 pieces, no kings.\n"
                  + "  • 1-square diagonal forward moves.\n"
                  + "  • Captures are OPTIONAL unless no other move exists.\n"
                  + "  • Reaching the last row → STACK and STACK BONUS:\n"
                  + "      pick any opponent piece to remove.\n\n"
                  + "INTERMEDIATE (Level 2)\n"
                  + "  • 8x8, 12 pieces, with kings.  No stacks.\n"
                  + "  • Captures are MANDATORY.\n"
                  + "  • Kings can move/capture diagonally backward.\n"
                  + "  • AI plays Black with a 2-move look-ahead for kings.\n\n"
                  + "ADVANCED (Level 3)\n"
                  + "  • 10x10, 20 pieces.\n"
                  + "  • Men move 1 square in ANY direction (incl. orthogonal).\n"
                  + "  • Kings fly any distance, jumping over single opponents.\n"
                  + "  • AI plays Black with a 4-move look-ahead.\n";
                JOptionPane.showMessageDialog(Board.this, body, "Help / Rules",
                                              JOptionPane.INFORMATION_MESSAGE);
            }
        });

        resign.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                String who = (model.getCurrentPlayer() == 1) ? "Red" : "Black";
                JOptionPane.showMessageDialog(Board.this,
                        who + " resigned.  Game over.",
                        "Resigned", JOptionPane.INFORMATION_MESSAGE);
                Window w = SwingUtilities.getWindowAncestor(Board.this);
                if (w != null) w.dispose();
            }
        });

        menu.add(help);
        menu.add(resign);
        menuBar.add(menu);
    }
}
