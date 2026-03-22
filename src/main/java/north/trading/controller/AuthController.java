package north.trading.controller;


import io.javalin.Javalin;
import io.javalin.http.Context;
import north.trading.model.User;
import north.trading.repository.UserRepository;
import org.mindrot.jbcrypt.BCrypt;

public class AuthController {

    private final UserRepository userRepo;

    public AuthController(Javalin app, UserRepository userRepo) {
        this.userRepo = userRepo;
        //when user goes to url... /login
        app.get("/login", this::showLogin);
        //when user logins in via login html
        app.post("/login", this::handleLogin);
        app.get("/register", this::showRegister);
        app.post("/register", this::handleRegister);
        app.get("/logout", this::logout);
    }

    private void showLogin(Context ctx) {
        ctx.render("login.html");
    }

    private void handleLogin(Context ctx) {
        String username = ctx.formParam("username");
        String password = ctx.formParam("password");

        if (username == null || password == null) {
            ctx.attribute("error", "Missing credentials");
            ctx.render("login.html");
            return;
        }

        var userOpt = userRepo.findByUsername(username);
        if (userOpt.isEmpty() || !BCrypt.checkpw(password, userOpt.get().passwordHash())) {
            ctx.attribute("error", "Invalid username or password");
            ctx.render("login.html");
            return;
        }

        ctx.sessionAttribute("user", userOpt.get());
        ctx.redirect("/dashboard");
    }

    private void showRegister(Context ctx) {
        ctx.render("register.html");
    }

    private void handleRegister(Context ctx) {
        String username = ctx.formParam("username");
        String password = ctx.formParam("password");
        String password2 = ctx.formParam("password2");

        if (username == null || password == null || password2 == null) {
            ctx.attribute("error", "All fields are required");
            ctx.render("register.html");
            return;
        }

        if (!password.equals(password2)) {
            ctx.attribute("error", "Passwords do not match");
            ctx.render("register.html");
            return;
        }

        if (userRepo.findByUsername(username).isPresent()) {
            ctx.attribute("error", "Username already taken");
            ctx.render("register.html");
            return;
        }

        userRepo.createUser(username, password);
        ctx.redirect("/login");
    }

    private void logout(Context ctx) {
        ctx.req().getSession().invalidate();
        ctx.redirect("/login");
    }
}