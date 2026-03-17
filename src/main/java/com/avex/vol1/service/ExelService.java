package com.avex.vol1.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStrings;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import com.avex.vol1.model.PartResult;

@Service
public class ExelService {

    static {
        IOUtils.setByteArrayMaxOverride(250_000_000);
    }

    // IV.xlsx — каталог
    // B=1  бренд
    // D=3  наименование
    // F=5  кросс-номера
    // G=6  номер запчасти
    // H=7  остаток ("Есть")
    // M=12 цена
    private static final String CATALOG_PATH = "catalog.xlsx";
    private static final int CAT_BRAND  = 1;
    private static final int CAT_TITLE  = 3;
    private static final int CAT_CROSS  = 5;
    private static final int CAT_NUMBER = 6;
    private static final int CAT_STOCK  = 7;
    private static final int CAT_PRICE  = 12;

    // Книга1.xlsx — наличие
    // B=1 наименование
    // C=2 бренд
    // D=3 спецификация (кросс-номера)
    // E=4 номер запчасти
    // F=5 остаток
    // G=6 цена
    private static final String STOCK_PATH  = "instock.xlsx";
    private static final int STK_TITLE  = 1;
    private static final int STK_BRAND  = 2;
    private static final int STK_CROSS  = 3;
    private static final int STK_NUMBER = 4;
    private static final int STK_STOCK  = 5;
    private static final int STK_PRICE  = 6;

    public List<PartResult> search(String rawInput) {
        if (rawInput == null || rawInput.trim().isEmpty()) return List.of();
        String target = normalize(rawInput);

        // Прямые совпадения
        List<RowData> catalogRows = findRows(CATALOG_PATH, target,
                new int[]{CAT_NUMBER, CAT_CROSS},
                CAT_TITLE, CAT_BRAND, CAT_CROSS, CAT_NUMBER, CAT_STOCK, CAT_PRICE);

        List<RowData> stockRows = findRows(STOCK_PATH, target,
                new int[]{STK_NUMBER, STK_CROSS},
                STK_TITLE, STK_BRAND, STK_CROSS, STK_NUMBER, STK_STOCK, STK_PRICE);

        // Все кросс-номера из найденных строк
        Set<String> crossTargets = new LinkedHashSet<>();
        for (RowData r : catalogRows) {
            for (String c : r.crossNumbers) crossTargets.add(normalize(c));
        }
        for (RowData r : stockRows) {
            for (String c : r.crossNumbers) crossTargets.add(normalize(c));
        }
        crossTargets.remove(target);

        // Ищем каждый кросс-номер отдельно в каталоге и в наличии
        // Используем ОТДЕЛЬНЫЕ мапы чтобы не перезаписывать друг друга
        Map<String, List<RowData>> crossCatalogRows = new LinkedHashMap<>();
        Map<String, List<RowData>> crossStockRows   = new LinkedHashMap<>();

        for (String cross : crossTargets) {
            List<RowData> foundCat = findRows(CATALOG_PATH, cross,
                    new int[]{CAT_NUMBER},
                    CAT_TITLE, CAT_BRAND, CAT_CROSS, CAT_NUMBER, CAT_STOCK, CAT_PRICE);
            if (!foundCat.isEmpty()) crossCatalogRows.put(cross, foundCat);

            List<RowData> foundStock = findRows(STOCK_PATH, cross,
                    new int[]{STK_NUMBER},
                    STK_TITLE, STK_BRAND, STK_CROSS, STK_NUMBER, STK_STOCK, STK_PRICE);
            if (!foundStock.isEmpty()) crossStockRows.put(cross, foundStock);
        }

        // Собираем карточки
        Map<String, PartResult> results = new LinkedHashMap<>();

        // 1. Прямые совпадения из каталога
        for (RowData cat : catalogRows) {
            String key = normalize(cat.partNumber);
            boolean orderable = "есть".equalsIgnoreCase(cat.stockStatus.trim());
            RowData stk = findMatch(stockRows, cat.partNumber);
            boolean inStock = stk != null;
            String price = inStock ? stk.price : cat.price;
            String title = cat.title.isEmpty() ? (stk != null ? stk.title : "") : cat.title;
            String brand = cat.brand.isEmpty() ? (stk != null ? stk.brand : "") : cat.brand.split(" ")[0];
            results.put(key, new PartResult(cat.partNumber, null, title, brand, price, inStock, orderable));
        }

        // 2. Прямые совпадения только в наличии (нет в каталоге)
        for (RowData stk : stockRows) {
            String key = normalize(stk.partNumber);
            if (!results.containsKey(key)) {
                results.put(key, new PartResult(stk.partNumber, null, stk.title, stk.brand.split(" ")[0], stk.price, true, false));
            }
        }

        // 3. Совпадения по кросс-номерам
        // Объединяем ключи из обеих крос-мап
        Set<String> allCrossKeys = new LinkedHashSet<>();
        allCrossKeys.addAll(crossCatalogRows.keySet());
        allCrossKeys.addAll(crossStockRows.keySet());

        for (String crossNorm : allCrossKeys) {
            String crossOriginal = findOriginalCross(catalogRows, stockRows, crossNorm);

            List<RowData> catList  = crossCatalogRows.getOrDefault(crossNorm, List.of());
            List<RowData> stkList  = crossStockRows.getOrDefault(crossNorm, List.of());

            // Все номера запчастей найденных по этому кроссу
            Set<String> partNumbers = new LinkedHashSet<>();
            for (RowData r : catList)  partNumbers.add(normalize(r.partNumber));
            for (RowData r : stkList)  partNumbers.add(normalize(r.partNumber));

            for (String normPart : partNumbers) {
                // Пропускаем если уже есть как прямое совпадение
                if (results.containsKey(normPart)) continue;

                String crossKey = "cross_" + normPart;
                if (results.containsKey(crossKey)) continue;

                // Ищем данные в catList и stkList
                RowData catRow = catList.stream()
                        .filter(r -> normalize(r.partNumber).equals(normPart))
                        .findFirst().orElse(null);
                RowData stkRow = stkList.stream()
                        .filter(r -> normalize(r.partNumber).equals(normPart))
                        .findFirst().orElse(null);

                boolean inStock   = stkRow != null;
                boolean orderable = catRow != null && "есть".equalsIgnoreCase(catRow.stockStatus.trim());
                String price  = inStock ? stkRow.price  : (catRow != null ? catRow.price  : "");
                String title  = (catRow != null && !catRow.title.isEmpty()) ? catRow.title
                              : (stkRow != null ? stkRow.title : "");
                String brand  = (catRow != null && !catRow.brand.isEmpty()) ? catRow.brand.split(" ")[0]
                              : (stkRow != null ? stkRow.brand.split(" ")[0] : "");
                String partNum = catRow != null ? catRow.partNumber : stkRow.partNumber;

                results.put(crossKey, new PartResult(partNum, crossOriginal, title, brand, price, inStock, orderable));
            }
        }

        return new ArrayList<>(results.values());
    }

    private List<RowData> findRows(String path, String target,
                                   int[] searchCols,
                                   int titleCol, int brandCol, int crossCol,
                                   int numberCol, int stockCol, int priceCol) {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            OPCPackage pkg = OPCPackage.open(is);
            XSSFReader xr = new XSSFReader(pkg);
            SharedStrings sst = xr.getSharedStringsTable();

            SAXParserFactory spf = SAXParserFactory.newInstance();
            SAXParser sp = spf.newSAXParser();
            XMLReader xmlReader = sp.getXMLReader();

            RowCollectorHandler handler = new RowCollectorHandler(
                    sst, target, searchCols, titleCol, brandCol, crossCol, numberCol, stockCol, priceCol);
            xmlReader.setContentHandler(handler);
            xmlReader.parse(new InputSource(xr.getSheetsData().next()));

            return handler.getResults();
        } catch (Exception e) {
            System.err.println("Ошибка чтения " + path + ": " + e.getMessage());
            return List.of();
        }
    }

    private RowData findMatch(List<RowData> rows, String partNumber) {
        String norm = normalize(partNumber);
        for (RowData r : rows) {
            if (normalize(r.partNumber).equals(norm)) return r;
            for (String c : r.crossNumbers) {
                if (normalize(c).equals(norm)) return r;
            }
        }
        return null;
    }

    private String findOriginalCross(List<RowData> catalogRows, List<RowData> stockRows, String normCross) {
        for (RowData r : catalogRows) {
            for (String c : r.crossNumbers) {
                if (normalize(c).equals(normCross)) return c;
            }
        }
        for (RowData r : stockRows) {
            for (String c : r.crossNumbers) {
                if (normalize(c).equals(normCross)) return c;
            }
        }
        return normCross;
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.replace("-", "").replace(".", "")
                .replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    static class RowData {
        String title      = "";
        String brand      = "";
        List<String> crossNumbers = new ArrayList<>();
        String partNumber = "";
        String stockStatus = "";
        String price      = "";
    }

    class RowCollectorHandler extends DefaultHandler {
        private final SharedStrings sst;
        private final String target;
        private final int[] searchCols;
        private final int titleCol, brandCol, crossCol, numberCol, stockCol, priceCol;

        private final List<RowData> results = new ArrayList<>();

        private RowData currentRow;
        private int currentColIndex = -1;
        private boolean isSharedString = false;
        private boolean isRelevantCell = false;
        private final StringBuilder cellValue = new StringBuilder();

        private final Set<Integer> collectCols;

        RowCollectorHandler(SharedStrings sst, String target, int[] searchCols,
                            int titleCol, int brandCol, int crossCol, int numberCol,
                            int stockCol, int priceCol) {
            this.sst = sst;
            this.target = target;
            this.searchCols = searchCols;
            this.titleCol  = titleCol;
            this.brandCol  = brandCol;
            this.crossCol  = crossCol;
            this.numberCol = numberCol;
            this.stockCol  = stockCol;
            this.priceCol  = priceCol;
            this.collectCols = new HashSet<>(Arrays.asList(
                    titleCol, brandCol, crossCol, numberCol, stockCol, priceCol));
        }

        public List<RowData> getResults() { return results; }

        @Override
        public void startElement(String uri, String local, String qName, Attributes attrs) {
            if ("row".equals(qName)) {
                currentRow = new RowData();
            } else if ("c".equals(qName) && currentRow != null) {
                String ref = attrs.getValue("r");
                currentColIndex = colIndex(ref);
                isSharedString = "s".equals(attrs.getValue("t"));
                isRelevantCell = collectCols.contains(currentColIndex);
                cellValue.setLength(0);
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (isRelevantCell) cellValue.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String local, String qName) {
            if ("v".equals(qName) && isRelevantCell && currentRow != null) {
                String raw = cellValue.toString().trim();
                String val = isSharedString
                        ? sst.getItemAt(Integer.parseInt(raw)).getString()
                        : raw;

                if (currentColIndex == titleCol)  currentRow.title = val;
                if (currentColIndex == brandCol)   currentRow.brand = val;
                if (currentColIndex == numberCol)  currentRow.partNumber = val;
                if (currentColIndex == stockCol)   currentRow.stockStatus = val;
                if (currentColIndex == priceCol)   currentRow.price = val;
                if (currentColIndex == crossCol) {
                    for (String part : val.split("[,，;]")) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty()) currentRow.crossNumbers.add(trimmed);
                    }
                }
                isRelevantCell = false;
            }

            if ("row".equals(qName) && currentRow != null) {
                if (rowMatches()) results.add(currentRow);
                currentRow = null;
            }
        }

        private boolean rowMatches() {
            for (int col : searchCols) {
                String val = getColValue(col);
                for (String part : val.split("[,，;]")) {
                    if (normalize(part.trim()).equals(target)) return true;
                }
            }
            return false;
        }

        private String getColValue(int col) {
            if (col == titleCol)  return currentRow.title;
            if (col == brandCol)  return currentRow.brand;
            if (col == numberCol) return currentRow.partNumber;
            if (col == stockCol)  return currentRow.stockStatus;
            if (col == priceCol)  return currentRow.price;
            if (col == crossCol)  return String.join(",", currentRow.crossNumbers);
            return "";
        }

        private int colIndex(String ref) {
            if (ref == null) return -1;
            int idx = 0;
            for (char c : ref.toCharArray()) {
                if (!Character.isLetter(c)) break;
                idx = idx * 26 + (Character.toUpperCase(c) - 'A' + 1);
            }
            return idx - 1;
        }
    }
}