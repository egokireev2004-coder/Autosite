package com.avex.vol1.model;

public class PartResult {
    private String partNumber;   // номер запчасти из файла
    private String crossNumber;  // кросс-номер через который нашли (null если прямое совпадение)
    private String title;        // наименование
    private String brand;        // бренд
    private String price;        // цена
    private boolean inStock;     // найден в наличии (instock.xlsx)
    private boolean orderable;   // найден в каталоге (catalog.xlsx) с остатком "Есть"

    public PartResult(String partNumber, String crossNumber, String title, String brand,
                      String price, boolean inStock, boolean orderable) {
        this.partNumber  = partNumber;
        this.crossNumber = crossNumber;
        this.title       = title;
        this.brand       = brand;
        this.price       = price;
        this.inStock     = inStock;
        this.orderable   = orderable;
    }

    public String getPartNumber()  { return partNumber; }
    public String getCrossNumber() { return crossNumber; }
    public String getTitle()       { return title; }
    public String getBrand()       { return brand; }
    public String getPrice()       { return price; }
    public boolean isInStock()     { return inStock; }
    public boolean isOrderable()   { return orderable; }
}