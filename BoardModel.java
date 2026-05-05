import java.util.ArrayList;
import java.util.List;

/**
 * Grade-C rules : turn switching, no kings (stacks instead), optional
 *                 captures (mandatory only when no other move exists),
 *                 stack-penalty mechanic on promotion.
 * Grade-B rules : kings (no stacks), MANDATORY captures, multi-jump
 *                 chaining, kings can move backward, 2-move minimax AI
 *                 for kings.
 * Grade-A rules : flying-king (any-distance diagonal jump), single
 *                 pieces may step horizontally/vertically as well as
 *                 diagonally, evaluateBoard() heuristic, 4-move minimax
 *                 for all pieces.
 *
 * Fixes vs. the previous version:
 *   - Level 1 captures are now permitted (spec: "the C-version will not
 *     REQUIRE a piece to jump if one is available — however if it is the
 *     only move possible, it must be taken").
 *   - "No legal move" win is now decided by piece count (spec) instead of
 *     defaulting to the opponent.
 *   - Stack height is preserved through captures and removals.
 *   - removeOpponentPiece now correctly decrements stack heights.
 *   - Penalty cell selection cannot be the cell that just promoted
 *     (otherwise the promoting player just removes their own newly-made
 *     stack — which makes no sense).  Spec implies opponent piece only,
 *     which we already enforced.
 */
public class BoardModel {

    /* State */

    private final CellCoordinate[][] board;
    private final int size;
    private final int level;          // 1=Beginner(C), 2=Intermediate(B), 3=Advanced(A)
    private int currentPlayer;        // 1=Red, 2=Black

    /** True when a stack has just been created and the player still owes a removal. */
    private boolean pendingPenalty;

    /** When >=0, the chained piece that MUST keep jumping until no more captures. */
    private int pendingJumpRow = -1;
    private int pendingJumpCol = -1;

    /* Construction */

    public BoardModel(int size, int level) {
        this.size  = size;
        this.level = level;
        this.board = new CellCoordinate[size][size];
        this.currentPlayer  = 1;     // Red moves first (standard checkers convention)
        this.pendingPenalty = false;
        initializeBoard();
    }

    private void initializeBoard() {
        // 8x8 → 3 rows of pieces per side; 10x10 → 4 rows.
        int rows = (size == 8) ? 3 : 4;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                boolean dark = (r + c) % 2 != 0;
                board[r][c] = new CellCoordinate(dark);
                if (!dark) continue;
                if (r < rows)              board[r][c].setStatus(CellCoordinate.BLACK_PIECE);
                else if (r >= size - rows) board[r][c].setStatus(CellCoordinate.RED_PIECE);
            }
        }
    }

    /* Move interface used by views */

    /**
     * Full validation for a player-attempted move.
     * Enforces turn ownership, multi-jump continuation, mandatory captures
     * (level 2+ unconditional, level 1 only when no non-capture is available),
     * and direction / distance rules.
     */
    public boolean isValidMove(int r1, int c1, int r2, int c2) {
        // Bounds
        if (r1 < 0 || r1 >= size || c1 < 0 || c1 >= size) return false;
        if (r2 < 0 || r2 >= size || c2 < 0 || c2 >= size) return false;
        // Destination must be empty, OR it must be the level-1 stack-growth
        // exception (a regular man landing on its own back-row stack).
        if (!canLandOn(r1, c1, r2, c2))                   return false;

        // Must move own piece
        if (!isOwnedBy(r1, c1, currentPlayer)) return false;

        // Multi-jump in progress: only that piece may move, and only by capturing.
        if (pendingJumpRow >= 0) {
            if (r1 != pendingJumpRow || c1 != pendingJumpCol) return false;
            return isValidCapture(r1, c1, r2, c2, currentPlayer);
        }

        // Mandatory capture rules differ by level:
        //  - Level 2/3 : if any capture exists for this player, only captures are legal.
        //  - Level 1   : captures are optional UNLESS no non-capture move exists.
        boolean anyCapture = !getAllCaptures(currentPlayer).isEmpty();
        if (anyCapture) {
            if (level >= 2) {
                return isValidCapture(r1, c1, r2, c2, currentPlayer);
            } else {
                // Level 1: captures are an OPTION.  But if there are NO regular
                // moves available anywhere on the board, the capture is forced.
                boolean anyRegular = hasAnyRegularMove(currentPlayer);
                if (!anyRegular) {
                    return isValidCapture(r1, c1, r2, c2, currentPlayer);
                }
                // Otherwise either capture or regular is fine.
                if (isValidCapture(r1, c1, r2, c2, currentPlayer)) return true;
                return isValidRegularMove(r1, c1, r2, c2);
            }
        }

        // No captures available → only regular moves
        return isValidRegularMove(r1, c1, r2, c2);
    }

    /**
     * Executes a pre-validated move.
     * @return true if the turn passed to the other player, false if the same
     *         player must continue (multi-jump or pending stack penalty).
     */
    public boolean executeMove(int r1, int c1, int r2, int c2) {
        boolean wasCapture = isCaptureMove(r1, c1, r2, c2);

        // Move the piece
        int srcStatus     = board[r1][c1].getStatus();
        int srcStackHeight = board[r1][c1].getStackHeight();
        // (A stack cannot move under our rules — but be safe: only a single piece moves.)
        boolean fromStack = (srcStatus == CellCoordinate.RED_STACK
                          || srcStatus == CellCoordinate.BLACK_STACK);

        if (fromStack) {
            // Stacks don't move in any current grade level; treat as no-op.
            // Defensive guard — should never reach here through isValidMove.
            return false;
        }

        // Level-1 stack growth: a regular man stepping (or jumping) onto its
        // own back-row stack adds itself to the stack rather than landing as
        // a fresh piece.  Handle this BEFORE overwriting the destination,
        // since setStatus(srcStatus) would clobber the existing stack.
        if (isLevel1StackGrowth(r1, c1, r2, c2)) {
            board[r2][c2].incrementStack();
            board[r1][c1].setStatus(CellCoordinate.EMPTY);
            if (wasCapture) removeCapturedPiece(r1, c1, r2, c2);
            // Every piece that joins a stack triggers another opponent removal,
            // matching the existing per-promotion penalty rule.
            pendingPenalty = true;
            pendingJumpRow = -1;
            pendingJumpCol = -1;
            return false; // hold turn until removeOpponentPiece() resolves it
        }

        board[r2][c2].setStatus(srcStatus);
        board[r1][c1].setStatus(CellCoordinate.EMPTY);

        // Remove the captured piece (or one piece off a captured stack).
        if (wasCapture) removeCapturedPiece(r1, c1, r2, c2);

        // Promotion check (king at level 2+, stack at level 1).
        boolean promoted = checkPromotion(r2, c2);

        // Level-1 stack penalty: hold the turn, let caller invoke removeOpponentPiece().
        if (pendingPenalty) {
            pendingJumpRow = -1;
            pendingJumpCol = -1;
            return false;
        }

        // After a capture (not interrupted by promotion at lvl 2+), check for chain.
        if (wasCapture && !promoted) {
            List<int[]> further = getCapturesForPiece(r2, c2, currentPlayer);
            if (!further.isEmpty()) {
                pendingJumpRow = r2;
                pendingJumpCol = c2;
                return false; // same player continues
            }
        }

        // End of turn.
        pendingJumpRow = -1;
        pendingJumpCol = -1;
        currentPlayer  = (currentPlayer == 1) ? 2 : 1;
        return true;
    }

    /* Win condition */

    /**
     * @return 1 if Red wins, 2 if Black wins, 0 if the game continues, 3 if drawn
     *         (equal pieces and current player has no moves — extremely rare).
     */
    public int checkWin() {
        int redCount = 0, blackCount = 0;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                int s = board[r][c].getStatus();
                int n = board[r][c].getPieceCount();
                if (s == CellCoordinate.RED_PIECE  || s == CellCoordinate.RED_KING
                 || s == CellCoordinate.RED_STACK)  redCount   += n;
                if (s == CellCoordinate.BLACK_PIECE || s == CellCoordinate.BLACK_KING
                 || s == CellCoordinate.BLACK_STACK) blackCount += n;
            }
        }
        if (blackCount == 0) return 1;
        if (redCount   == 0) return 2;

        // Spec: "no legal move possible → player with most pieces wins"
        if (getAllMoves(currentPlayer).isEmpty()) {
            if (redCount  > blackCount) return 1;
            if (blackCount > redCount)  return 2;
            return 3; // draw
        }
        return 0;
    }

    /* AI: minimax with alpha–beta pruning */

    /**
     * Returns the best [r1,c1,r2,c2] move for the current player.
     * Depth = 4 at level 3 (all pieces); depth = 2 at level 2 (Grade B).
     * At level 1 the AI is not used.
     */
    public int[] getAIMove() {
        int depth     = (level == 3) ? 4 : 2;
        boolean isMax = (currentPlayer == 1);          // Red maximises
        int[]  best   = null;
        int bestScore = isMax ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        List<int[]> moves = getAllMoves(currentPlayer);
        for (int[] move : moves) {
            BoardModel copy = deepCopy();
            copy.executeMove(move[0], move[1], move[2], move[3]);
            // Resolve any pending penalty in the copy (AI can't pause for human input).
            // Pick a deterministic opponent-piece to remove if needed.
            if (copy.isPendingPenalty()) copy.autoResolvePenalty();
            int score = copy.minimax(depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, !isMax);
            if (isMax ? (score > bestScore) : (score < bestScore)) {
                bestScore = score;
                best      = move;
            }
        }
        return best;
    }

    /** Pick any opponent piece for the level-1 stack-penalty resolution
     *  (used inside minimax copies and when the AI itself promotes). */
    private void autoResolvePenalty() {
        int foePlayer = (currentPlayer == 1) ? 2 : 1;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (isOwnedBy(r, c, foePlayer)) { removeOpponentPiece(r, c); return; }
            }
        }
        // No opponent pieces left — clear the flag so we don't deadlock.
        pendingPenalty = false;
        currentPlayer  = (currentPlayer == 1) ? 2 : 1;
    }

    private int minimax(int depth, int alpha, int beta, boolean maximizing) {
        int winner = checkWin();
        if (winner == 1) return  1000 + depth;   // prefer faster wins
        if (winner == 2) return -1000 - depth;
        if (winner == 3) return 0;               // draw

        if (depth == 0) return evaluateBoard();

        List<int[]> moves = getAllMoves(currentPlayer);
        if (moves.isEmpty()) return maximizing ? -1000 : 1000;

        if (maximizing) {
            int max = Integer.MIN_VALUE;
            for (int[] m : moves) {
                BoardModel copy = deepCopy();
                copy.executeMove(m[0], m[1], m[2], m[3]);
                if (copy.isPendingPenalty()) copy.autoResolvePenalty();
                int s = copy.minimax(depth - 1, alpha, beta, false);
                max   = Math.max(max, s);
                alpha = Math.max(alpha, s);
                if (beta <= alpha) break;
            }
            return max;
        } else {
            int min = Integer.MAX_VALUE;
            for (int[] m : moves) {
                BoardModel copy = deepCopy();
                copy.executeMove(m[0], m[1], m[2], m[3]);
                if (copy.isPendingPenalty()) copy.autoResolvePenalty();
                int s = copy.minimax(depth - 1, alpha, beta, true);
                min  = Math.min(min, s);
                beta = Math.min(beta, s);
                if (beta <= alpha) break;
            }
            return min;
        }
    }

    /**
     * Heuristic.  Positive = Red advantage.  Negative = Black advantage.
     * Material + small centre-column positional bonus.
     */
    public int evaluateBoard() {
        int score = 0;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                int s = board[r][c].getStatus();
                int n = board[r][c].getPieceCount();
                switch (s) {
                    case CellCoordinate.RED_PIECE:   score += 10 * n; break;
                    case CellCoordinate.RED_KING:    score += 16 * n; break;
                    case CellCoordinate.RED_STACK:   score += 13 * n; break;
                    case CellCoordinate.BLACK_PIECE: score -= 10 * n; break;
                    case CellCoordinate.BLACK_KING:  score -= 16 * n; break;
                    case CellCoordinate.BLACK_STACK: score -= 13 * n; break;
                }
                if (s != CellCoordinate.EMPTY) {
                    int centerBonus = (int) (2.0 - Math.abs(c - (size / 2.0 - 0.5)));
                    if (s == CellCoordinate.RED_PIECE || s == CellCoordinate.RED_KING)
                        score += centerBonus;
                    else if (s == CellCoordinate.BLACK_PIECE || s == CellCoordinate.BLACK_KING)
                        score -= centerBonus;
                }
            }
        }
        return score;
    }

    /* Move enumeration */

    /** All legal [r1,c1,r2,c2] capture moves available to {@code player}. */
    public List<int[]> getAllCaptures(int player) {
        List<int[]> caps = new ArrayList<>();
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++)
                if (isOwnedBy(r, c, player))
                    caps.addAll(getCapturesForPiece(r, c, player));
        return caps;
    }

    /** All legal [r1,c1,r2,c2] moves for {@code player}.  Captures included or
     *  forced according to current rules. */
    public List<int[]> getAllMoves(int player) {
        // Multi-jump in progress: only the chained piece may move.
        if (pendingJumpRow >= 0) {
            return getCapturesForPiece(pendingJumpRow, pendingJumpCol, player);
        }

        List<int[]> caps = getAllCaptures(player);

        if (level >= 2) {
            // Mandatory capture: if any exists, those are the ONLY legal moves.
            if (!caps.isEmpty()) return caps;
        } else {
            // Level 1: captures forced only when no regular move exists.
            if (!caps.isEmpty() && !hasAnyRegularMove(player)) return caps;
        }

        // Otherwise, captures + regular moves are both legal at level 1;
        // at level 2/3 we only reach here if there were no captures, so
        // regulars are the only option.
        List<int[]> moves = new ArrayList<>();
        if (level == 1) moves.addAll(caps);  // optional captures at level 1
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (!isOwnedBy(r, c, player)) continue;
                moves.addAll(getRegularMovesForPiece(r, c, player));
            }
        }
        return moves;
    }

    private boolean hasAnyRegularMove(int player) {
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (!isOwnedBy(r, c, player)) continue;
                if (!getRegularMovesForPiece(r, c, player).isEmpty()) return true;
            }
        }
        return false;
    }

    /* Stack penalty (Grade C) */

    public boolean isPendingPenalty() { return pendingPenalty; }

    /**
     * Resolves a pending stack-penalty by removing one of the opponent's pieces.
     * If the chosen cell is a stack, one piece is taken off the top instead of
     * obliterating the whole stack.
     * @return true on success; false if the chosen cell does not contain an
     *         opponent piece or the call is made when no penalty is pending.
     */
    public boolean removeOpponentPiece(int r, int c) {
        if (!pendingPenalty)                                   return false;
        if (r < 0 || r >= size || c < 0 || c >= size)          return false;
        int s = board[r][c].getStatus();
        boolean valid =
            (currentPlayer == 1 && (s == CellCoordinate.BLACK_PIECE
                                  || s == CellCoordinate.BLACK_KING
                                  || s == CellCoordinate.BLACK_STACK))
         || (currentPlayer == 2 && (s == CellCoordinate.RED_PIECE
                                  || s == CellCoordinate.RED_KING
                                  || s == CellCoordinate.RED_STACK));
        if (!valid) return false;

        if (s == CellCoordinate.RED_STACK || s == CellCoordinate.BLACK_STACK) {
            board[r][c].decrementStack();          // takes one off the top
        } else {
            board[r][c].setStatus(CellCoordinate.EMPTY);
        }
        pendingPenalty = false;
        currentPlayer  = (currentPlayer == 1) ? 2 : 1;
        return true;
    }

    /* Getters */

    public int getSize()                  { return size; }
    public int getLevel()                 { return level; }
    public int getCurrentPlayer()         { return currentPlayer; }
    public int getCellStatus(int r, int c){ return board[r][c].getStatus(); }
    public int getStackHeight(int r, int c){ return board[r][c].getStackHeight(); }

    public boolean hasPendingJump()       { return pendingJumpRow >= 0; }
    public int     getPendingJumpRow()    { return pendingJumpRow; }
    public int     getPendingJumpCol()    { return pendingJumpCol; }

    /** Total piece count on the board for the given player (stacks counted by height). */
    public int getPieceCountFor(int player) {
        int n = 0;
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++)
                if (isOwnedBy(r, c, player)) n += board[r][c].getPieceCount();
        return n;
    }

    /* Internal helpers */

    private boolean isOwnedBy(int r, int c, int player) {
        return isOwnPiece(board[r][c].getStatus(), player);
    }

    private boolean isOwnPiece(int s, int player) {
        if (player == 1)
            return s == CellCoordinate.RED_PIECE
                || s == CellCoordinate.RED_KING
                || s == CellCoordinate.RED_STACK;
        return s == CellCoordinate.BLACK_PIECE
            || s == CellCoordinate.BLACK_KING
            || s == CellCoordinate.BLACK_STACK;
    }

    private boolean isOpponentPiece(int s, int player) {
        return s != CellCoordinate.EMPTY && !isOwnPiece(s, player);
    }

    /**
     * True if a piece at (r1,c1) is permitted to land on (r2,c2).
     * Normally the destination must be empty.  The single exception is
     * Grade-C stack growth: a regular man reaching its own promotion row
     * may land on a same-color stack already there, adding itself to it.
     */
    private boolean canLandOn(int r1, int c1, int r2, int c2) {
        if (board[r2][c2].isEmpty()) return true;
        return isLevel1StackGrowth(r1, c1, r2, c2);
    }

    /**
     * Detects the Grade-C stack-growth move: at level 1 only, a regular
     * piece moving onto its own back-row stack to add itself to it.
     */
    private boolean isLevel1StackGrowth(int r1, int c1, int r2, int c2) {
        if (level != 1) return false;
        int src = board[r1][c1].getStatus();
        int dst = board[r2][c2].getStatus();
        if (src == CellCoordinate.RED_PIECE
            && dst == CellCoordinate.RED_STACK
            && r2 == 0)            return true;
        if (src == CellCoordinate.BLACK_PIECE
            && dst == CellCoordinate.BLACK_STACK
            && r2 == size - 1)     return true;
        return false;
    }

    /* Regular-move validation */
    private boolean isValidRegularMove(int r1, int c1, int r2, int c2) {
        int   status   = board[r1][c1].getStatus();
        boolean isKing = (status == CellCoordinate.RED_KING || status == CellCoordinate.BLACK_KING);
        boolean isStack = (status == CellCoordinate.RED_STACK || status == CellCoordinate.BLACK_STACK);

        // Stacks don't move (they're locked at the back row earning capture points only).
        if (isStack) return false;

        int rowDiff  = r2 - r1;
        int colDiff  = c2 - c1;
        int absRow   = Math.abs(rowDiff);
        int absCol   = Math.abs(colDiff);

        if (level == 3) {
            if (isKing) {
                // Flying king: any distance, any of 8 directions, path must be clear.
                boolean diagonal   = (absRow == absCol && absRow > 0);
                boolean horizontal = (absRow == 0 && absCol > 0);
                boolean vertical   = (absRow > 0 && absCol == 0);
                if (!diagonal && !horizontal && !vertical) return false;
                return isPathClear(r1, c1, r2, c2);
            } else {
                // Grade A: regular pieces single-step in all 8 directions.
                return (absRow <= 1 && absCol <= 1 && (absRow + absCol) > 0);
            }
        } else {
            // Levels 1 & 2: diagonal-only single step.
            if (absRow != 1 || absCol != 1) return false;
            if (isKing) return true;                  // kings move any diagonal direction
            return isForwardStep(r1, r2, status);     // regular pieces forward only
        }
    }

    private boolean isForwardStep(int r1, int r2, int status) {
        if (status == CellCoordinate.RED_PIECE)   return r2 < r1; // Red moves up
        if (status == CellCoordinate.BLACK_PIECE) return r2 > r1; // Black moves down
        return true; // kings handled elsewhere
    }

    /* Capture validation */
    private boolean isValidCapture(int r1, int c1, int r2, int c2, int player) {
        if (!canLandOn(r1, c1, r2, c2)) return false;

        int   status = board[r1][c1].getStatus();
        boolean isKing = (status == CellCoordinate.RED_KING || status == CellCoordinate.BLACK_KING);
        boolean isStack = (status == CellCoordinate.RED_STACK || status == CellCoordinate.BLACK_STACK);
        if (isStack) return false; // stacks cannot capture

        int   rowDiff = r2 - r1;
        int   colDiff = c2 - c1;
        int   absRow  = Math.abs(rowDiff);
        int   absCol  = Math.abs(colDiff);

        if (level == 3 && isKing) {
            // Grade-A flying-king capture: any direction (H, V, or D), any
            // distance, with exactly one opponent piece in the path.
            boolean diagonal   = (absRow == absCol && absRow >= 2);
            boolean horizontal = (absRow == 0 && absCol >= 2);
            boolean vertical   = (absCol == 0 && absRow >= 2);
            if (!diagonal && !horizontal && !vertical) return false;
            int steps = Math.max(absRow, absCol);
            int dr = (rowDiff == 0) ? 0 : rowDiff / absRow;
            int dc = (colDiff == 0) ? 0 : colDiff / absCol;
            int opps = 0;
            for (int i = 1; i < steps; i++) {
                int s = board[r1 + i * dr][c1 + i * dc].getStatus();
                if (s != CellCoordinate.EMPTY) {
                    if (isOpponentPiece(s, player)) { opps++; if (opps > 1) return false; }
                    else                            return false; // own piece in path
                }
            }
            return opps == 1;
        } else {
            // Two-square jump.  At level 3 it can be in any of the 8 directions
            // (Grade A: "all pieces ... horizontal and vertical as well as
            // diagonal").  At levels 1/2 it must be diagonal.
            boolean validShape;
            if (level == 3) {
                boolean diag2 = (absRow == 2 && absCol == 2);
                boolean horz2 = (absRow == 0 && absCol == 2);
                boolean vert2 = (absRow == 2 && absCol == 0);
                validShape = diag2 || horz2 || vert2;
            } else {
                validShape = (absRow == 2 && absCol == 2);
            }
            if (!validShape) return false;
            // Level 1/2 regular pieces must capture in their forward direction unless king.
            if (level <= 2 && !isKing && !isForwardStep(r1, r2, status)) return false;
            int dr = (rowDiff == 0) ? 0 : rowDiff / 2;
            int dc = (colDiff == 0) ? 0 : colDiff / 2;
            int mid = board[r1 + dr][c1 + dc].getStatus();
            return isOpponentPiece(mid, player);
        }
    }

    /** Determines whether an already-validated move IS a capture. */
    private boolean isCaptureMove(int r1, int c1, int r2, int c2) {
        int absRow = Math.abs(r2 - r1);
        int absCol = Math.abs(c2 - c1);
        // Standard 2-square jump shapes (diag, and at level 3 also H / V).
        if (absRow == 2 && absCol == 2) return true;
        if (level == 3) {
            if (absRow == 0 && absCol == 2) return true;
            if (absRow == 2 && absCol == 0) return true;
        }

        int status = board[r1][c1].getStatus();
        boolean isKing = (status == CellCoordinate.RED_KING || status == CellCoordinate.BLACK_KING);
        if (level == 3 && isKing) {
            // Flying-king long move: it's a capture iff it satisfies the
            // capture rules (i.e. there's exactly one opponent in the path).
            // Re-check via isValidCapture which is idempotent.
            boolean longShape = (absRow == absCol && absRow > 2)
                             || (absRow == 0 && absCol > 2)
                             || (absCol == 0 && absRow > 2);
            if (longShape) return isValidCapture(r1, c1, r2, c2, currentPlayer);
        }
        return false;
    }

    /* Capture enumeration for one piece */
    private List<int[]> getCapturesForPiece(int r, int c, int player) {
        List<int[]> caps = new ArrayList<>();
        int status = board[r][c].getStatus();
        if (status == CellCoordinate.EMPTY) return caps;
        if (status == CellCoordinate.RED_STACK || status == CellCoordinate.BLACK_STACK) return caps;

        boolean isKing = (status == CellCoordinate.RED_KING || status == CellCoordinate.BLACK_KING);

        if (level == 3 && isKing) {
            // Grade-A flying-king: scan all 8 directions (4 diagonals, 4 orthogonals)
            // for a single opponent to jump over.
            int[][] dirs = {{1,1},{1,-1},{-1,1},{-1,-1},{1,0},{-1,0},{0,1},{0,-1}};
            for (int[] d : dirs) {
                boolean foundOpp = false;
                for (int step = 1; step < size; step++) {
                    int nr = r + step * d[0];
                    int nc = c + step * d[1];
                    if (nr < 0 || nr >= size || nc < 0 || nc >= size) break;
                    int s = board[nr][nc].getStatus();
                    if (s == CellCoordinate.EMPTY) {
                        if (foundOpp) caps.add(new int[]{r, c, nr, nc});
                    } else if (isOpponentPiece(s, player)) {
                        if (foundOpp) break;             // can't jump two opponents
                        foundOpp = true;
                    } else {
                        break;                            // own piece blocks
                    }
                }
            }
        } else {
            // Standard 2-square jump.  Direction restricted for level 1/2 men;
            // at level 3 men can jump in any of 8 directions (Grade A).
            int[][] dirs;
            if (level == 3) {
                dirs = new int[][]{{2,2},{2,-2},{-2,2},{-2,-2},{2,0},{-2,0},{0,2},{0,-2}};
            } else if (isKing) {
                dirs = new int[][]{{2,2},{2,-2},{-2,2},{-2,-2}};
            } else {
                int fwd = (player == 1) ? -2 : 2;
                dirs = new int[][]{{fwd,2},{fwd,-2}};
            }
            for (int[] d : dirs) {
                int nr = r + d[0];
                int nc = c + d[1];
                if (nr < 0 || nr >= size || nc < 0 || nc >= size) continue;
                if (!canLandOn(r, c, nr, nc)) continue;
                int mr = r + d[0] / 2;
                int mc = c + d[1] / 2;
                if (isOpponentPiece(board[mr][mc].getStatus(), player))
                    caps.add(new int[]{r, c, nr, nc});
            }
        }
        return caps;
    }

    /* Regular-move enumeration for one piece */
    private List<int[]> getRegularMovesForPiece(int r, int c, int player) {
        List<int[]> moves = new ArrayList<>();
        int status = board[r][c].getStatus();
        if (status == CellCoordinate.RED_STACK || status == CellCoordinate.BLACK_STACK) return moves;
        boolean isKing = (status == CellCoordinate.RED_KING || status == CellCoordinate.BLACK_KING);

        if (level == 3) {
            if (isKing) {
                int[][] dirs = {{1,1},{1,-1},{-1,1},{-1,-1},{1,0},{-1,0},{0,1},{0,-1}};
                for (int[] d : dirs) {
                    for (int step = 1; step < size; step++) {
                        int nr = r + step * d[0];
                        int nc = c + step * d[1];
                        if (nr < 0 || nr >= size || nc < 0 || nc >= size) break;
                        if (!board[nr][nc].isEmpty()) break;
                        moves.add(new int[]{r, c, nr, nc});
                    }
                }
            } else {
                int[][] dirs = {{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
                for (int[] d : dirs) {
                    int nr = r + d[0]; int nc = c + d[1];
                    if (nr < 0 || nr >= size || nc < 0 || nc >= size) continue;
                    if (board[nr][nc].isEmpty()) moves.add(new int[]{r, c, nr, nc});
                }
            }
        } else {
            if (isKing) {
                int[][] dirs = {{1,1},{1,-1},{-1,1},{-1,-1}};
                for (int[] d : dirs) {
                    int nr = r + d[0]; int nc = c + d[1];
                    if (nr < 0 || nr >= size || nc < 0 || nc >= size) continue;
                    if (board[nr][nc].isEmpty()) moves.add(new int[]{r, c, nr, nc});
                }
            } else {
                int fwd = (player == 1) ? -1 : 1;
                for (int dc : new int[]{-1, 1}) {
                    int nr = r + fwd; int nc = c + dc;
                    if (nr < 0 || nr >= size || nc < 0 || nc >= size) continue;
                    if (canLandOn(r, c, nr, nc)) moves.add(new int[]{r, c, nr, nc});
                }
            }
        }
        return moves;
    }

    /* Execution helpers */

    private void removeCapturedPiece(int r1, int c1, int r2, int c2) {
        int rowDiff = r2 - r1;
        int colDiff = c2 - c1;
        int absRow  = Math.abs(rowDiff);
        int absCol  = Math.abs(colDiff);
        int steps   = Math.max(absRow, absCol);
        int dr = (rowDiff == 0) ? 0 : rowDiff / absRow;
        int dc = (colDiff == 0) ? 0 : colDiff / absCol;

        if (steps == 2) {
            int mr = r1 + dr;
            int mc = c1 + dc;
            removeOnePieceAt(mr, mc);
        } else {
            // Flying king: first opponent piece encountered along the path.
            for (int i = 1; i < steps; i++) {
                int mr = r1 + i * dr;
                int mc = c1 + i * dc;
                if (board[mr][mc].getStatus() != CellCoordinate.EMPTY) {
                    removeOnePieceAt(mr, mc);
                    break;
                }
            }
        }
    }

    /** Remove one piece at (r,c) — taking a single piece off a stack. */
    private void removeOnePieceAt(int r, int c) {
        int s = board[r][c].getStatus();
        if (s == CellCoordinate.RED_STACK || s == CellCoordinate.BLACK_STACK) {
            board[r][c].decrementStack();
        } else {
            board[r][c].setStatus(CellCoordinate.EMPTY);
        }
    }

    /** Promotes a piece that has reached its promotion row.  Returns true if promoted. */
    private boolean checkPromotion(int r, int c) {
        int s = board[r][c].getStatus();

        if (r == 0 && s == CellCoordinate.RED_PIECE) {
            if (level == 1) {
                board[r][c].setStatus(CellCoordinate.RED_STACK);
                pendingPenalty = true;
            } else {
                board[r][c].setStatus(CellCoordinate.RED_KING);
            }
            return true;
        }
        if (r == 0 && s == CellCoordinate.RED_STACK && level == 1) {
            // Already a stack, just add to it (height +1) and trigger penalty again.
            board[r][c].incrementStack();
            pendingPenalty = true;
            return true;
        }
        if (r == size - 1 && s == CellCoordinate.BLACK_PIECE) {
            if (level == 1) {
                board[r][c].setStatus(CellCoordinate.BLACK_STACK);
                pendingPenalty = true;
            } else {
                board[r][c].setStatus(CellCoordinate.BLACK_KING);
            }
            return true;
        }
        if (r == size - 1 && s == CellCoordinate.BLACK_STACK && level == 1) {
            board[r][c].incrementStack();
            pendingPenalty = true;
            return true;
        }
        return false;
    }

    /** True if all squares strictly between (r1,c1) and (r2,c2) are empty. */
    private boolean isPathClear(int r1, int c1, int r2, int c2) {
        int rowDiff = r2 - r1;
        int colDiff = c2 - c1;
        int absRow  = Math.abs(rowDiff);
        int absCol  = Math.abs(colDiff);
        int dr = (absRow == 0) ? 0 : rowDiff / absRow;
        int dc = (absCol == 0) ? 0 : colDiff / absCol;
        int steps = Math.max(absRow, absCol);
        for (int i = 1; i < steps; i++) {
            if (board[r1 + i * dr][c1 + i * dc].getStatus() != CellCoordinate.EMPTY)
                return false;
        }
        return true;
    }

    /* Deep copy for minimax */
    private BoardModel deepCopy() {
        BoardModel copy = new BoardModel(this.size, this.level);
        copy.currentPlayer  = this.currentPlayer;
        copy.pendingPenalty = this.pendingPenalty;
        copy.pendingJumpRow = this.pendingJumpRow;
        copy.pendingJumpCol = this.pendingJumpCol;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                copy.board[r][c] = new CellCoordinate(this.board[r][c].isAvailable());
                copy.board[r][c].setStatus(this.board[r][c].getStatus());
                // Restore stack height (setStatus resets it to 1 for stacks).
                int h = this.board[r][c].getStackHeight();
                while (copy.board[r][c].getStackHeight() < h) copy.board[r][c].incrementStack();
            }
        }
        return copy;
    }
}