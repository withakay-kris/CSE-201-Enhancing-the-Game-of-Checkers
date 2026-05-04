import java.util.ArrayList;
import java.util.List;

/**
 * Grade C adds  : turn switching, stack-penalty flag wired properly.
 * Grade B adds   : capture/jump logic, mandatory-capture enforcement,
 *                  king backward movement, multi-jump chaining,
 *                  win-condition detection, 2-move minimax for kings.
 * Grade A adds   : flying-king (any-distance diagonal jump),
 *                  horizontal/vertical single-step moves for all pieces,
 *                  evaluateBoard() heuristic, 4-move minimax for all pieces.
 */

public class BoardModel {

    /*State*/

    private CellCoordinate[][] board;
    private final int size;
    private final int level;          // 1=Beginner(C)  2=Intermediate(B)  3=Advanced(A)
    private int currentPlayer;        // 1=Red  2=Black

    /** True after a stack promotion at level 1; caller must resolve before turn ends. */
    private boolean pendingPenalty;

    /** Non-negative when a piece must continue a multi-jump chain. */
    private int pendingJumpRow = -1;
    private int pendingJumpCol = -1;

    /*Construction*/

    public BoardModel(int size, int level) {
        this.size  = size;
        this.level = level;
        this.board = new CellCoordinate[size][size];
        this.currentPlayer  = 1;   // Red goes first
        this.pendingPenalty = false;
        initializeBoard();
    }

    private void initializeBoard() {
        int rows = (size == 8) ? 3 : 4;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                boolean available = (r + c) % 2 != 0;
                board[r][c] = new CellCoordinate(available);
                if (available) {
                    if (r < rows)            board[r][c].setStatus(CellCoordinate.BLACK_PIECE);
                    else if (r >= size - rows) board[r][c].setStatus(CellCoordinate.RED_PIECE);
                }
            }
        }
    }

    /*PUBLIC MOVE INTERFACE*/

    /**
     * Full validation for the human player's chosen move.
     * Enforces whose turn it is, mandatory captures, direction rules,
     * and multi-jump continuations.
     */
    public boolean isValidMove(int r1, int c1, int r2, int c2) {
        // Bounds + destination empty
        if (r2 < 0 || r2 >= size || c2 < 0 || c2 >= size) return false;
        if (!board[r2][c2].isEmpty())                        return false;

        // Must move own piece
        if (!isOwnedBy(r1, c1, currentPlayer)) return false;

        // Multi-jump in progress: only that piece may move, and only by capturing
        if (pendingJumpRow >= 0) {
            if (r1 != pendingJumpRow || c1 != pendingJumpCol) return false;
            return isValidCapture(r1, c1, r2, c2, currentPlayer);
        }

        // Mandatory capture (level 2+): must jump if a jump is available
        if (level >= 2 && !getAllCaptures(currentPlayer).isEmpty()) {
            return isValidCapture(r1, c1, r2, c2, currentPlayer);
        }

        // A capture is always a legal move when available
        if (level >= 2 && isValidCapture(r1, c1, r2, c2, currentPlayer)) return true;

        // --- Regular (non-capture) move rules ---
        return isValidRegularMove(r1, c1, r2, c2);
    }

    /**
     * Executes a pre-validated move.  Returns true if the turn switched to
     * the other player, false if the turn is still in progress (multi-jump
     * or stack penalty pending).
     */
    public boolean executeMove(int r1, int c1, int r2, int c2) {
        boolean wasCapture = isCaptureMove(r1, c1, r2, c2);

        // Move the piece
        int status = board[r1][c1].getStatus();
        board[r2][c2].setStatus(status);
        board[r1][c1].setStatus(CellCoordinate.EMPTY);

        // Remove the captured piece
        if (wasCapture) removeCapturedPiece(r1, c1, r2, c2);

        // Promotion check
        boolean promoted = checkPromotion(r2, c2);

        // Level-1 stack penalty: hold the turn, let caller invoke removeOpponentPiece()
        if (pendingPenalty) {
            pendingJumpRow = -1;
            pendingJumpCol = -1;
            return false;
        }

        // After a capture (not followed by promotion), check for multi-jump continuation
        if (wasCapture && !promoted) {
            List<int[]> further = getCapturesForPiece(r2, c2, currentPlayer);
            if (!further.isEmpty()) {
                pendingJumpRow = r2;
                pendingJumpCol = c2;
                return false; // same player continues
            }
        }

        // Normal end-of-turn
        pendingJumpRow = -1;
        pendingJumpCol = -1;
        currentPlayer = (currentPlayer == 1) ? 2 : 1;
        return true;
    }

    /*WIN CONDITION*/

    /**
     * @return 1 if Red wins, 2 if Black wins, 0 if the game is still going.
     */
    public int checkWin() {
        boolean redAlive   = false;
        boolean blackAlive = false;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                int s = board[r][c].getStatus();
                if (s == CellCoordinate.RED_PIECE  || s == CellCoordinate.RED_KING
                 || s == CellCoordinate.RED_STACK)   redAlive   = true;
                if (s == CellCoordinate.BLACK_PIECE || s == CellCoordinate.BLACK_KING
                 || s == CellCoordinate.BLACK_STACK) blackAlive = true;
            }
        }
        if (!blackAlive) return 1;
        if (!redAlive)   return 2;

        // No legal moves → current player loses
        if (getAllMoves(currentPlayer).isEmpty()) {
            return (currentPlayer == 1) ? 2 : 1;
        }
        return 0;
    }

    /*AI — MINIMAX WITH ALPHA-BETA PRUNING*/

    /**
     * Returns the best [r1,c1,r2,c2] move for the current player.
     * Depth = 4 at level 3 (all pieces); depth = 2 at level 2 (Grade B).
     */
    public int[] getAIMove() {
        int depth     = (level == 3) ? 4 : 2;
        boolean isMax = (currentPlayer == 1); // Red maximises
        int[]  best   = null;
        int bestScore = isMax ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        for (int[] move : getAllMoves(currentPlayer)) {
            BoardModel copy  = deepCopy();
            copy.executeMove(move[0], move[1], move[2], move[3]);
            int score = copy.minimax(depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, !isMax);
            if (isMax ? (score > bestScore) : (score < bestScore)) {
                bestScore = score;
                best      = move;
            }
        }
        return best;
    }

    private int minimax(int depth, int alpha, int beta, boolean maximizing) {
        int winner = checkWin();
        if (winner == 1) return  1000 + depth;   // Red wins — prefer quicker wins
        if (winner == 2) return -1000 - depth;   // Black wins

        if (depth == 0) return evaluateBoard();

        List<int[]> moves = getAllMoves(currentPlayer);
        if (moves.isEmpty()) return maximizing ? -1000 : 1000;

        if (maximizing) {
            int max = Integer.MIN_VALUE;
            for (int[] m : moves) {
                BoardModel copy = deepCopy();
                copy.executeMove(m[0], m[1], m[2], m[3]);
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
                int s = copy.minimax(depth - 1, alpha, beta, true);
                min  = Math.min(min, s);
                beta = Math.min(beta, s);
                if (beta <= alpha) break;
            }
            return min;
        }
    }

    /**
     * Board-evaluation heuristic.
     * Positive = Red advantage.  Negative = Black advantage.
     * Kings are worth more; center-board positions get a small positional bonus.
     */
    public int evaluateBoard() {
        int score = 0;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                int s = board[r][c].getStatus();
                // Material
                switch (s) {
                    case CellCoordinate.RED_PIECE:   score += 10; break;
                    case CellCoordinate.RED_KING:    score += 16; break;
                    case CellCoordinate.RED_STACK:   score += 13; break;
                    case CellCoordinate.BLACK_PIECE: score -= 10; break;
                    case CellCoordinate.BLACK_KING:  score -= 16; break;
                    case CellCoordinate.BLACK_STACK: score -= 13; break;
                }
                // Positional bonus: prefer centre columns
                if (s != CellCoordinate.EMPTY) {
                    int centerBonus = (int)(2.0 - Math.abs(c - (size / 2.0 - 0.5)));
                    if (s == CellCoordinate.RED_PIECE || s == CellCoordinate.RED_KING)
                        score += centerBonus;
                    else if (s == CellCoordinate.BLACK_PIECE || s == CellCoordinate.BLACK_KING)
                        score -= centerBonus;
                }
            }
        }
        return score;
    }

    /*CAPTURE / MOVE QUERIES (package-visible for minimax copies)*/

    /** All [r1,c1,r2,c2] captures available to {@code player}. */
    public List<int[]> getAllCaptures(int player) {
        List<int[]> caps = new ArrayList<>();
        if (level < 2) return caps;   // Level 1 has no captures
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++)
                if (isOwnedBy(r, c, player))
                    caps.addAll(getCapturesForPiece(r, c, player));
        return caps;
    }

    /** All legal [r1,c1,r2,c2] moves for {@code player} (captures are mandatory). */
    public List<int[]> getAllMoves(int player) {
        // Multi-jump in progress: only the chained piece may move
        if (pendingJumpRow >= 0) {
            return getCapturesForPiece(pendingJumpRow, pendingJumpCol, player);
        }

        // Mandatory capture at levels 2+
        List<int[]> caps = getAllCaptures(player);
        if (!caps.isEmpty() && level >= 2) return caps;

        // Collect all regular moves
        List<int[]> moves = new ArrayList<>(caps); // includes any caps found at level 1 (none)
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (!isOwnedBy(r, c, player)) continue;
                moves.addAll(getRegularMovesForPiece(r, c, player));
            }
        }
        return moves;
    }

    /*STACK PENALTY (Grade C)*/

    public boolean isPendingPenalty() { return pendingPenalty; }

    /**
     * Called by the UI/text loop after a stack is created.
     * Removes an opponent piece chosen by the current player.
     */
    public boolean removeOpponentPiece(int r, int c) {
        int s = board[r][c].getStatus();
        boolean valid =
            (currentPlayer == 1 && (s == CellCoordinate.BLACK_PIECE
                                  || s == CellCoordinate.BLACK_KING
                                  || s == CellCoordinate.BLACK_STACK))
         || (currentPlayer == 2 && (s == CellCoordinate.RED_PIECE
                                  || s == CellCoordinate.RED_KING
                                  || s == CellCoordinate.RED_STACK));
        if (!valid) return false;

        board[r][c].setStatus(CellCoordinate.EMPTY);
        pendingPenalty = false;
        currentPlayer = (currentPlayer == 1) ? 2 : 1;  // now switch turns
        return true;
    }

    /*GETTERS*/

    public int getSize()          { return size; }
    public int getLevel()         { return level; }
    public int getCurrentPlayer() { return currentPlayer; }
    public int getCellStatus(int r, int c) { return board[r][c].getStatus(); }

    public boolean hasPendingJump() { return pendingJumpRow >= 0; }
    public int getPendingJumpRow()  { return pendingJumpRow; }
    public int getPendingJumpCol()  { return pendingJumpCol; }

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

    // Regular-move validation

    private boolean isValidRegularMove(int r1, int c1, int r2, int c2) {
        int   status    = board[r1][c1].getStatus();
        boolean isKing  = (status == CellCoordinate.RED_KING || status == CellCoordinate.BLACK_KING);
        int   rowDiff   = r2 - r1;
        int   colDiff   = c2 - c1;
        int   absRow    = Math.abs(rowDiff);
        int   absCol    = Math.abs(colDiff);

        if (level == 3) {
            if (isKing) {
                // Flying king: any distance, any of 8 directions, path must be clear
                boolean diagonal   = (absRow == absCol && absRow > 0);
                boolean horizontal = (absRow == 0 && absCol > 0);
                boolean vertical   = (absRow > 0 && absCol == 0);
                if (!diagonal && !horizontal && !vertical) return false;
                return isPathClear(r1, c1, r2, c2);
            } else {
                // Grade A: regular pieces single-step in all 8 directions
                return (absRow <= 1 && absCol <= 1 && (absRow + absCol) > 0);
            }
        } else {
            // Levels 1 & 2: diagonal only
            if (absRow != 1 || absCol != 1) return false;
            if (isKing) return true;                  // kings go any diagonal direction
            return isForwardStep(r1, r2, status);     // regular pieces forward only
        }
    }

    /** True if row change is in the correct forward direction for this piece type. */
    private boolean isForwardStep(int r1, int r2, int status) {
        if (status == CellCoordinate.RED_PIECE)   return r2 < r1; // Red moves up
        if (status == CellCoordinate.BLACK_PIECE) return r2 > r1; // Black moves down
        return true; // kings handled elsewhere
    }

    // Capture validation

    private boolean isValidCapture(int r1, int c1, int r2, int c2, int player) {
        if (level < 2) return false;   // Level 1 has no captures
        if (!board[r2][c2].isEmpty()) return false;

        int   status = board[r1][c1].getStatus();
        boolean isKing = (status == CellCoordinate.RED_KING || status == CellCoordinate.BLACK_KING);
        int   rowDiff  = r2 - r1;
        int   colDiff  = c2 - c1;
        int   absRow   = Math.abs(rowDiff);
        int   absCol   = Math.abs(colDiff);

        if (level == 3 && isKing) {
            // Flying-king capture: any diagonal distance, exactly 1 opponent in path
            if (absRow != absCol || absRow < 2) return false;
            int dr = rowDiff / absRow;
            int dc = colDiff / absCol;
            int opps = 0;
            for (int i = 1; i < absRow; i++) {
                int s = board[r1 + i * dr][c1 + i * dc].getStatus();
                if (s != CellCoordinate.EMPTY) {
                    if (isOpponentPiece(s, player)) { opps++; if (opps > 1) return false; }
                    else                              return false; // own piece in path
                }
            }
            return opps == 1;
        } else {
            // Standard 2-square diagonal jump
            if (absRow != 2 || absCol != 2) return false;
            int mid = board[r1 + rowDiff / 2][c1 + colDiff / 2].getStatus();
            return isOpponentPiece(mid, player);
        }
    }

    // Determine at execution time whether a move is a capture

    private boolean isCaptureMove(int r1, int c1, int r2, int c2) {
        if (level < 2) return false;
        int absRow = Math.abs(r2 - r1);
        int absCol = Math.abs(c2 - c1);
        if (absRow == 2 && absCol == 2) return true;

        int status = board[r1][c1].getStatus();
        boolean isKing = (status == CellCoordinate.RED_KING || status == CellCoordinate.BLACK_KING);
        if (level == 3 && isKing && absRow == absCol && absRow > 2) {
            return isValidCapture(r1, c1, r2, c2, currentPlayer);
        }
        return false;
    }

    // Capture enumeration (for mandatory-capture + multi-jump)

    private List<int[]> getCapturesForPiece(int r, int c, int player) {
        List<int[]> caps = new ArrayList<>();
        if (level < 2) return caps;

        int status = board[r][c].getStatus();
        boolean isKing = (status == CellCoordinate.RED_KING || status == CellCoordinate.BLACK_KING);

        if (level == 3 && isKing) {
            // Flying-king: scan all 4 diagonals for a single opponent to jump over
            int[][] dirs = {{1,1},{1,-1},{-1,1},{-1,-1}};
            for (int[] d : dirs) {
                boolean foundOpp = false;
                for (int step = 1; step < size; step++) {
                    int nr = r + step * d[0];
                    int nc = c + step * d[1];
                    if (nr < 0 || nr >= size || nc < 0 || nc >= size) break;
                    int s = board[nr][nc].getStatus();
                    if (s == CellCoordinate.EMPTY) {
                        if (foundOpp) caps.add(new int[]{r, c, nr, nc}); // valid landing
                    } else if (isOpponentPiece(s, player)) {
                        if (foundOpp) break; // can't jump two opponents
                        foundOpp = true;
                    } else {
                        break; // own piece blocks
                    }
                }
            }
        } else {
            // Standard 2-square jump in all 4 diagonal directions
            int[][] dirs = {{2,2},{2,-2},{-2,2},{-2,-2}};
            for (int[] d : dirs) {
                int nr = r + d[0];
                int nc = c + d[1];
                if (nr < 0 || nr >= size || nc < 0 || nc >= size) continue;
                if (!board[nr][nc].isEmpty()) continue;
                int mr = r + d[0] / 2;
                int mc = c + d[1] / 2;
                if (isOpponentPiece(board[mr][mc].getStatus(), player))
                    caps.add(new int[]{r, c, nr, nc});
            }
        }
        return caps;
    }

    // Regular-move enumeration (for AI / win detection)

    private List<int[]> getRegularMovesForPiece(int r, int c, int player) {
        List<int[]> moves = new ArrayList<>();
        int status = board[r][c].getStatus();
        boolean isKing = (status == CellCoordinate.RED_KING || status == CellCoordinate.BLACK_KING);

        if (level == 3) {
            if (isKing) {
                // All 8 directions, any distance (path must be clear)
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
                // Single step in all 8 directions
                int[][] dirs = {{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
                for (int[] d : dirs) {
                    int nr = r + d[0]; int nc = c + d[1];
                    if (nr < 0 || nr >= size || nc < 0 || nc >= size) continue;
                    if (board[nr][nc].isEmpty()) moves.add(new int[]{r, c, nr, nc});
                }
            }
        } else {
            if (isKing) {
                // 4 diagonal directions, 1 step
                int[][] dirs = {{1,1},{1,-1},{-1,1},{-1,-1}};
                for (int[] d : dirs) {
                    int nr = r + d[0]; int nc = c + d[1];
                    if (nr < 0 || nr >= size || nc < 0 || nc >= size) continue;
                    if (board[nr][nc].isEmpty()) moves.add(new int[]{r, c, nr, nc});
                }
            } else {
                // Forward diagonal only
                int fwd = (player == 1) ? -1 : 1;
                for (int dc : new int[]{-1, 1}) {
                    int nr = r + fwd; int nc = c + dc;
                    if (nr < 0 || nr >= size || nc < 0 || nc >= size) continue;
                    if (board[nr][nc].isEmpty()) moves.add(new int[]{r, c, nr, nc});
                }
            }
        }
        return moves;
    }

    // Execution helpers

    private void removeCapturedPiece(int r1, int c1, int r2, int c2) {
        int rowDiff = r2 - r1;
        int colDiff = c2 - c1;
        int absRow  = Math.abs(rowDiff);
        int dr = rowDiff / absRow;
        int dc = colDiff / Math.abs(colDiff);

        if (absRow == 2) {
            // Standard 2-square jump
            board[r1 + dr][c1 + dc].setStatus(CellCoordinate.EMPTY);
        } else {
            // Flying king: first opponent piece encountered along the path
            for (int i = 1; i < absRow; i++) {
                int mr = r1 + i * dr;
                int mc = c1 + i * dc;
                if (board[mr][mc].getStatus() != CellCoordinate.EMPTY) {
                    board[mr][mc].setStatus(CellCoordinate.EMPTY);
                    break;
                }
            }
        }
    }

    /**
     * Promotes a piece that has reached its promotion row.
     * @return true if a promotion occurred.
     */
    private boolean checkPromotion(int r, int c) {
        int s = board[r][c].getStatus();
        if (r == 0 && s == CellCoordinate.RED_PIECE) {
            board[r][c].setStatus(level == 1 ? CellCoordinate.RED_STACK : CellCoordinate.RED_KING);
            if (level == 1) pendingPenalty = true;
            return true;
        }
        if (r == size - 1 && s == CellCoordinate.BLACK_PIECE) {
            board[r][c].setStatus(level == 1 ? CellCoordinate.BLACK_STACK : CellCoordinate.BLACK_KING);
            if (level == 1) pendingPenalty = true;
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

    // Deep copy for minimax

    private BoardModel deepCopy() {
        BoardModel copy = new BoardModel(this.size, this.level);
        copy.currentPlayer  = this.currentPlayer;
        copy.pendingPenalty = this.pendingPenalty;
        copy.pendingJumpRow = this.pendingJumpRow;
        copy.pendingJumpCol = this.pendingJumpCol;
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++) {
                copy.board[r][c] = new CellCoordinate(this.board[r][c].isAvailable());
                copy.board[r][c].setStatus(this.board[r][c].getStatus());
            }
        return copy;
    }
}