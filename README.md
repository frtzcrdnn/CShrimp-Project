# CShrimp
 
A JavaFX desktop application that manages sales, inventory, products and cash for a small shrimp processing and export business. It replaces paper logs and spreadsheets with one program that has separate screens for managers and clerks.
 
The project was written for an object-oriented programming course. It uses only the JDK and JavaFX, with no other libraries.
 
## Features
 
**Manager**
- Adds, edits and removes products (name, description, price per kg).
- Enters opening stock, restocks products, and reads the stock history.
- Approves or rejects void requests from clerks, or voids a transaction directly.
- Approves new clerk accounts.
- Watches each clerk's sales history and can open any clerk's dashboard in read-only mode.
- Sees total sales, low-stock items, recent transactions and the cash balance on the dashboard.
**Clerk**
- Records a sale by choosing a product, entering the weight in kg and the cash received. The program calculates the total and the change, then reduces the stock.
- Sees a warning and cannot complete the sale when the requested weight is more than the stock on hand.
- Checks inventory and reports a product as low on stock, which the manager then sees.
- Records cash in and cash out entries in the wallet.
- Asks the manager to void a transaction by giving a reason.
Every transaction is saved with its date, time, user, product, quantity and amount. A voided transaction stays in the history, marked as voided, and its stock or cash effect is reversed.
 
## Products
 
The system ships with eight shrimp products. The manager can change this list and every price.
 
| Code | Name | Description |
|---|---|---|
| CHLSO | Cooked Head-Less Shell-On | Cooked shrimp with the head removed and shell left intact |
| CHOSO | Cooked Head-On Shell-On | Cooked shrimp with head and shell retained |
| FHOSO | Fresh Head-On Shell-On | Raw whole shrimp with head and shell intact |
| FHLSO | Fresh Head-Less Shell-On | Raw shrimp with head removed but shell kept on |
| NOBASHI | Nobashi | Peeled and deveined, tail-on, stretched shrimp |
| FPD | Fresh Peeled & Deveined | Raw shrimp fully peeled and deveined |
| FPDTO | Fresh Peeled & Deveined Tail On | Raw shrimp peeled and deveined with the tail left on |
| FPU | Fresh Peeled & Undeveined | Raw shrimp peeled but not deveined |
 
## Requirements
 
- JDK 17 or newer
- JavaFX 20 or newer.
## Running the program
 
1. Clone the repository and open it as a Java project in your IDE (Eclipse, IntelliJ IDEA or VS Code).
2. Download the JavaFX SDK for your operating system from https://openjfx.io and add its `lib` folder to the project's module path.
3. Run `main.Main` with these VM options, replacing the path with your own JavaFX `lib` folder:
```
--module-path /path/to/javafx-sdk/lib --add-modules javafx.controls
```
 
On first launch the program creates the eight default products and a default manager account. The default login is defined in the source code; change it before using the program for anything real.
 
## Project structure
 
```
src
├── main
│   └── Main                      application
├── model
│   ├── User                      abstract base class
│   ├── Clerk, Manager            subclasses of User
│   ├── Product
│   ├── Transaction
│   ├── TransactionType           SALE, CASH_IN, CASH_OUT
│   ├── TransactionStatus         COMPLETED, VOID_REQUESTED, VOIDED
│   └── StockMovement             one line of the stock history
├── service
│   ├── ShrimpSystem              wires the services together and tracks the signed-in user
│   ├── AuthenticationService     login and clerk account approval
│   ├── ProductService            add, edit, remove, search and save products
│   ├── InventoryService          restock, stock deduction, low-stock checks, stock history
│   ├── SalesService              sales, cash entries and the void workflow
│   ├── InsufficientStockException
|   └── ValidationException
```
 
## How it works
 
`User` is an abstract class. `Clerk` and `Manager` extend it and answer permission questions such as `canRestock()` and `canApproveVoids()`. The services call those methods before changing data, so a clerk who reaches a manager-only action gets an error message instead of a change.
 
All business rules sit in the `service` package. A service validates its input, then either completes the whole operation or throws an exception with a message written for the user. The screens catch that exception and show it in a dialog, so the same message appears whether the mistake is a blank field, a negative price or a sale larger than the stock.
 
Stock never goes below zero. Each change to a product's stock (opening stock, restock, sale, void) is written to the stock history with the user's name, the change in kg and the stock left afterwards.
 
A clerk cannot void a transaction alone. The clerk submits a reason, the transaction becomes `VOID_REQUESTED`, and a manager approves or rejects it. Approval marks the record `VOIDED`, returns the stock for a sale, and removes the amount from the cash balance.
 
Prices, quantities and cash amounts are `double` values rounded to two decimal places. The interface is built entirely in Java code: the project has no FXML files and no CSS files.
 
## Data storage
 
Data is saved to a `data/` folder in the directory the program is started from. The folder is created on first run. Each kind of record (products, stock history, users, transactions) has its own text file, with one record per line and fields separated by `|`. Every change is written to disk right away, so closing the window does not lose data.
 
To reset the program to its default state, close it and delete the `data/` folder.
 
## Specification checklist
 
| Requirement | Where it is met |
|---|---|
| Sales, inventory, product and cash transaction modules | `SalesService`, `InventoryService`, `ProductService`, and the cash entries in `TransactionPanel` |
| Two user types, Manager and Clerk | `Manager` and `Clerk`, both extending `User` |
| Persistence | Text files in `data/` |
| Error handling | `InsufficientStockException` and the validation messages shown by `MessageBox` |
