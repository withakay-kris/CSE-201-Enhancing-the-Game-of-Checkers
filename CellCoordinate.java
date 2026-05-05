/**
 * CellCoordinate — single board cell.
 *
 * Grade-C requirement: "a new class that defines the contents of a cell of
 * the board" with availability + occupancy state (red/black piece, king,
 * stack).
 *
 * Fix: added stackHeight so the level-1 stack penalty rule
 * "stacks can have many pieces" can actually be tracked.  Each piece stacked
 * onto a cell increments stackHeight; one removal decrements it and clears
 * the cell when it hits zero.
 */
public class CellCoordinate {

    /* Status constants (also used as raw board state by BoardModel) */
    public static final int EMPTY        = 0;
    public static final int RED_PIECE    = 1;
    public static final int BLACK_PIECE  = 2;
    public static final int RED_KING     = 3;
    public static final int BLACK_KING   = 4;
    public static final int RED_STACK    = 5;
    public static final int BLACK_STACK  = 6;

    private final boolean isAvailable;   // dark squares only
    private int status;
    private int stackHeight;             // meaningful only for stack cells

    public CellCoordinate(boolean isAvailable) {
        this.isAvailable = isAvailable;
        this.status      = EMPTY;
        this.stackHeight = 0;
    }

    public boolean isAvailable() { return isAvailable; }
    public int     getStatus()   { return status; }
    public boolean isEmpty()     { return this.status == EMPTY; }

    public void setStatus(int status) {
        this.status = status;
        if (status == EMPTY) {
            this.stackHeight = 0;
        } else if (status == RED_STACK || status == BLACK_STACK) {
            // Becoming a stack — start at 1 if not already a stack.
            if (this.stackHeight == 0) this.stackHeight = 1;
        } else {
            this.stackHeight = 1;
        }
    }

    /** Add one piece to a stack cell.  Caller must ensure status is a stack. */
    public void incrementStack() {
        if (status == RED_STACK || status == BLACK_STACK) stackHeight++;
    }

    /** Remove one piece from a stack cell.  Empties the cell if it was the last. */
    public void decrementStack() {
        if (status == RED_STACK || status == BLACK_STACK) {
            stackHeight--;
            if (stackHeight <= 0) {
                stackHeight = 0;
                status = EMPTY;
            }
        }
    }

    /**
     * Number of pieces this cell counts as for the win-by-piece-count rule.
     * Empty = 0, regular/king = 1, stack = stackHeight.
     */
    public int getPieceCount() {
        if (status == EMPTY) return 0;
        if (status == RED_STACK || status == BLACK_STACK) return Math.max(1, stackHeight);
        return 1;
    }

    public int getStackHeight() { return stackHeight; }
}
