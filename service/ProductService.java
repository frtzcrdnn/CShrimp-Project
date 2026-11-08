package service;

import model.Product;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles all the product operations (add, edit, delete, search)
 * and saves them to a text file. It does not need InventoryService,
 * so it still works even if the inventory part is empty.
 *
 * Each product is saved as one line, separated by "|":
 * id|name|pricePerKg|description|stockQuantity
 */
public class ProductService {

}
