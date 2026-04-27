package Checkers;

import java.util.ArrayList;

public class BoardModel {
    private CellCoordinate[][] board;
    private int size;
    private int level; // 1: Beginner (C), 2: Intermediate (B), 3: Advanced (A)
    private int currentPlayer; // 1 for Red, 2 for Black

    public BoardModel(int size, int level) {
        this.size = size;
        this.level = level;
        this.board = new CellCoordinate[size][size];
        this.currentPlayer = 1; // Red starts
        initializeBoard();
    }

    private void initializeBoard() {
        int rowsOfPieces;
        if (size == 8) {
            rowsOfPieces = 3;
        } else {
            rowsOfPieces = 4;
        }
        
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                boolean available = (r + c) % 2 != 0;
                board[r][c] = new CellCoordinate(available);
                
                if (available) {
                    if (r < rowsOfPieces) {
                        board[r][c].setStatus(CellCoordinate.BLACK_PIECE);
                    } else if (r >= size - rowsOfPieces) {
                        board[r][c].setStatus(CellCoordinate.RED_PIECE);
                    }
                }
            }
        }
    }

    // CORE LOGIC: Validates move based on Level requirements
    public boolean isValidMove(int r1, int c1, int r2, int c2) {
        if (r2 < 0 || r2 >= size || c2 < 0 || c2 >= size) return false;
        if (!board[r2][c2].isEmpty()) return false;

        int rowDiff = Math.abs(r2 - r1);
        int colDiff = Math.abs(c2 - c1);

        // Grade A Scope: Allow horizontal and vertical moves
        if (level == 3) {
            return (rowDiff == 1 && colDiff == 0) || (rowDiff == 0 && colDiff == 1) || (rowDiff == 1 && colDiff == 1);
        }

        // Grade C & B Scope: Diagonal only
        return rowDiff == 1 && colDiff == 1;
    }

    public void executeMove(int r1, int c1, int r2, int c2) {
        int status = board[r1][c1].getStatus();
        board[r2][c2].setStatus(status);
        board[r1][c1].setStatus(CellCoordinate.EMPTY);

        checkPromotion(r2, c2);
        // Switch players logic here
    }

    private void checkPromotion(int r, int c) {
        int status = board[r][c].getStatus();
        // Red reaches top row
        if (r == 0 && status == CellCoordinate.RED_PIECE) {
            if (level == 1) {
                board[r][c].setStatus(CellCoordinate.RED_STACK);
                triggerPenalty(); // Signal UI to let player remove an opponent piece
            } else {
                board[r][c].setStatus(CellCoordinate.RED_KING);
            }
        }
        // Black reaches bottom row
        if (r == size - 1 && status == CellCoordinate.BLACK_PIECE) {
            if (level == 1) {
                board[r][c].setStatus(CellCoordinate.BLACK_STACK);
                triggerPenalty();
            } else {
                board[r][c].setStatus(CellCoordinate.BLACK_KING);
            }
        }
    }

    // Grade C: The "Stack" penalty logic
    public void removeOpponentPiece(int r, int c) {
        int targetStatus = board[r][c].getStatus();
        
        if (currentPlayer == 1) { // Red's turn, removing Black
            if (targetStatus == CellCoordinate.BLACK_PIECE || 
                targetStatus == CellCoordinate.BLACK_KING || 
                targetStatus == CellCoordinate.BLACK_STACK) {
                board[r][c].setStatus(CellCoordinate.EMPTY);
            }
        } else if (currentPlayer == 2) { // Black's turn, removing Red
            if (targetStatus == CellCoordinate.RED_PIECE || 
                targetStatus == CellCoordinate.RED_KING || 
                targetStatus == CellCoordinate.RED_STACK) {
                board[r][c].setStatus(CellCoordinate.EMPTY);
            }
        }
    }

    private void triggerPenalty() {
        System.out.println("Stack created! Select an opponent's piece to remove.");
    }
    
    public int getSize() {
        return this.size;
    }

    // Helper for both GUI and Text View
    public int getCellStatus(int r, int c) {
        return board[r][c].getStatus();
    }
}