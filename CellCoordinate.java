
public class CellCoordinate {
    
    private boolean isAvailable;
    
    public static final int EMPTY = 0;
    public static final int RED_PIECE = 1;
    public static final int BLACK_PIECE = 2;
    public static final int RED_KING = 3;
    public static final int BLACK_KING = 4;
    public static final int RED_STACK = 5;
    public static final int BLACK_STACK = 6;
    
    private int status;
    
    public CellCoordinate(boolean isAvailable) {
        this.isAvailable = isAvailable;
        this.status = EMPTY;
    }
    
    public boolean isAvailable() {
        return isAvailable;
    }
    
    public int getStatus() {
        return status;
    }
    
    public void setStatus(int status) {
        this.status = status;
    }
    
    public boolean isEmpty() {
        return this.status == EMPTY;
    }
}
