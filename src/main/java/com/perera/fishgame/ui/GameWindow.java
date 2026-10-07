package com.perera.fishgame.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;

import com.perera.fishgame.auth.ScoreEntry;
import com.perera.fishgame.engine.AnswerResult;
import com.perera.fishgame.engine.GameEngine;
import com.perera.fishgame.engine.GameListener;
import com.perera.fishgame.model.Game;
import com.perera.fishgame.service.GameProviderException;

/**
 * The game window (the "view").
 *
 * <p>Event-driven programming in this class, from three sources:
 * <ol>
 * <li><b>User events:</b> clicking a digit button or pressing a key (0-9, also
 * on the numeric keypad) calls {@link #onDigit(int)}; the Leaderboard and
 * Retry/Play again buttons have their own action listeners. Swing calls them on
 * the Event Dispatch Thread (EDT).</li>
 * <li><b>Timer events:</b> a {@link Timer} fires once a second. When the
 * countdown reaches zero the window tells the engine the time is up
 * ({@link GameEngine#timeOut()}), which costs a life.</li>
 * <li><b>Game events out of the engine:</b> this class implements
 * {@link GameListener}; the engine fires score, lives, round and game-over
 * events and the window updates itself. The engine knows nothing about Swing
 * (low coupling).</li>
 * </ol>
 *
 * <p>Loading a round calls a web service, which can be slow. It is done in a
 * {@link SwingWorker} (background thread) so the window never freezes. Because
 * the engine then fires events from that background thread, every listener
 * method moves its work back onto the EDT with {@link #onEdt(Runnable)}
 * (Swing components may only be changed on the EDT).
 *
 * <p>The window knows which player is logged in, shows the player's best score
 * and can show the leaderboard. It does not know how scores are saved or read:
 * it gets a callback for finished games and a supplier for the leaderboard.
 *
 * <p>Author: Vinod Perera. (The unit's example GUI had the buttons and an
 * ActionListener; the engine events, keyboard input, timer, leaderboard,
 * background loading, error handling and player/best-score display are my own
 * additions.)
 */
public final class GameWindow extends JFrame implements GameListener {

    private static final long serialVersionUID = 1L;

    /** Seconds the player has to answer each picture. */
    static final int ROUND_SECONDS = 20;
    private static final int WARNING_SECONDS = 5;

    private final transient GameEngine engine;
    private final String playerName;
    private final transient IntConsumer onGameFinished;
    private final transient Supplier<List<ScoreEntry>> leaderboardSource;
    private int bestScore;

    private final JLabel imageLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel scoreLabel = new JLabel();
    private final JLabel timeLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel livesLabel = new JLabel();
    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JButton[] digitButtons = new JButton[10];
    private final JButton actionButton = new JButton();
    private final JButton leaderboardButton = new JButton("Leaderboard");

    /** Fires every second while a round is being played. */
    private final Timer countdown = new Timer(1000, e -> onTick());
    private int secondsLeft;
    private String notice = "";

    /** True while a round is being loaded; answers are ignored meanwhile. */
    private boolean loading = false;

    /**
     * @param engine            the game rules
     * @param playerName        the logged-in player, shown in the title
     * @param bestScore         the player's best score so far
     * @param onGameFinished    called (on the EDT) with the final score of each
     *                          finished game
     * @param leaderboardSource gives the leaderboard lines when the player asks
     *                          for them
     */
    public GameWindow(GameEngine engine, String playerName, int bestScore,
            IntConsumer onGameFinished, Supplier<List<ScoreEntry>> leaderboardSource) {
        super("Fish Game - " + playerName);
        this.engine = engine;
        this.playerName = playerName;
        this.bestScore = bestScore;
        this.onGameFinished = onGameFinished;
        this.leaderboardSource = leaderboardSource;
        buildLayout();
        installKeyboardShortcuts();
        engine.addListener(this); // subscribe to game events
        showScore(engine.getScore());
        showLives(engine.getLives());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------------
    // Building the window
    // ------------------------------------------------------------------

    private void buildLayout() {
        Font big = new Font(Font.SANS_SERIF, Font.BOLD, 18);

        scoreLabel.setFont(big);
        timeLabel.setFont(big);
        livesLabel.setFont(big);
        livesLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        top.add(scoreLabel, BorderLayout.WEST);
        top.add(timeLabel, BorderLayout.CENTER);
        top.add(livesLabel, BorderLayout.EAST);

        imageLabel.setPreferredSize(new Dimension(660, 360));
        imageLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));

        statusLabel.setFont(big);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));

        JPanel digits = new JPanel(new GridLayout(1, 10, 6, 0));
        for (int d = 0; d < 10; d++) {
            final int digit = d;
            JButton b = new JButton(String.valueOf(d));
            b.setFont(big);
            b.setFocusable(false); // keep keyboard shortcuts working
            b.addActionListener(e -> onDigit(digit)); // button click event
            digitButtons[d] = b;
            digits.add(b);
        }

        actionButton.setFont(big);
        actionButton.setVisible(false);
        actionButton.addActionListener(e -> onActionButton());

        leaderboardButton.setFocusable(false);
        leaderboardButton.addActionListener(e -> showLeaderboard());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttons.add(leaderboardButton);
        buttons.add(actionButton);

        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        bottom.add(statusLabel, BorderLayout.NORTH);
        bottom.add(digits, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout());
        center.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        center.add(imageLabel, BorderLayout.CENTER);

        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(center, BorderLayout.CENTER);
        getContentPane().add(bottom, BorderLayout.SOUTH);

        setDigitsEnabled(false);
    }

    /** Keys 0-9 (top row and numeric keypad) behave like clicking the button. */
    private void installKeyboardShortcuts() {
        JComponent root = getRootPane();
        for (int d = 0; d < 10; d++) {
            final int digit = d;
            AbstractAction action = new AbstractAction() {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(ActionEvent e) {
                    onDigit(digit); // keyboard event
                }
            };
            String name = "digit" + d;
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_0 + d, 0), name);
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0 + d, 0), name);
            root.getActionMap().put(name, action);
        }
    }

    // ------------------------------------------------------------------
    // User events
    // ------------------------------------------------------------------

    /** Starts the first round. Call once after the window is visible. */
    public void startGame() {
        loadNextRound();
    }

    /** The player chose a number (by button or key). Runs on the EDT. */
    void onDigit(int digit) {
        if (loading || engine.isGameOver() || engine.getCurrentGame() == null) {
            return; // ignore input while loading, after game over, or before a round
        }
        AnswerResult result = engine.submitAnswer(digit);
        switch (result) {
            case CORRECT -> {
                statusLabel.setText("Correct!");
                loadNextRound();
            }
            case WRONG -> statusLabel.setText("Not correct, try again!");
            case GAME_OVER -> {
                // The details are shown by onGameOver(), fired by the engine.
            }
        }
    }

    /** The button below the digits is either "Retry" or "Play again". */
    private void onActionButton() {
        actionButton.setVisible(false);
        if (engine.isGameOver()) {
            engine.restart();
        }
        loadNextRound();
    }

    private void showLeaderboard() {
        boolean wasRunning = countdown.isRunning();
        countdown.stop(); // the clock waits while the player looks at the table

        List<ScoreEntry> entries = leaderboardSource.get();
        StringBuilder text = new StringBuilder();
        if (entries.isEmpty()) {
            text.append("No scores yet. Be the first!");
        } else {
            int rank = 1;
            for (ScoreEntry entry : entries) {
                text.append(String.format("%2d.  %-20s %3d\n", rank++, entry.username(),
                        entry.bestScore()));
            }
        }
        JTextArea area = new JTextArea(text.toString());
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
        JOptionPane.showMessageDialog(this, area, "Leaderboard", JOptionPane.PLAIN_MESSAGE);

        if (wasRunning && !loading && !engine.isGameOver()) {
            countdown.start();
        }
    }

    // ------------------------------------------------------------------
    // Timer events
    // ------------------------------------------------------------------

    private void startCountdown() {
        secondsLeft = ROUND_SECONDS;
        showTime();
        countdown.restart();
    }

    private void stopCountdown() {
        countdown.stop();
        timeLabel.setText(" ");
    }

    /** Called by the Swing timer once a second, on the EDT. */
    private void onTick() {
        secondsLeft--;
        showTime();
        if (secondsLeft <= 0) {
            onTimeUp();
        }
    }

    private void onTimeUp() {
        countdown.stop();
        if (loading || engine.isGameOver() || engine.getCurrentGame() == null) {
            return;
        }
        if (engine.timeOut() == AnswerResult.WRONG) {
            notice = "Time is up! ";
            loadNextRound(); // a fresh picture, one life fewer
        }
        // GAME_OVER is shown by onGameOver(), fired by the engine.
    }

    private void showTime() {
        timeLabel.setText("Time: " + Math.max(secondsLeft, 0));
        timeLabel.setForeground(secondsLeft <= WARNING_SECONDS
                ? new Color(190, 30, 30) : UIManager.getColor("Label.foreground"));
    }

    // ------------------------------------------------------------------
    // Loading a round in the background
    // ------------------------------------------------------------------

    private void loadNextRound() {
        loading = true;
        stopCountdown();
        setDigitsEnabled(false);
        statusLabel.setText("Loading a new picture...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                engine.nextRound(); // slow: calls the web service
                return null;
            }

            @Override
            protected void done() { // back on the EDT
                loading = false;
                try {
                    get();
                    statusLabel.setText(notice + "How many fish are there?");
                    notice = "";
                    setDigitsEnabled(true);
                    startCountdown();
                } catch (ExecutionException e) {
                    notice = "";
                    Throwable cause = e.getCause();
                    String message = cause instanceof GameProviderException
                            ? cause.getMessage() : String.valueOf(cause);
                    statusLabel.setText("Could not load a game: " + message);
                    actionButton.setText("Retry");
                    actionButton.setVisible(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    // ------------------------------------------------------------------
    // Game events coming from the engine (GameListener)
    // ------------------------------------------------------------------

    @Override
    public void onRoundStarted(Game game) {
        onEdt(() -> imageLabel.setIcon(new ImageIcon(game.getImage())));
    }

    @Override
    public void onScoreChanged(int newScore) {
        onEdt(() -> showScore(newScore));
    }

    @Override
    public void onLivesChanged(int livesLeft) {
        onEdt(() -> showLives(livesLeft));
    }

    @Override
    public void onGameOver(int finalScore) {
        onEdt(() -> {
            stopCountdown();
            setDigitsEnabled(false);
            statusLabel.setText("Game over, " + playerName + "! Final score: " + finalScore);
            actionButton.setText("Play again");
            actionButton.setVisible(true);
            if (finalScore > bestScore) {
                bestScore = finalScore;
                showScore(finalScore);
            }
            // Report after the current event has finished, so a dialog (if the
            // callback shows one) cannot block the engine in the middle of its work.
            SwingUtilities.invokeLater(() -> onGameFinished.accept(finalScore));
        });
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private void showScore(int score) {
        scoreLabel.setText("Score: " + score + "    Best: " + bestScore);
    }

    private void showLives(int lives) {
        livesLabel.setText("Lives: " + lives);
    }

    private void setDigitsEnabled(boolean enabled) {
        for (JButton b : digitButtons) {
            b.setEnabled(enabled);
        }
    }

    /** Runs the task on the Event Dispatch Thread (now, if we are already on it). */
    private static void onEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }
}