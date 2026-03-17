package com.avex.vol1.repository;

import com.avex.vol1.model.Part;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PartRepository extends JpaRepository<Part, Long> {

    // Поиск по одному нормализованному номеру
    List<Part> findByPartNumberNorm(String partNumberNorm);

    // Поиск по списку нормализованных номеров (для кросс-поиска)
    List<Part> findByPartNumberNormIn(List<String> partNumberNorms);

    // Удалить все записи из источника перед переимпортом
    void deleteBySource(String source);
}