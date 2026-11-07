package model;

public class Product {
	// attributes
	private final String productId;
	private String name;
	private double pricePerKg;
	private String description;
	private double stockQuantity;
	
	// constructor
	public Product(String productId, String name, double pricePerKg,
				   String description, double stockQuantity) {
		this.productId = productId;
		this.name = name;
		this.pricePerKg = pricePerKg;
		this.description = description;
		this.stockQuantity = stockQuantity;
	}
	
	// getters & setters
	public String getProductId() { return productId; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public double getPricePerKg() { return pricePerKg; }
	public void setPricePerKg(double pricePerKg) { this.pricePerKg = pricePerKg; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(double stockQuantity) { this.stockQuantity = stockQuantity; }

	
	
}
