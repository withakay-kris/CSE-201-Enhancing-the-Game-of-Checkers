import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;

/**
 * Tiles — single board square (View component).
 *
 * Refactor: pieces are no longer owned by tiles.  The tile asks the
 * BoardModel for the cell's status and renders accordingly.  This
 * eliminates the parallel-state bug from the legacy code where the
 * GUI's Pieces objects could disagree with the model.
 *
 * The tile also displays a highlight when it is the currently selected
 * source cell (for the click-source / click-destination interaction).
 */
public class Tiles extends JPanel {

    private final int row;
    private final int col;
    private final Board board;        // for callbacks (selection, move attempts)
    private final BoardModel model;   // single source of truth

    public Tiles(int row, int col, Board board, BoardModel model) {
        this.row   = row;
        this.col   = col;
        this.board = board;
        this.model = model;
    }

    public int getRow()      { return row; }
    public int getCol()      { return col; }
    public Board getBoard()  { return board; }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // Square colour: dark squares are the playable cells.
        boolean dark = (row + col) % 2 != 0;
        g2.setColor(dark ? new Color(120, 80, 50) : new Color(238, 217, 181));
        g2.fillRect(0, 0, w, h);

        // Highlight: selected source.
        if (board.isSelected(row, col)) {
            g2.setColor(new Color(255, 255, 0, 120));
            g2.fillRect(0, 0, w, h);
            g2.setColor(Color.YELLOW);
            g2.setStroke(new BasicStroke(3f));
            g2.drawRect(2, 2, w - 4, h - 4);
        }

        // Highlight: legal destinations from current selection.
        if (board.isLegalDestination(row, col)) {
            g2.setColor(new Color(0, 255, 0, 90));
            g2.fillOval(w / 4, h / 4, w / 2, h / 2);
        }

        // Render the piece (if any).
        int status = model.getCellStatus(row, col);
        if (status == CellCoordinate.EMPTY) return;

        int pad  = w / 8;
        int size = w - 2 * pad;

        // Piece body colour
        Color body;
        switch (status) {
            case CellCoordinate.RED_PIECE:   body = new Color(200, 30, 30);   break;
            case CellCoordinate.RED_KING:    body = new Color(255, 60, 60);   break;
            case CellCoordinate.RED_STACK:   body = new Color(220, 50, 50);   break;
            case CellCoordinate.BLACK_PIECE: body = new Color(30, 30, 30);    break;
            case CellCoordinate.BLACK_KING:  body = new Color(80, 80, 80);    break;
            case CellCoordinate.BLACK_STACK: body = new Color(50, 50, 50);    break;
            default: body = Color.GRAY;
        }
        g2.setColor(body);
        g2.fillOval(pad, pad, size, size);

        // Outline
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval(pad, pad, size, size);

        // King marker
        if (status == CellCoordinate.RED_KING || status == CellCoordinate.BLACK_KING) {
            g2.setColor(Color.YELLOW);
            g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(12, size / 2)));
            String k = "K";
            int strW = g2.getFontMetrics().stringWidth(k);
            int strH = g2.getFontMetrics().getAscent();
            g2.drawString(k, (w - strW) / 2, (h + strH) / 2 - 2);
        }

        // Stack marker — show count
        if (status == CellCoordinate.RED_STACK || status == CellCoordinate.BLACK_STACK) {
            int cnt = model.getStackHeight(row, col);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(10, size / 3)));
            String s = "x" + cnt;
            int strW = g2.getFontMetrics().stringWidth(s);
            int strH = g2.getFontMetrics().getAscent();
            g2.drawString(s, (w - strW) / 2, (h + strH) / 2 - 2);
        }
    }
}
