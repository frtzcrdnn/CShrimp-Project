package service;

import model.Product;
import model.Transaction;
import model.TransactionStatus;
import model.TransactionType;
import model.User;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Coordinates sales, cash-in/cash-out adjustments, void workflows,
 * financial summaries, and file persistence in {@code data/transactions.txt}.
 */
public class TransactionService {

    private static final String FILE_NAME = "data/transactions.txt";
    private static final String SEPARATOR = "|";
    private static final int MAX_NOTE_LENGTH = 120;
    private static final double MAX_CASH_AMOUNT = 10_000_000;

    private final ProductService productService;
    private final InventoryService inventoryService;
    private final List<Transaction> transactions = new ArrayList<>();
    private final String filePath;
    private int transactionCounter = 1;

    public TransactionService(ProductService productService, InventoryService inventoryService) {
        this(productService, inventoryService, FILE_NAME);
    }

    public TransactionService(ProductService productService, InventoryService inventoryService,
                              String filePath) {
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.filePath = filePath;
        loadTransactions();
    }

    private synchronized String generateTransactionId() {
        return String.format("TXN-%04d", transactionCounter++);
    }

    /** Processes a sale with default Walk-in customer and exact Cash payment. */
    public Transaction processSale(User user, String productId, double quantityKg) {
        if (Double.isNaN(quantityKg) || Double.isInfinite(quantityKg) || quantityKg <= 0) {
            throw new ValidationException("Quantity must be a valid positive number.");
        }
        Product product = productService.findById(productId);
        if (product == null) {
            throw new ValidationException("Product not found: " + productId);
        }
        double soldQuantity = round2(quantityKg);
        double totalAmount = round2(soldQuantity * product.getPricePerKg());
        return processSale(user, productId, soldQuantity, "Walk-in", "Cash", totalAmount);
    }

    /** Processes a sale with customer name, payment method, and tendered amount validation. */
    public Transaction processSale(User user, String productId, double quantityKg,
                                   String customerName, String paymentMethod, double amountTendered) {
        if (user == null) {
            throw new ValidationException("No user is signed in.");
        }
        if (Double.isNaN(quantityKg) || Double.isInfinite(quantityKg) || quantityKg <= 0) {
            throw new ValidationException("Quantity must be a valid positive number.");
        }
        Product product = productService.findById(productId);
        if (product == null) {
            throw new ValidationException("Product not found: " + productId);
        }

        double soldQuantity = round2(quantityKg);
        inventoryService.ensureAvailable(productId, soldQuantity);

        double pricePerKg = product.getPricePerKg();
        double totalAmount = round2(soldQuantity * pricePerKg);

        String cleanCustomer = cleanText(customerName == null ? "" : customerName.trim());
        if (cleanCustomer.length() > MAX_NOTE_LENGTH) {
            throw new ValidationException("Customer name must be " + MAX_NOTE_LENGTH + " characters or fewer.");
        }
        if (cleanCustomer.isEmpty()) {
            cleanCustomer = "Walk-in";
        }
        String cleanMethod = (paymentMethod == null || paymentMethod.trim().isEmpty())
                ? "Cash" : cleanText(paymentMethod.trim());

        if (Double.isNaN(amountTendered) || Double.isInfinite(amountTendered)) {
            throw new ValidationException("Amount tendered must be a valid number.");
        }
        double tendered = round2(amountTendered);
        if (tendered < totalAmount) {
            throw new ValidationException(String.format(
                    "Insufficient payment. Total is Php %,.2f, tendered Php %,.2f.",
                    totalAmount, tendered));
        }
        double change = round2(tendered - totalAmount);

        inventoryService.deductStock(user, productId, soldQuantity, "Sale to " + cleanCustomer);

        Transaction transaction = new Transaction(
                generateTransactionId(),
                LocalDateTime.now().withNano(0),
                TransactionType.SALE,
                product.getProductId(),
                product.getName(),
                soldQuantity,
                pricePerKg,
                totalAmount,
                cleanCustomer,
                cleanMethod,
                tendered,
                change,
                user.getUsername());

        transactions.add(transaction);
        try {
            saveTransactions();
        } catch (RuntimeException e) {
            transactions.remove(transaction);
            try {
                inventoryService.returnStock(user, productId, soldQuantity,
                        "Rollback failed sale save: " + transaction.getTransactionId());
            } catch (RuntimeException ignored) {
            }
            throw e;
        }
        return transaction;
    }

    /** Records a cash-in or cash-out adjustment with default counterparty. */
    public Transaction processCashTransaction(User user, TransactionType type, double amount, String note) {
        return processCashTransaction(user, type, amount, "-", note);
    }

    /** Records a cash-in or cash-out adjustment with balance validation for cash-out. */
    public Transaction processCashTransaction(User user, TransactionType type, double amount,
                                              String counterparty, String note) {
        if (user == null) {
            throw new ValidationException("No user is signed in.");
        }
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0) {
            throw new ValidationException("Amount must be a valid positive number.");
        }
        if (type == null || (type != TransactionType.CASH_IN && type != TransactionType.CASH_OUT)) {
            throw new ValidationException("Transaction must be CASH IN or CASH OUT.");
        }
        if (amount > MAX_CASH_AMOUNT) {
            throw new ValidationException("Amount cannot exceed Php 10,000,000.");
        }

        double rounded = round2(amount);
        if (rounded < 0.01) {
            throw new ValidationException("Amount must be at least Php 0.01.");
        }
        if (type == TransactionType.CASH_OUT) {
            double availableCash = calculateTotalCashBalance();
            if (rounded > availableCash) {
                throw new ValidationException(String.format(
                        "Insufficient cash balance for cash-out. Available: Php %,.2f, requested: Php %,.2f.",
                        availableCash, rounded));
            }
        }

        String cleanParty = cleanText(counterparty == null ? "" : counterparty.trim());
        if (cleanParty.isEmpty()) {
            cleanParty = "-";
        }
        if (cleanParty.length() > MAX_NOTE_LENGTH) {
            throw new ValidationException("Customer / Source must be " + MAX_NOTE_LENGTH + " characters or fewer.");
        }

        String cleanNote = cleanText(note == null ? "" : note.trim());
        if (cleanNote.length() > MAX_NOTE_LENGTH) {
            throw new ValidationException("Note must be " + MAX_NOTE_LENGTH + " characters or fewer.");
        }
        if (cleanNote.isEmpty()) {
            cleanNote = type.getLabel();
        }

        Transaction transaction = new Transaction(
                generateTransactionId(),
                LocalDateTime.now().withNano(0),
                type,
                null,
                cleanNote,
                0.0,
                0.0,
                rounded,
                cleanParty,
                "Cash",
                rounded,
                0.0,
                user.getUsername());

        transactions.add(transaction);
        try {
            saveTransactions();
        } catch (RuntimeException e) {
            transactions.remove(transaction);
            throw e;
        }
        return transaction;
    }

    /** Allows a clerk to request a void on one of their own completed transactions. */
    public void requestVoid(User clerk, String transactionId, String reason) {
        if (clerk == null) {
            throw new ValidationException("No user is signed in.");
        }
        if (!clerk.canRequestVoid()) {
            throw new ValidationException("Only a clerk can request a transaction void.");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new ValidationException("Void reason is required.");
        }
        String cleanReason = cleanText(reason.trim());
        if (cleanReason.length() > MAX_NOTE_LENGTH) {
            throw new ValidationException("Void reason must be " + MAX_NOTE_LENGTH + " characters or fewer.");
        }

        Transaction transaction = findTransaction(transactionId);
        if (!transaction.getPerformedBy().equalsIgnoreCase(clerk.getUsername())) {
            throw new ValidationException("You can only request a void on your own transactions.");
        }
        if (transaction.getStatus() != TransactionStatus.COMPLETED) {
            throw new ValidationException("Only completed transactions can be requested for void.");
        }

        transaction.requestVoid(clerk.getUsername(), cleanReason);
        try {
            saveTransactions();
        } catch (RuntimeException e) {
            transaction.rejectVoid();
            throw e;
        }
    }

    /** Approves a clerk's pending void request and returns stock if it was a sale. */
    public void approveVoid(User manager, String transactionId) {
        if (manager == null || !manager.canApproveVoids()) {
            throw new ValidationException("Only a manager can approve void requests.");
        }

        Transaction transaction = findTransaction(transactionId);
        if (transaction.getStatus() != TransactionStatus.VOID_REQUESTED) {
            throw new ValidationException("Transaction has no pending void request.");
        }

        String prevReqBy = transaction.getVoidRequestedBy();
        String prevReason = transaction.getVoidReason();

        if (transaction.getType() == TransactionType.SALE) {
            inventoryService.returnStock(manager, transaction.getProductId(),
                    transaction.getQuantityKg(), "Approved void: " + transaction.getTransactionId());
        }

        transaction.approveVoid(manager.getUsername());
        try {
            saveTransactions();
        } catch (RuntimeException e) {
            transaction.restoreVoidState(TransactionStatus.VOID_REQUESTED, prevReqBy, prevReason, null);
            if (transaction.getType() == TransactionType.SALE) {
                try {
                    inventoryService.deductStock(manager, transaction.getProductId(),
                            transaction.getQuantityKg(), "Rollback failed void: " + transaction.getTransactionId());
                } catch (RuntimeException ignored) {
                }
            }
            throw e;
        }
    }

    /** Allows a manager to directly void any completed transaction. */
    public void voidTransaction(User manager, String transactionId, String reason) {
        if (manager == null || !manager.canApproveVoids()) {
            throw new ValidationException("Only a manager can void transactions.");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new ValidationException("Void reason is required.");
        }
        String cleanReason = cleanText(reason.trim());
        if (cleanReason.length() > MAX_NOTE_LENGTH) {
            throw new ValidationException("Void reason must be " + MAX_NOTE_LENGTH + " characters or fewer.");
        }

        Transaction transaction = findTransaction(transactionId);
        if (transaction.getStatus() != TransactionStatus.COMPLETED) {
            throw new ValidationException("Only completed transactions can be directly voided.");
        }

        if (transaction.getType() == TransactionType.SALE) {
            inventoryService.returnStock(manager, transaction.getProductId(),
                    transaction.getQuantityKg(), "Voided by manager: " + transaction.getTransactionId());
        }

        transaction.directVoid(manager.getUsername(), cleanReason);
        try {
            saveTransactions();
        } catch (RuntimeException e) {
            transaction.rejectVoid();
            if (transaction.getType() == TransactionType.SALE) {
                try {
                    inventoryService.deductStock(manager, transaction.getProductId(),
                            transaction.getQuantityKg(), "Rollback failed void: " + transaction.getTransactionId());
                } catch (RuntimeException ignored) {
                }
            }
            throw e;
        }
    }

    /** Rejects a pending void request and returns the transaction to COMPLETED. */
    public void rejectVoid(User manager, String transactionId) {
        if (manager == null || !manager.canApproveVoids()) {
            throw new ValidationException("Only a manager can reject void requests.");
        }

        Transaction transaction = findTransaction(transactionId);
        if (transaction.getStatus() != TransactionStatus.VOID_REQUESTED) {
            throw new ValidationException("Transaction has no pending void request.");
        }

        String prevReqBy = transaction.getVoidRequestedBy();
        String prevReason = transaction.getVoidReason();
        transaction.rejectVoid();
        try {
            saveTransactions();
        } catch (RuntimeException e) {
            transaction.restoreVoidState(TransactionStatus.VOID_REQUESTED, prevReqBy, prevReason, null);
            throw e;
        }
    }

    private Transaction findTransaction(String transactionId) {
        if (transactionId == null || transactionId.trim().isEmpty()) {
            throw new ValidationException("Transaction ID is required.");
        }
        for (Transaction t : transactions) {
            if (t.getTransactionId().equalsIgnoreCase(transactionId.trim())) {
                return t;
            }
        }
        throw new ValidationException("Transaction not found: " + transactionId);
    }

    public List<Transaction> getAllTransactions() {
        return new ArrayList<>(transactions);
    }

    public List<Transaction> getTransactionsByUser(String username) {
        return transactions.stream()
                .filter(t -> t.getPerformedBy().equalsIgnoreCase(username))
                .collect(Collectors.toList());
    }

    public List<Transaction> getPendingVoidRequests() {
        return transactions.stream()
                .filter(t -> t.getStatus() == TransactionStatus.VOID_REQUESTED)
                .collect(Collectors.toList());
    }

    public boolean hasTransactionsForProduct(String productId) {
        for (Transaction t : transactions) {
            if (t.getProductId() != null && t.getProductId().equalsIgnoreCase(productId)) {
                return true;
            }
        }
        return false;
    }

    public Transaction getTransaction(String transactionId) {
        if (transactionId == null) {
            return null;
        }
        for (Transaction t : transactions) {
            if (t.getTransactionId().equalsIgnoreCase(transactionId.trim())) {
                return t;
            }
        }
        return null;
    }

    /** Net cash balance across all non-voided transactions (pending voids still count until approved). */
    public double calculateTotalCashBalance() {
        double balance = 0.0;
        for (Transaction t : transactions) {
            if (isNonVoided(t)) {
                balance += t.getType().getSign() * t.getTotalAmount();
            }
        }
        return round2(balance);
    }

    public double calculateTotalSales() { return sumNonVoided(TransactionType.SALE); }
    public double calculateTotalCashIn() { return sumNonVoided(TransactionType.CASH_IN); }
    public double calculateTotalCashOut() { return sumNonVoided(TransactionType.CASH_OUT); }

    public double calculateBalanceByUser(String username) {
        double balance = 0.0;
        for (Transaction t : getTransactionsByUser(username)) {
            if (isNonVoided(t)) {
                balance += t.getType().getSign() * t.getTotalAmount();
            }
        }
        return round2(balance);
    }

    public double calculateTodaySales() {
        LocalDate today = LocalDate.now();
        double total = 0.0;
        for (Transaction t : transactions) {
            if (t.getType() == TransactionType.SALE && isNonVoided(t)
                    && t.getDateTime().toLocalDate().equals(today)) {
                total += t.getTotalAmount();
            }
        }
        return round2(total);
    }

    public double calculateSalesByUser(String username) {
        double total = 0.0;
        for (Transaction t : getTransactionsByUser(username)) {
            if (t.getType() == TransactionType.SALE && isNonVoided(t)) {
                total += t.getTotalAmount();
            }
        }
        return round2(total);
    }

    public double calculateTodaySalesByUser(String username) {
        LocalDate today = LocalDate.now();
        double total = 0.0;
        for (Transaction t : getTransactionsByUser(username)) {
            if (t.getType() == TransactionType.SALE && isNonVoided(t)
                    && t.getDateTime().toLocalDate().equals(today)) {
                total += t.getTotalAmount();
            }
        }
        return round2(total);
    }

    private double sumNonVoided(TransactionType type) {
        double total = 0.0;
        for (Transaction t : transactions) {
            if (t.getType() == type && isNonVoided(t)) {
                total += t.getTotalAmount();
            }
        }
        return round2(total);
    }

    private static boolean isNonVoided(Transaction t) {
        return t.getStatus() != TransactionStatus.VOIDED;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private synchronized void saveTransactions() {
        File file = new File(filePath);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        File temp = new File(filePath + ".tmp");
        try {
            try (PrintWriter writer = new PrintWriter(new FileWriter(temp))) {
                for (Transaction t : transactions) {
                    writer.println(t.getTransactionId() + SEPARATOR
                            + t.getDateTime() + SEPARATOR
                            + t.getType().name() + SEPARATOR
                            + t.getStatus().name() + SEPARATOR
                            + (t.getProductId() == null ? "" : t.getProductId()) + SEPARATOR
                            + cleanText(t.getProductName() == null ? "" : t.getProductName()) + SEPARATOR
                            + t.getQuantityKg() + SEPARATOR
                            + t.getPricePerKg() + SEPARATOR
                            + t.getTotalAmount() + SEPARATOR
                            + cleanText(t.getPerformedBy()) + SEPARATOR
                            + cleanText(t.getVoidRequestedBy() == null ? "" : t.getVoidRequestedBy()) + SEPARATOR
                            + cleanText(t.getVoidReason() == null ? "" : t.getVoidReason()) + SEPARATOR
                            + cleanText(t.getVoidApprovedBy() == null ? "" : t.getVoidApprovedBy()) + SEPARATOR
                            + cleanText(t.getPaymentMethod()) + SEPARATOR
                            + t.getAmountTendered() + SEPARATOR
                            + t.getChangeAmount() + SEPARATOR
                            + cleanText(t.getCustomerName()));
                }
                if (writer.checkError()) {
                    throw new IOException("Write failed while saving transactions.");
                }
            }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            temp.delete();
            throw new RuntimeException("Could not save transactions: " + e.getMessage());
        }
    }

    private void loadTransactions() {
        transactions.clear();
        File file = new File(filePath);
        if (!file.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] p = line.split("\\|", -1);
                if (p.length < 13) {
                    continue;
                }
                try {
                    double totalAmount = Double.parseDouble(p[8]);
                    String method = (p.length >= 16 && !p[13].isEmpty()) ? p[13] : "Cash";
                    double tendered = (p.length >= 16 && !p[14].isEmpty()) ? Double.parseDouble(p[14]) : totalAmount;
                    double change = (p.length >= 16 && !p[15].isEmpty()) ? Double.parseDouble(p[15]) : 0.0;
                    String customer = (p.length >= 17 && !p[16].isEmpty()) ? p[16] : "Walk-in";

                    Transaction t = new Transaction(
                            p[0], LocalDateTime.parse(p[1]), TransactionType.valueOf(p[2]),
                            p[4].isEmpty() ? null : p[4], p[5],
                            Double.parseDouble(p[6]), Double.parseDouble(p[7]), totalAmount,
                            customer, method, tendered, change, p[9]);

                    t.restoreVoidState(TransactionStatus.valueOf(p[3]),
                            p[10].isEmpty() ? null : p[10],
                            p[11].isEmpty() ? null : p[11],
                            p[12].isEmpty() ? null : p[12]);

                    transactions.add(t);
                    int number = Integer.parseInt(p[0].substring(p[0].indexOf('-') + 1));
                    if (number >= transactionCounter) {
                        transactionCounter = number + 1;
                    }
                } catch (RuntimeException e) {
                    // skip broken lines
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not load transactions: " + e.getMessage());
        }
    }

    private static String cleanText(String text) {
        return text.replace("|", "/").replace("\n", " ").replace("\r", " ");
    }
}