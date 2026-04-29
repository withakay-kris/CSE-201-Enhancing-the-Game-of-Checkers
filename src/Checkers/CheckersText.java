
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class CheckersText {
    private BoardModel model;
    // We define the map as a constant so it's only created once
    private static final Map<Integer, String> REPRESENTATION_MAP = new HashMap<>();

    static {
        REPRESENTATION_MAP.put(CellCoordinate.EMPTY, " . ");
        REPRESENTATION_MAP.put(CellCoordinate.RED_PIECE, " r ");
        REPRESENTATION_MAP.put(CellCoordinate.BLACK_PIECE, " b ");
        REPRESENTATION_MAP.put(CellCoordinate.RED_KING, " R ");
        REPRESENTATION_MAP.put(CellCoordinate.BLACK_KING, " B ");
        REPRESENTATION_MAP.put(CellCoordinate.RED_STACK, "RS "); // 'RS' for Red Stack
        REPRESENTATION_MAP.put(CellCoordinate.BLACK_STACK, "BS "); // 'BS' for Black Stack
    }

    public CheckersText(BoardModel model) {
        this.model = model;
    }
    
    public void start() {
        System.out.println("Text game has started");
        displayBoard();
        
        Scanner keyboardReader = new Scanner(System.in);
        boolean running = true;
        
        while (running) {
            displayBoard();
            
            System.out.println("\nEnter your move (row1 col1 row2 col2) or 'exit':");
            String command = keyboardReader.nextLine();

            if (command.equalsIgnoreCase("exit")) {
                running = false;
                System.out.println("Game terminated.");
            } else {
                try {
                    // Split the input "0 1 1 2" into separate integers
                    String[] parts = command.split(" ");
                    int r1 = Integer.parseInt(parts[0]);
                    int c1 = Integer.parseInt(parts[1]);
                    int r2 = Integer.parseInt(parts[2]);
                    int c2 = Integer.parseInt(parts[3]);

                    if (model.isValidMove(r1, c1, r2, c2)) {
                        model.executeMove(r1, c1, r2, c2);
                        System.out.println("Move successful!");
                    } else {
                        System.err.println("Invalid Move! Try again.");
                    }
                } catch (Exception e) {
                    System.err.println("Error: Enter 4 numbers separated by spaces (e.g., 5 0 4 1)");
                }
            }
        }
        keyboardReader.close();
    }

    private void displayBoard() {
        int size = model.getSize();
        
        System.out.print("  ");
        for (int i = 0; i < size; i++) System.out.print(" " + i + " ");
        System.out.println();

        for (int r = 0; r < size; r++) {
            System.out.print(r + " "); // Print row header
            for (int c = 0; c < size; c++) {
                int status = model.getCellStatus(r, c);
                System.out.print(REPRESENTATION_MAP.getOrDefault(status, " ? "));
            }
            System.out.println(); 
        }
    }
}