import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Mouse — click handler.
 *
 * Refactor: replaces the legacy logic that referenced its own piece
 * objects.  Now it just forwards (row, col) to Board.handleClick(),
 * which delegates to BoardModel for all rules / validation.
 */
public class Mouse extends MouseAdapter {

    @Override
    public void mousePressed(MouseEvent e) {
        Object src = e.getSource();
        if (!(src instanceof Tiles)) return;
        Tiles tile = (Tiles) src;
        tile.getBoard().handleClick(tile.getRow(), tile.getCol());
    }
}
