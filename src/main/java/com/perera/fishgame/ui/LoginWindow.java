package com.perera.fishgame.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;

import com.perera.fishgame.auth.AuthException;
import com.perera.fishgame.auth.AuthService;
import com.perera.fishgame.auth.Session;

/**
 * Login and registration screen (virtual identity).
 *
 * <p>The window only collects input and shows messages; all the rules (password
 * checks, hashing, lockout, sessions) are in {@link AuthService}. Hashing is
 * deliberately slow, so login and registration run in a {@link SwingWorker}
 * and the window stays responsive. When login succeeds, the {@link Session} is
 * handed to the callback, which opens the game.
 *
 * <p>Author: Vinod Perera. (The unit's example login form, adapted from a
 * public tutorial, had a username field, a password field and a hard-coded
 * check; this version uses real authentication.)
 */
public final class LoginWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    private final transient AuthService auth;
    private final transient Consumer<Session> onLoggedIn;

    private final JTextField userField = new JTextField(18);
    private final JPasswordField passField = new JPasswordField(18);
    private final JButton loginButton = new JButton("Login");
    private final JButton registerButton = new JButton("Register");
    private final JLabel messageLabel = new JLabel(" ", SwingConstants.CENTER);

    public LoginWindow(AuthService auth, Consumer<Session> onLoggedIn) {
        super("Fish Game - Login");
        this.auth = auth;
        this.onLoggedIn = onLoggedIn;
        buildLayout();
        loginButton.addActionListener(e -> submit(false));
        registerButton.addActionListener(e -> submit(true));
        getRootPane().setDefaultButton(loginButton); // Enter key = Login
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
    }

    private void buildLayout() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.anchor = GridBagConstraints.WEST;

        JLabel title = new JLabel("Fish Game", SwingConstants.CENTER);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(title, c);

        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        c.gridy = 1;
        c.gridx = 0;
        form.add(new JLabel("Username:"), c);
        c.gridx = 1;
        form.add(userField, c);

        c.gridy = 2;
        c.gridx = 0;
        form.add(new JLabel("Password:"), c);
        c.gridx = 1;
        form.add(passField, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttons.add(loginButton);
        buttons.add(registerButton);
        c.gridy = 3;
        c.gridx = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(buttons, c);

        messageLabel.setPreferredSize(new Dimension(320, 48));
        c.gridy = 4;
        form.add(messageLabel, c);

        getContentPane().add(form);
    }

    /** Runs a login or a registration in the background. */
    private void submit(boolean register) {
        String username = userField.getText();
        char[] password = passField.getPassword();
        passField.setText(""); // never leave the password sitting in the box
        setBusy(true);
        showMessage(register ? "Creating account..." : "Checking...", false);

        new SwingWorker<Session, Void>() {
            @Override
            protected Session doInBackground() throws Exception {
                if (register) {
                    auth.register(username, password);
                    return null;
                }
                return auth.login(username, password);
            }

            @Override
            protected void done() { // back on the EDT
                setBusy(false);
                try {
                    Session session = get();
                    if (session == null) {
                        showMessage("Account created. You can now log in.", false);
                        passField.requestFocusInWindow();
                    } else {
                        onLoggedIn.accept(session);
                        dispose();
                    }
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    showMessage(cause instanceof AuthException
                            ? cause.getMessage() : "Something went wrong: " + cause, true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        loginButton.setEnabled(!busy);
        registerButton.setEnabled(!busy);
    }

    private void showMessage(String text, boolean error) {
        messageLabel.setForeground(error ? new Color(180, 30, 30) : Color.DARK_GRAY);
        messageLabel.setText("<html><div style='text-align:center;width:300px'>"
                + text + "</div></html>");
    }
}