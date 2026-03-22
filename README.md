# Nouveau_Fund_Management
Repo for securities portfolio manager

>What this app can do:
> 
> View security prices that refreshes every 30 sec
> 
> Simulate a portfolio manager by trading with fake money.
> 
> Uses Alpha Vantage free API to retrieve real time data on security values

>How to link existing repos with each other:
> 
1. git remote add origin https://github.com/Naomirenney/Nouveau_Fund_Management.git

2. git pull --rebase origin main

3. git branch -m master main

4. git branch -u origin/main main
5. git branch --set-upstream-to=origin/main master

## MVC
> Model view controller
> 
> Model (Domain): Contains the "core" of the application, including entities, business logic, and state. It handles the "what" of your application—what a user is, what a product does, and the rules governing them.
> 
>Controller: Acts as an interface or broker between the view and the model. It handles incoming user actions, updates the Model, and selects a View to render.
> 

# SetUp

>```mvn clean compile exec:java```
> 
>open http://localhost:8080
> 
> register a new username and password
> 
> Or 
> 
> login
