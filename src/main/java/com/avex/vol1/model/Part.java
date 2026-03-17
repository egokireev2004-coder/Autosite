package com.avex.vol1.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "parts")
public class Part {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "part_number")
    private String partNumber;

    @Column(name = "part_number_norm")
    private String partNumberNorm;

    private String brand;
    private String title;
    private String price;

    @Column(name = "stock_status")
    private String stockStatus;   // "instock" / "order" / "none"

    private String source;        // "catalog" / "instock"

    @OneToMany(mappedBy = "part", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CrossNumber> crossNumbers = new ArrayList<>();

    public Part() {}

    public Long getId()                        { return id; }
    public String getPartNumber()              { return partNumber; }
    public void setPartNumber(String v)        { this.partNumber = v; }
    public String getPartNumberNorm()          { return partNumberNorm; }
    public void setPartNumberNorm(String v)    { this.partNumberNorm = v; }
    public String getBrand()                   { return brand; }
    public void setBrand(String v)             { this.brand = v; }
    public String getTitle()                   { return title; }
    public void setTitle(String v)             { this.title = v; }
    public String getPrice()                   { return price; }
    public void setPrice(String v)             { this.price = v; }
    public String getStockStatus()             { return stockStatus; }
    public void setStockStatus(String v)       { this.stockStatus = v; }
    public String getSource()                  { return source; }
    public void setSource(String v)            { this.source = v; }
    public List<CrossNumber> getCrossNumbers() { return crossNumbers; }
}