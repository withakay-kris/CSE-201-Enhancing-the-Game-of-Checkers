package Checkers;

import javax.swing.*;
import java.awt.*;

public class StartUpMenu {
    
    public static void main(String[] args) {
        // Set Look and Feel to System for a cleaner UI
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
        frame.setSize(500, 350);
        frame.setLayout(new BorderLayout(15, 15));
        
        // Header
        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(new Color(60, 63, 65));
        JLabel title = new JLabel("Checkers Project Scope Design");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        headerPanel.add(title);
        
        // Center Section
        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 10, 10));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 50, 10, 50));
        
        JLabel instruction = new JLabel("Select Board Dimension & Level:", JLabel.CENTER);
        instruction.setFont(new Font("SansSerif", Font.BOLD, 18));
        centerPanel.add(instruction);
        
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        JButton btn8 = new JButton("8 x 8 (Beginner/Intermediate)");
        JButton btn10 = new JButton("10 x 10 (Advanced)");
        
        // Style buttons
        btn8.setBackground(new Color(70, 130, 180));
        btn8.setPreferredSize(new Dimension(200, 40));
        
        btn10.setBackground(new Color(200, 70, 70));
        btn10.setPreferredSize(new Dimension(200, 40));
        
        // Action Listeners using standard if/else logic in the launch method
        btn8.addActionListener(e -> launchGame(frame, 8));
        btn10.addActionListener(e -> launchGame(frame, 10));
        
        buttonRow.add(btn8);
        buttonRow.add(btn10);
        centerPanel.add(buttonRow);
        
        frame.add(headerPanel, BorderLayout.NORTH);
        frame.add(centerPanel, BorderLayout.CENTER);
        
        // Footer (Project Branding)
        JLabel footer = new JLabel("Refactored MVC Implementation", JLabel.CENTER);
        footer.setFont(new Font("SansSerif", Font.ITALIC, 10));
        frame.add(footer, BorderLayout.SOUTH);
        
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
    
    private void launchGame(JFrame menuFrame, int size) {
        // 1. Determine Level based on Size
        int level;
        if (size == 8) {
            level = 1; // Grade C: Beginner (8x8)
        } else {
            level = 2; // Grade B/A: Intermediate/Advanced (10x10)
        }
        
        // 2. Initialize the Shared Model (The Brain)
        BoardModel model = new BoardModel(size, level);
        
        // 3. Prompt for View Type (Grade C Requirement)
        Object[] options = {"GUI (Swing) View", "Text-Based View"};
        int viewChoice = JOptionPane.showOptionDialog(
            menuFrame, 
            "Existing design improved! Select your view mode:", 
            "View Selector",
            JOptionPane.DEFAULT_OPTION, 
            JOptionPane.QUESTION_MESSAGE,
            null, 
            options, 
            options[0]
        );

        // 4. Close menu and start the chosen view
        menuFrame.dispose();

        if (viewChoice == 1) {
            // Start Text View
            CheckersText textGame = new CheckersText(model);
            textGame.start();
        } else {
            // Start GUI View
            // This assumes your Board class has been updated to take the model
            JFrame gameFrame = new JFrame("Checkers - " + size + "x" + size);
            gameFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            
            Board boardComponent = new Board(model);
            
            gameFrame.add(boardComponent);
            
            gameFrame.pack();
            gameFrame.setLocationRelativeTo(null);
            gameFrame.setVisible(true);
        }
    }
}