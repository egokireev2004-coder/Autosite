package com.avex.vol1.model;

import jakarta.persistence.*;

@Entity
@Table(name = "cross_numbers")
public class CrossNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_id", nullable = false)
    private Part part;

    @Column(name = "cross_number")
    private String crossNumber;

    @Column(name = "cross_number_norm")
    private String crossNumberNorm;

    public CrossNumber() {}

    public CrossNumber(Part part, String crossNumber, String crossNumberNorm) {
        this.part = part;
        this.crossNumber = crossNumber;
        this.crossNumberNorm = crossNumberNorm;
    }

    public Long getId()                     { return id; }
    public Part getPart()                   { return part; }
    public String getCrossNumber()          { return crossNumber; }
    public void setCrossNumber(String v)    { this.crossNumber = v; }
    public String getCrossNumberNorm()      { return crossNumberNorm; }
    public void setCrossNumberNorm(String v){ this.crossNumberNorm = v; }
}