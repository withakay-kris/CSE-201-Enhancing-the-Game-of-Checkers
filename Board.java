package Checkers;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics; // (1) This is added
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
// import javax.swing.JFrame; (2) This has been removed
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

public class Board extends JComponent {
    // Numerous private variables with different characteristics
    private int COL; // (3) final & = 8 was removed
    private int ROW; // (4) final & = 8 was removed
    private Tiles[][] theBoard; // (5) This was added
    // private Tiles theBoard[][] = new Tiles[COL][ROW]; // The container for the checkerboard (6)Removed
    // private Tiles[][] tile = new Tiles[COL][ROW]; // Container for the tiles (7)Removed
    // private JFrame frame; (8) This has been removed
    // private JPanel panel; (9) Removed
    private ArrayList<Pieces> redPieces = new ArrayList<>(); // Holds all the
    // pieces.
    private ArrayList<Pieces> blackPieces = new ArrayList<>(); // Used in
    // construction
    // of the board
    private JMenuBar menuBar;
    private JMenu menu; // Menu
    private JMenuItem help;
    private JMenuItem resign; // Resign option
    private int redCounter = 0;
    private int blackCounter = 0;
    private int destRow = 0;
    private int destCol = 0;
    private int currentRow = 0;
    private int currentCol = 0;
    private int preyRow = 0; // Coordinates of piece being eaten
    private int preyCol = 0;
    private int turnCounter = 0;
    private Pieces lastPieceMoved; // Proposed for a path-finding function...
    private Player RED; // Players of the game
    private Player BLACK;
    private ArrayList<Pieces> nextPiece = new ArrayList<>();
    private String loser; // Prints losing side
    
    private BoardModel model; // (10) This is added
    
    /** (11) This is ignored
    public Board() {
        createComponents();
        addingTiles();
        makePieces();
        createMenu();
        BLACK = new Player(PlayerType.BLACK);
        RED = new Player(PlayerType.RED);
        frame.add(panel);
        frame.setVisible(true);
    }
    **/
    
    // (12) This is a new method added...
    public Board(BoardModel model) {
        this.model = model;
        this.ROW = model.getSize();
        this.COL = model.getSize();
        
        this.theBoard = new Tiles[ROW][COL];
        
        this.BLACK = new Player(PlayerType.BLACK);
        this.RED = new Player(PlayerType.RED);
        
        setLayout(new GridLayout(ROW, COL));
        int totalSize = ROW * 60;
        setPreferredSize(new Dimension(totalSize, totalSize));
        
        setupTiles();
        makePieces();
        createMenu();
    }
    
    // (13) This is a new method added...
    private void setupTiles() {
        int size = model.getSize();
        Mouse m = new Mouse();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                theBoard[i][j] = new Tiles(i, j, this);
                theBoard[i][j].addMouseListener(m);
                add(theBoard[i][j]);
            }
        }
    }
    
    // (14) This is a new added method...
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
    }

    public void checkWin() { // If one side has no pieces left, the other side
        // wins. No draw game functionality
        if (RED.piecesLeft() == 0) {
            loser = "Red";
            displayDialog();
            handleGameOver();
        } else if (BLACK.piecesLeft() == 0) {
            loser = "Black";
            handleGameOver();
        }
    }
    
    private void handleGameOver() {
        displayDialog();
        
        Window window = javax.swing.SwingUtilities.getWindowAncestor(this);
        if (window != null) {
            window.dispose();
        }
    }

    public void switchTurns() { // Once a move is made, switch/increment turns
        turnCounter++;
    }

    public void clearPotentialMoves() {
        nextPiece.clear();
    }

    public PlayerType turn() { // Makes sure turns are alternating. Black goes
        // first.
        if (turnCounter % 2 == 1) {
            return PlayerType.RED;
        } else {
            return PlayerType.BLACK;
        }
    }

    public void getRootRowCol(int row, int col) { // Passes in clicked
        // piece/tile coordinates
        currentRow = row;
        currentCol = col;
    }
    /**
    public void createComponents() { // Creation of JComponents
        frame = new JFrame();
        frame.setSize(new Dimension(600, 623));
        frame.setResizable(false);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        panel = new JPanel(new GridLayout(ROW, COL));
    }
    

    public void addingTiles() { // Creation and storage of Tiles. Tiles extends
        // JPanel
        Mouse m = new Mouse();
        for (int i = 0; i < ROW; i++) {
            for (int j = 0; j < COL; j++) {
                tile[i][j] = new Tiles(i, j, this); // Tile(row, column, Board
                // this);
                theBoard[i][j] = tile[i][j];
                theBoard[i][j].addMouseListener(m);
                panel.add(theBoard[i][j]); // i is row, j is column
            }
        }
    } */

    public void makePieces() { // Creation and storage of Pieces
        redCounter = 0;
        blackCounter = 0;
        redPieces.clear();
        blackPieces.clear();
        
        int rowsToFill;
        if (ROW >= 10) {
            rowsToFill = 4;
        } else {
            rowsToFill = 3;
        }
        
        for (int i = 0; i < ROW; i++) {
            for (int j = 0; j < COL; j++) {
                if ((i + j) % 2 != 0) {
                    
                    // Top rows for Red
                    if (i < rowsToFill) {
                        redPieces.add(new Pieces(i, j, PieceType.RED));
                        theBoard[i][j].addPiece(redPieces.get(redCounter));
                        redCounter++;
                    }
                    
                    // Bottom rows for Black
                    else if (i >= (ROW - rowsToFill)) {
                        blackPieces.add(new Pieces(i, j, PieceType.BLACK));
                        theBoard[i][j].addPiece(blackPieces.get(blackCounter));
                        blackCounter++;
                    }
                }
            }
        }
    }

    public void checkingTheCrown(Pieces p, int destRow, int destCol) { // Checks
        // if a
        // piece
        // has
        // qualified
        // to
        // become
        // a
        // King
        if (p.getType() == PieceType.RED && destRow == (ROW - 1)) {
            p.crowned();
        } else if (p.getType() == PieceType.BLACK && destRow == 0) {
            p.crowned();
        }
    }

    public Tiles getTile(int xCoord, int yCoord) {
        if ((xCoord >= 0 && xCoord <= ROW) && (yCoord >= 0 && yCoord <= COL)) {
            return theBoard[xCoord][yCoord];
        } else
            return null;
    }

    public int returnTurns() {
        return turnCounter;
    }

    public boolean checkJump(Pieces jumper) { // Checks if a piece may jump.
        // Jumper is the clicked piece.
        // For very direct movements
        currentRow = jumper.getRow();
        currentCol = jumper.getCol();
        System.out.println("Jumper begins at " + currentRow + "," + currentCol);
        int rowDistance = (destRow - jumper.getRow());
        int colDistance = (destCol - jumper.getCol());
        preyRow = jumper.getRow() + (rowDistance / 2); // Location of "prey"
        // piece
        preyCol = jumper.getCol() + (colDistance / 2);

        if (!theBoard[destRow][destCol].isOccupied() // If the destination is
                // not occupied and the
                // "prey" location is
                && (theBoard[preyRow][preyCol].isOccupied())) {
            return true;
        } else {
            System.err.println("Cannot jump to " + destRow + "," + destCol);
            switchTurns();
            return false;
        }
    }

    public boolean jumpAvailable(Pieces jumper) { // Checks numerous potential
        // destinations
        int switchCase = 0, RowMovement = 0, jumperRow = 0, jumperCol = 0;
        if (jumper.getType() == PieceType.RED) { // Depending on the piece type,
            // switch case checks
            // different areas
            switchCase = 1;
            RowMovement = 2; // Red pieces may only move South
        } else if (jumper.getType() == PieceType.BLACK) {
            switchCase = 2; // Black pieces may only move North
            RowMovement = -2;
        } else if (jumper.getType() == PieceType.RED_KING // Kings move in all 4
                // directions
                || jumper.getType() == PieceType.BLACK_KING) {
            switchCase = 3;
        }

        jumperRow = jumper.getRow();
        jumperCol = jumper.getCol();

        switch (switchCase) {
            case 1: { // Red pieces
                if ((jumperRow > -1 && jumperRow < ROW)
                        && (jumperCol > -1 && jumperCol < COL)) { // Checks if within
                    // board bounds
                    if ((jumperRow + RowMovement) < ROW) { // Checks if row is <= 7
                        if (jumperCol < (COL - 1) && jumperCol < (COL - 2) && jumperCol != 0
                                && jumperCol > 1) { // If the selected piece is not
                            // near any edges
                            if (!theBoard[jumperRow + RowMovement][jumperCol + 2] // Check
                                    // right
                                    // location
                                    .isOccupied()
                                    && theBoard[jumperRow + 1][jumperCol + 1]
                                    .isOccupied()) {
                                return true;
                            }
                            if (!theBoard[jumperRow + RowMovement][jumperCol - 2] // Check
                                    // left
                                    // location
                                    .isOccupied()
                                    && theBoard[jumperRow + 1][jumperCol - 1]
                                    .isOccupied()) {
                                return true;
                            }
                            return false;
                        }

                        if (jumperCol >= (COL - 2)) { // if jumper is close to 7 cols, check
                            // movement toward 0 col
                            if (!theBoard[jumperRow + RowMovement][jumperCol - 2]
                                    .isOccupied()
                                    && theBoard[jumperRow + 1][jumperCol - 1]
                                    .isOccupied()) {
                                return true;
                            }
                        }
                        if (jumperCol <= 1) {
                            if (!theBoard[jumperRow + RowMovement][jumperCol + 2]
                                    .isOccupied()
                                    && theBoard[jumperRow + 1][jumperCol + 1]
                                    .isOccupied()) {
                                return true;
                            }
                        }
                        return false;
                    } else
                        return false;
                }
            }
            break;
            case 2: { // Black pieces
                if ((jumperRow > -1 && jumperRow < ROW)
                        && (jumperCol > -1 && jumperCol < COL)) { // Checks if within
                    // board bounds
                    if ((jumperRow + RowMovement) > -1) { // if row within bounds

                        if (jumperCol < (COL - 1) && jumperCol < (COL - 2) && jumperCol > 0
                                && jumperCol > 1) { // if column less than 6 and
                            // greater than 1. check both
                            // sides

                            if (!theBoard[jumperRow + RowMovement][jumperCol + 2]
                                    .isOccupied()
                                    && theBoard[jumperRow - 1][jumperCol + 1]
                                    .isOccupied()) {
                                return true;
                            }
                            if (!theBoard[jumperRow + RowMovement][jumperCol - 2]
                                    .isOccupied()
                                    && theBoard[jumperRow - 1][jumperCol - 1]
                                    .isOccupied()) {
                                return true;
                            }
                            return false;
                        }
                        if (jumperCol >= (COL - 2)) { // if jumper is close to 7 cols, check
                            // movement toward 0 col
                            if (!theBoard[jumperRow + RowMovement][jumperCol - 2]
                                    .isOccupied()
                                    && theBoard[jumperRow - 1][jumperCol - 1]
                                    .isOccupied()) {
                                return true;
                            }
                        }
                        if (jumperCol <= 1) {
                            if (!theBoard[jumperRow + RowMovement][jumperCol + 2]
                                    .isOccupied()
                                    && theBoard[jumperRow - 1][jumperCol + 1]
                                    .isOccupied()) {
                                return true;
                            }
                        }
                        return false;
                    } else
                        return false;
                }
            }
            break;
            case 3: { // King availability
                int KingNorth = jumperRow - 2;
                int KingEast = jumperCol + 2;
                int KingSouth = jumperRow + 2;
                int KingWest = jumperCol - 2;
                if (KingSouth <= (ROW - 1) && KingEast <= (COL - 1) && KingNorth >= 0
                        && KingWest >= 0) { // If destination is within bounds
                    System.out.println(KingSouth + " " + KingEast + " " + KingNorth
                            + " " + KingWest);
                    if (!theBoard[KingNorth][KingEast].isOccupied()
                            && theBoard[jumperRow - 1][jumperCol + 1].isOccupied()) {
                        System.out.println("NorthEast open");
                        return true;
                    }
                    if (!theBoard[KingNorth][KingWest].isOccupied()
                            && theBoard[jumperRow - 1][jumperCol - 1].isOccupied()) {
                        System.out.println("NorthWest open");
                        return true;
                    }
                    if (!theBoard[KingSouth][KingEast].isOccupied()
                            && theBoard[jumperRow + 1][jumperCol + 1].isOccupied()) {
                        System.out.println("SouthEast open");
                        return true;
                    }
                    if (!theBoard[KingSouth][KingWest].isOccupied()
                            && theBoard[jumperRow + 1][jumperCol - 1].isOccupied()) {
                        System.out.println("SouthWest open");
                        return true;
                    }
                }
                if ((jumperRow == 0 || jumperRow == 1)
                        && ((KingEast < COL) && (KingWest >= 0))) { // Column to
                    // far north
                    if (!theBoard[KingSouth][KingEast].isOccupied()
                            && theBoard[jumperRow + 1][jumperCol + 1].isOccupied()) {
                        return true;
                    }
                    if (!theBoard[KingSouth][KingWest].isOccupied()
                            && theBoard[jumperRow + 1][jumperCol - 1].isOccupied()) {
                        return true;
                    }
                }
                if ((jumperCol == (ROW - 1) || jumperCol == (ROW - 2))
                        && ((KingEast < COL) && (KingWest >= 0))) { // Column to
                    // far south
                    if (!theBoard[KingNorth][KingEast].isOccupied()
                            && theBoard[jumperRow - 1][jumperCol + 1].isOccupied()) {
                        return true;
                    }
                    if (!theBoard[KingNorth][KingWest].isOccupied()
                            && theBoard[jumperRow - 1][jumperCol - 1].isOccupied()) {
                        return true;
                    }
                }
                if ((jumperRow == (COL - 1) || jumperRow == (COL - 2))
                        && ((KingNorth >= 0) && (KingSouth < ROW))) { // Column to
                    // far right
                    if (!theBoard[KingNorth][KingWest].isOccupied()
                            && theBoard[jumperRow - 1][jumperCol - 1].isOccupied()) {
                        return true;
                    }
                    if (!theBoard[KingSouth][KingWest].isOccupied()
                            && theBoard[jumperRow + 1][jumperCol - 1].isOccupied()) {
                        return true;
                    }
                }
                if ((jumperRow == 0 || jumperRow == 1)
                        && ((KingNorth >= 0) && (KingSouth < ROW))) { // Column to
                    // far left
                    if (!theBoard[KingNorth][KingEast].isOccupied()
                            && theBoard[jumperRow - 1][jumperCol + 1].isOccupied()) {
                        return true;
                    }
                    if (!theBoard[KingSouth][KingEast].isOccupied()
                            && theBoard[jumperRow + 1][jumperCol + 1].isOccupied()) {
                        return true;
                    }
                }
                return false;
            }
            default:
                System.err.println("Default case");
                break;
        }
        return false;
    }

    public void jumpPieces(Pieces jumper) {

        Pieces prey = theBoard[preyRow][preyCol].getPiece();
        Tiles t = theBoard[currentRow][currentCol];
        jumper = t.getPiece();
        if (checkJump(jumper)) {
            if (jumper.getType() == prey.getType()) {
                jumper.talk();
                prey.talk();
                System.err.println("Cannot eat same side piece");
                return;
            }
            theBoard[destRow][destCol].addPiece(jumper);
            theBoard[currentRow][currentCol].delete();
            jumper.moved(destRow, destCol);
            lastPieceMoved = jumper;
            checkingTheCrown(jumper, destRow, destCol);
            theBoard[preyRow][preyCol].delete();
            if (prey.getType() == PieceType.RED
                    || prey.getType() == PieceType.RED_KING) {
                RED.pieceEaten();
            } else if (prey.getType() == PieceType.BLACK
                    || prey.getType() == PieceType.BLACK_KING) {
                BLACK.pieceEaten();
            }
            checkWin();
        }
        if (jumpAvailable(lastPieceMoved)) {
            if (checkJump(lastPieceMoved)) {
                jumpPieces(lastPieceMoved);
            } else
                return;
        } else
            return;
    }

    public void movePieces(int dRow, int dCol) {
        destRow = dRow;
        destCol = dCol;
        System.out.println(currentRow + "," + currentCol
                + " would like to go to " + destRow + "," + destCol);
        if ((theBoard[currentRow][currentCol].isOccupied())
                && ((destRow + destCol) % 2 == 1)) { // Gray tiles
            Pieces root = theBoard[currentRow][currentCol].getPiece();
            if (jumpAvailable(root) == false) {
                if (root.getType() == PieceType.BLACK_KING
                        || root.getType() == PieceType.RED_KING) {
                    if ((Math.abs(destRow - currentRow) == 1)
                            || (Math.abs(destCol - currentCol) == 1)) {

                        if (theBoard[destRow][destCol].isOccupied() == false) {
                            theBoard[destRow][destCol].addPiece(root);
                            theBoard[currentRow][currentCol].delete();
                            root.moved(destRow, destCol);
                            lastPieceMoved = root;
                            System.out.println("Root piece moved to " + destRow
                                    + "," + destCol);
                            switchTurns();
                        }
                    }

                    // Normal piece movement
                } else if ((root.getType() == PieceType.BLACK || root.getType() == PieceType.RED)) {

                    if ((root.getType() == PieceType.RED && (destRow > currentRow))
                            || (root.getType() == PieceType.BLACK && (destRow < currentRow))) {
                        if ((Math.abs(destRow - currentRow) == 1)
                                || (Math.abs(destCol - currentCol) == 1)) {

                            if (theBoard[destRow][destCol].isOccupied() == false) {
                                theBoard[destRow][destCol].addPiece(root);
                                theBoard[currentRow][currentCol].delete();
                                root.moved(destRow, destCol);
                                lastPieceMoved = root;
                                // System.out.println("Last piece moved "
                                // + lastPieceMoved.getRow() + ","
                                // + lastPieceMoved.getCol());
                                switchTurns();
                                System.out.println("Root piece moved to "
                                        + destRow + "," + destCol);
                                checkingTheCrown(root, destRow, destCol);
                            }
                        }

                    } else {
                        System.err
                                .println("Normal pieces can't move backwards");
                        return;
                    }
                }
            } else {
                if (checkJump(root)) {
                    jumpPieces(root);
                    switchTurns();
                }
            }
        } else {
            System.err.println("Cannot move onto white tile bounds");
            return;
        }
    }

    public void displayDialog() {
        Window parentFrame = javax.swing.SwingUtilities.getWindowAncestor(this);
        
        // Now use parentFrame instead of the old 'frame' variable
        JOptionPane.showMessageDialog(parentFrame, loser + "-side player lost! Well played!");
    }
  

    public void createMenu() {
        menuBar = new JMenuBar();
        menu = new JMenu("File");
        resign = new JMenuItem("Resign");
        help = new JMenuItem("Help");
        help.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                Window parent = javax.swing.SwingUtilities.getWindowAncestor(Board.this);
                JOptionPane
                        .showMessageDialog(
                                parent,
                                "Checkers/Draughts is a board game designed to be played by two players.\n"
                                        + "\nThe objective is to \"eat\" all the pieces of the other side. This game is played only on the darker tiles of the board."
                                        + "\nNormal pieces may only move diagonally forward one space at a time, if a same-side piece is present, they are not able to move."
                                        + "\nPieces may only eat other-side pieces if there is another piece diagonal to them, and the tile behind that piece is open."
                                        + "\nIf the opportunity to eat a piece is present, the player must eat the piece.\n"
                                        + "\nNormal pieces that reach the other end of the board from their side are crowned king. Kings may move diagonally forwards and backwards.\n"
                                        + "\nThe first move is made by the black player side. Good luck and have fun!");
            }

        });
        resign.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                if (turnCounter % 2 == 1) {
                    RED.lost();
                    loser = "Red";
                } else {
                    BLACK.lost();
                    loser = "Black";
                }
                Window parentWindow = javax.swing.SwingUtilities.getWindowAncestor(Board.this);
                JOptionPane.showMessageDialog(parentWindow, loser + "-side player resigned! Good game.");
                
                if (parentWindow != null) {
                    parentWindow.dispose();
                }
            }
        });
        menuBar.add(menu);
        menu.add(help);
        menu.add(resign);
        // frame.setJMenuBar(menuBar);
    }
}