package com.avex.vol1.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.avex.vol1.model.CrossNumber;
import com.avex.vol1.model.Part;
import com.avex.vol1.model.PartResult;
import com.avex.vol1.repository.PartRepository;

@Service
public class SearchService {

    private final PartRepository partRepository;

    public SearchService(PartRepository partRepository) {
        this.partRepository = partRepository;
    }

    public List<PartResult> search(String rawInput) {
        if (rawInput == null || rawInput.trim().isEmpty()) return List.of();
        String target = normalize(rawInput);

        // ШАГ 1: Ищем ТОЛЬКО по колонке "номер запчасти" в обоих источниках
        List<Part> catalogDirect = partRepository.findByPartNumberNorm(target)
                .stream().filter(p -> "catalog".equals(p.getSource())).toList();
        List<Part> stockDirect   = partRepository.findByPartNumberNorm(target)
                .stream().filter(p -> "instock".equals(p.getSource())).toList();

        // ШАГ 2: Собираем все кросс-номера из найденных запчастей
        Set<String> crossNorms = new LinkedHashSet<>();
        for (Part p : catalogDirect) {
            for (CrossNumber c : p.getCrossNumbers()) crossNorms.add(c.getCrossNumberNorm());
        }
        for (Part p : stockDirect) {
            for (CrossNumber c : p.getCrossNumbers()) crossNorms.add(c.getCrossNumberNorm());
        }
        // Сам искомый номер из кросс-поиска исключаем
        crossNorms.remove(target);

        // ШАГ 3: Каждый кросс-номер ищем ТОЛЬКО по колонке "номер запчасти"
        // (не по колонке кросс-номеров — только точное совпадение с part_number)
        Map<String, List<Part>> crossCatalog = new LinkedHashMap<>();
        Map<String, List<Part>> crossStock   = new LinkedHashMap<>();

        if (!crossNorms.isEmpty()) {
            List<Part> found = partRepository.findByPartNumberNormIn(new ArrayList<>(crossNorms));
            for (Part p : found) {
                String pNorm = p.getPartNumberNorm();
                if ("catalog".equals(p.getSource())) {
                    crossCatalog.computeIfAbsent(pNorm, k -> new ArrayList<>()).add(p);
                } else {
                    crossStock.computeIfAbsent(pNorm, k -> new ArrayList<>()).add(p);
                }
            }
        }

        // ШАГ 4: Строим карточки результатов
        Map<String, PartResult> results = new LinkedHashMap<>();

        // --- Прямые совпадения из каталога ---
        for (Part cat : catalogDirect) {
            String key = cat.getPartNumberNorm();
            Part stk = findByNumber(stockDirect, cat.getPartNumberNorm());
            boolean inStock   = stk != null;
            boolean orderable = "order".equals(cat.getStockStatus());
            String price = inStock ? stk.getPrice() : cat.getPrice();
            String title = notEmpty(cat.getTitle(), stk != null ? stk.getTitle() : "");
            String brand = notEmpty(cat.getBrand().split(" ")[0], stk != null ? stk.getBrand() : "");
            results.put(key, new PartResult(cat.getPartNumber(), null, title, brand, price, inStock, orderable));
        }

        // --- Прямые совпадения только в наличии (нет в каталоге) ---
        for (Part stk : stockDirect) {
            String key = stk.getPartNumberNorm();
            if (!results.containsKey(key)) {
                results.put(key, new PartResult(
                        stk.getPartNumber(), null, stk.getTitle(), stk.getBrand().split(" ")[0],
                        stk.getPrice(), true, false));
            }
        }

        // --- Совпадения по кросс-номерам ---
        // crossOriginal нужен чтобы показать пользователю через какой кросс нашли
        Set<String> allCrossNorms = new LinkedHashSet<>();
        allCrossNorms.addAll(crossCatalog.keySet());
        allCrossNorms.addAll(crossStock.keySet());

        for (String crossNorm : allCrossNorms) {
            // Пропускаем если уже есть как прямое совпадение
            if (results.containsKey(crossNorm)) continue;
            String crossKey = "cross_" + crossNorm;
            if (results.containsKey(crossKey)) continue;

            // Ищем оригинальный (ненормализованный) кросс-номер для отображения
            String crossOriginal = findOriginalCross(catalogDirect, stockDirect, crossNorm);

            List<Part> catList = crossCatalog.getOrDefault(crossNorm, List.of());
            List<Part> stkList = crossStock.getOrDefault(crossNorm, List.of());

            // Берём первую запись из каждого источника (номер один и тот же)
            Part catRow = catList.isEmpty() ? null : catList.get(0);
            Part stkRow = stkList.isEmpty() ? null : stkList.get(0);

            boolean inStock   = stkRow != null;
            boolean orderable = catRow != null && "order".equals(catRow.getStockStatus());
            String price  = inStock ? stkRow.getPrice()  : (catRow != null ? catRow.getPrice()  : "");
            String title  = notEmpty(catRow != null ? catRow.getTitle() : "",
                                     stkRow != null ? stkRow.getTitle() : "");
            String brand  = notEmpty(catRow != null ? catRow.getBrand().split(" ")[0] : "",
                                     stkRow != null ? stkRow.getBrand().split(" ")[0] : "");
            String partNum = catRow != null ? catRow.getPartNumber() : stkRow.getPartNumber();

            results.put(crossKey, new PartResult(partNum, crossOriginal, title, brand, price, inStock, orderable));
        }

        return new ArrayList<>(results.values());
    }

    private Part findByNumber(List<Part> parts, String normNumber) {
        for (Part p : parts) {
            if (normNumber.equals(p.getPartNumberNorm())) return p;
        }
        return null;
    }

    // Находим как кросс-номер выглядит в оригинале (до нормализации)
    private String findOriginalCross(List<Part> catalogParts, List<Part> stockParts, String normCross) {
        for (Part p : catalogParts) {
            for (CrossNumber c : p.getCrossNumbers()) {
                if (c.getCrossNumberNorm().equals(normCross)) return c.getCrossNumber();
            }
        }
        for (Part p : stockParts) {
            for (CrossNumber c : p.getCrossNumbers()) {
                if (c.getCrossNumberNorm().equals(normCross)) return c.getCrossNumber();
            }
        }
        return normCross;
    }

    private String notEmpty(String primary, String fallback) {
        return (primary != null && !primary.isBlank()) ? primary : fallback;
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.replace("-", "").replace(".", "")
                .replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
}