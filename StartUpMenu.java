import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;

/**
 * StartUpMenu — entry point.
 *
 *  Board-size → level mapping:
 *    8×8  + Beginner     → level 1  (Grade C, no AI, optional captures, stacks)
 *    8×8  + Intermediate → level 2  (Grade B, AI 2-move, kings, mandatory captures)
 *    10×10               → level 3  (Grade A, AI 4-move, flying kings, omni-directional men)
 */
public class StartUpMenu {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(() -> new StartUpMenu().displayMenu());
    }

    public void displayMenu() {
        JFrame frame = new JFrame("Checkers Setup");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(560, 380);
        frame.setLayout(new BorderLayout(15, 15));

        JPanel header = new JPanel();
        header.setBackground(new Color(60, 63, 65));
        JLabel title = new JLabel("Checkers — Choose Game Mode");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        header.add(title);

        JPanel centre = new JPanel(new GridLayout(2, 1, 10, 10));
        centre.setBorder(BorderFactory.createEmptyBorder(10, 50, 10, 50));

        JLabel instruction = new JLabel("Select Board Dimension:", JLabel.CENTER);
        instruction.setFont(new Font("SansSerif", Font.BOLD, 18));
        centre.add(instruction);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));

        JButton btn8  = new JButton("8 × 8  (Beginner / Intermediate)");
        JButton btn10 = new JButton("10 × 10  (Advanced)");

        btn8.setBackground(new Color(70, 130, 180));
        btn8.setForeground(Color.BLACK);
        btn8.setPreferredSize(new Dimension(240, 42));

        btn10.setBackground(new Color(200, 70, 70));
        btn10.setForeground(Color.BLACK);
        btn10.setPreferredSize(new Dimension(240, 42));

        btn8.addActionListener(e -> {
            int level = chooseDifficulty8x8(frame);
            if (level > 0) launchGame(frame, 8, level);
        });
        btn10.addActionListener(e -> launchGame(frame, 10, 3));

        btnRow.add(btn8);
        btnRow.add(btn10);
        centre.add(btnRow);

        JLabel footer = new JLabel("Refactored MVC Implementation", JLabel.CENTER);
        footer.setFont(new Font("SansSerif", Font.ITALIC, 10));

        frame.add(header, BorderLayout.NORTH);
        frame.add(centre, BorderLayout.CENTER);
        frame.add(footer, BorderLayout.SOUTH);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private int chooseDifficulty8x8(JFrame parent) {
        String[] options = { "Beginner", "Intermediate" };
        int choice = JOptionPane.showOptionDialog(
                parent,
                "Choose difficulty for 8×8:",
                "Difficulty",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);
        if (choice == 0) return 1;
        if (choice == 1) return 2;
        return -1;
    }

    private void launchGame(JFrame menuFrame, int size, int level) {
        BoardModel model = new BoardModel(size, level);

        Object[] viewOpts = { "GUI (Swing) View", "Text-Based View" };
        int viewChoice = JOptionPane.showOptionDialog(
                menuFrame,
                "Select view mode:",
                "View Selector",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                viewOpts,
                viewOpts[0]);

        menuFrame.dispose();

        if (viewChoice == 1) {
            // Text view runs on its own thread so the Swing EDT isn't blocked.
            final BoardModel m = model;
            new Thread(() -> {
                CheckersText textGame = new CheckersText(m);
                textGame.start();
                System.exit(0);
            }, "checkers-text").start();
        } else {
            JFrame gameFrame = new JFrame("Checkers — " + size + "×" + size
                    + "  (Level " + level + ")");
            gameFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            Board boardComponent = new Board(model);
            gameFrame.setJMenuBar(boardComponent.getBoardMenuBar());
            gameFrame.add(boardComponent);
            gameFrame.pack();
            gameFrame.setLocationRelativeTo(null);
            gameFrame.setVisible(true);
        }
    }
}
