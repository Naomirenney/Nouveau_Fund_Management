package north.trading.controller;

import io.javalin.Javalin;
import io.javalin.http.Context;
import north.trading.model.User;
import north.trading.repository.UserRepository;

public class AuthController {

    private final UserRepository userRepo;

    public AuthController(Javalin app, UserRepository userRepo) {
        this.userRepo = userRepo;
        app.get("/login", this::showLogin);
        app.post("/login", this::handleLogin);
        app.get("/logout", this::logout);
    }

    private void showLogin(Context ctx) {
        ctx.render("login.html");
    }

    private void handleLogin(Context ctx) {
        String username = ctx.formParam("username");

        if (username == null || username.trim().isEmpty()) {
            ctx.attribute("error", "Username is required");
            ctx.render("login.html");
            return;
        }

        username = username.trim();

        // Check if user exists
        var userOpt = userRepo.findByUsername(username);

        if (userOpt.isPresent()) {
            // Existing user - welcome back
            User user = userOpt.get();
            ctx.sessionAttribute("user", user);
            ctx.attribute("message", "Welcome back, " + username + "!");
            ctx.redirect("/dashboard");
        } else {
            // New user - create account
            userRepo.createUser(username);
            var newUser = userRepo.findByUsername(username);
            if (newUser.isPresent()) {
                ctx.sessionAttribute("user", newUser.get());
                ctx.attribute("message", "Welcome new trader, " + username + "! You've been credited $100,000.");
                ctx.redirect("/dashboard");
            } else {
                ctx.attribute("error", "Failed to create account");
                ctx.render("login.html");
            }
        }
    }

    private void logout(Context ctx) {
        ctx.req().getSession().invalidate();
        ctx.redirect("/login");
    }
}