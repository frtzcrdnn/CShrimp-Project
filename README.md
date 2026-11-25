# CShrimp

A JavaFX desktop application that manages sales, inventory, products, employee accounts, and register cash for a small shrimp processing and export business. It replaces paper logs and spreadsheets with one program that routes managers and clerks to separate dashboards.

Built for an object-oriented programming course using only the JDK and JavaFX, with no third-party libraries.

## Features

**Manager**
- Adds, edits, archives, and deletes products (code, name, description, price per kg, and optional product photo). Products with past sales or stock movements cannot be deleted; archiving hides them from new sales while keeping historical records intact.
- Enters opening stock, restocks products, and reviews the stock movement log.
- Approves or rejects void requests from clerks, or voids any completed transaction directly.
- Creates clerk accounts, approves or deactivates self-registered clerks, and resets clerk passwords.
- Monitors each approved clerk's sales count, sales total, and pending void requests on the Overview tab, and opens any clerk's dashboard in a separate read-only window.
- Views total sales, completed sales count, net cash balance, low-stock items, out-of-stock count, pending voids, and recent transactions on the Overview tab.

**Clerk**
- Registers a new account from the login screen (remains pending until a manager approves it).
- Records a sale by selecting a product, entering weight in kg, customer name (defaults to `Walk-in`), payment method (`Cash`, `GCash`, or `Bank Transfer`), and amount tendered. The program rounds values to two decimal places, computes change, deducts stock, and saves the receipt.
- Sees a warning and cannot complete a sale when the requested weight exceeds available stock.
- Checks stock levels and reports products at or below 25 kg as low on stock, flagging them on the manager's dashboard.
- Records `Cash In` and `Cash Out` entries in the wallet; `Cash Out` is blocked if it exceeds the current register cash balance.
- Requests a void on one of their own completed transactions by submitting a reason.

**All Users**
- Signed-in users can change their own password from the dashboard.
- Every transaction is saved with its ID, date and time, type (`Sale`, `Cash In`, `Cash Out`), customer or counterparty, product or note, weight in kg, unit price, total amount, payment method, amount tendered, change, performing user, and status (`Completed`, `Void Requested`, `Voided`). Voiding a sale restores both the deducted stock and the register cash balance.

## Products

On first launch, the system seeds eight shrimp products into `data/products.txt`. A manager can edit these entries, adjust prices, or add new products.

| Code | Name | Default Price (Php/kg) | Description |
|---|---|---|---|
| CHLSO | Cooked Head-Less Shell-On | 730.00 | Cooked shrimp with head removed and shell retained |
| CHOSO | Cooked Head-On Shell-On | 700.00 | Cooked shrimp with head and shell retained |
| FHOSO | Fresh Head-On Shell-On | 570.00 | Raw whole shrimp with head and shell intact |
| FHLSO | Fresh Head-Less Shell-On | 590.00 | Raw shrimp with head removed but shell kept on |
| NOBASHI | Nobashi | 610.00 | Peeled and deveined, tail-on, stretched shrimp |
| FPD | Fresh Peeled & Deveined | 520.00 | Raw shrimp fully peeled and deveined |
| FPDTO | Fresh Peeled & Deveined Tail On | 500.00 | Raw shrimp peeled and deveined with the tail left on |
| FPU | Fresh Peeled & Undeveined | 490.00 | Raw shrimp peeled but not deveined |

## Requirements

- JDK 17 or newer
- JavaFX 20 or newer (`javafx.controls`)

## Running the program

1. Clone the repository and open it as a Java project in your IDE (Eclipse, IntelliJ IDEA, or VS Code).
2. Download the JavaFX SDK for your operating system from https://openjfx.io and add its `lib` folder to the project's module path.
3. Run `main.Main` with these VM options, replacing the path with your own JavaFX `lib` folder:
```text
--module-path /path/to/javafx-sdk/lib --add-modules javafx.controls